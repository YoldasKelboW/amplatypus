// SPDX-License-Identifier: GPL-3.0-or-later
using System;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using Windows.Media;
using Windows.Media.Audio;
using Windows.Media.MediaProperties;
using Windows.Media.Render;

namespace Amplatypus.Xbox
{
    // Gives access to the bytes of an AudioFrame's buffer.
    [ComImport]
    [Guid("5B0D3235-4DBA-4D44-865E-8F1D0E4FD04D")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal unsafe interface IMemoryBufferByteAccess
    {
        void GetBuffer(out byte* buffer, out uint capacity);
    }

    /// <summary>A loaded module, from libopenmpt or from the core players.</summary>
    internal unsafe interface IModule : IDisposable
    {
        int Read(int sampleRate, int frames, float* output);
        double Position { get; }
        double Duration { get; }
        void Seek(double seconds);
    }

    internal sealed class OpenMptModule : IModule
    {
        private IntPtr handle;
        public OpenMptModule(IntPtr handle) { this.handle = handle; }
        public unsafe int Read(int sampleRate, int frames, float* output) =>
            (int)OpenMpt.openmpt_module_read_interleaved_float_stereo(handle, sampleRate, (UIntPtr)frames, output);
        public double Position => OpenMpt.openmpt_module_get_position_seconds(handle);
        public double Duration => OpenMpt.openmpt_module_get_duration_seconds(handle);
        public void Seek(double seconds) => OpenMpt.openmpt_module_set_position_seconds(handle, seconds);
        public void Dispose()
        {
            if (handle != IntPtr.Zero) OpenMpt.openmpt_module_destroy(handle);
            handle = IntPtr.Zero;
        }
    }

    internal sealed class CoreModule : IModule
    {
        private IntPtr handle;
        public CoreModule(IntPtr handle) { this.handle = handle; }
        public unsafe int Read(int sampleRate, int frames, float* output) => Core.amp_read(handle, sampleRate, frames, output);
        public double Position => Core.amp_position(handle);
        public double Duration => Core.amp_duration(handle);
        public void Seek(double seconds) => Core.amp_seek(handle, seconds);
        public void Dispose()
        {
            if (handle != IntPtr.Zero) Core.amp_free(handle);
            handle = IntPtr.Zero;
        }
    }

    /// <summary>
    /// Plays one module at a time through an AudioGraph: the graph asks for each quantum of sound, and
    /// libopenmpt renders it straight into the frame's buffer.
    /// </summary>
    internal sealed class ModulePlayer : IDisposable
    {
        private readonly object gate = new object();
        private AudioGraph graph;
        private AudioFrameInputNode input;
        private IModule module;
        private int sampleRate;
        private bool playing;

        /// <summary>Raised on the audio thread when the module has played to its end.</summary>
        public event EventHandler Ended;

        public bool IsPlaying { get { lock (gate) return playing && module != null; } }

        public async Task<bool> StartAsync()
        {
            var settings = new AudioGraphSettings(AudioRenderCategory.Media);
            var created = await AudioGraph.CreateAsync(settings);
            if (created.Status != AudioGraphCreationStatus.Success) return false;
            graph = created.Graph;
            var output = await graph.CreateDeviceOutputNodeAsync();
            if (output.Status != AudioDeviceNodeCreationStatus.Success) return false;
            sampleRate = (int)graph.EncodingProperties.SampleRate;
            var format = AudioEncodingProperties.CreatePcm((uint)sampleRate, 2, 32);
            format.Subtype = MediaEncodingSubtypes.Float;
            input = graph.CreateFrameInputNode(format);
            input.AddOutgoingConnection(output.DeviceOutputNode);
            input.QuantumStarted += OnQuantumStarted;
            graph.Start();
            return true;
        }

        /// <summary>
        /// Loads and starts a module from its bytes; false if no player knows it. The core players go first,
        /// as on Android: some console formats would otherwise be taken for something else.
        /// </summary>
        public bool Play(byte[] data, string fileName, out string title, out string type)
        {
            title = type = string.Empty;
            IModule next = null;
            IntPtr core = Core.Load(data, fileName);
            if (core != IntPtr.Zero)
            {
                title = Core.Metadata(core, "title");
                type = Core.Metadata(core, "type_long");
                next = new CoreModule(core);
            }
            else
            {
                IntPtr mpt = OpenMpt.Load(data);
                if (mpt == IntPtr.Zero) return false;
                title = OpenMpt.Metadata(mpt, "title");
                type = OpenMpt.Metadata(mpt, "type_long");
                next = new OpenMptModule(mpt);
            }
            IModule old;
            lock (gate)
            {
                old = module;
                module = next;
                playing = true;
            }
            old?.Dispose();
            return true;
        }

        public void TogglePause()
        {
            lock (gate) playing = !playing;
        }

        public double Position { get { lock (gate) return module?.Position ?? 0; } }
        public double Duration { get { lock (gate) return module?.Duration ?? 0; } }

        public void Seek(double seconds)
        {
            lock (gate)
            {
                module?.Seek(Math.Max(0, seconds));
            }
        }

        private unsafe void OnQuantumStarted(AudioFrameInputNode sender, FrameInputNodeQuantumStartedEventArgs args)
        {
            int frames = args.RequiredSamples;
            if (frames <= 0) return;
            var frame = new AudioFrame((uint)(frames * 2 * sizeof(float)));
            bool ended = false;
            using (AudioBuffer buffer = frame.LockBuffer(AudioBufferAccessMode.Write))
            using (var reference = buffer.CreateReference())
            {
                ((IMemoryBufferByteAccess)reference).GetBuffer(out byte* bytes, out uint capacity);
                float* samples = (float*)bytes;
                int rendered = 0;
                lock (gate)
                {
                    if (playing && module != null)
                    {
                        rendered = module.Read(sampleRate, frames, samples);
                        if (rendered == 0)
                        {
                            playing = false;
                            ended = true;
                        }
                    }
                }
                for (int i = rendered * 2; i < frames * 2; i++) samples[i] = 0f;
            }
            sender.AddFrame(frame);
            if (ended) Ended?.Invoke(this, EventArgs.Empty);
        }

        public void Dispose()
        {
            graph?.Stop();
            graph?.Dispose();
            lock (gate)
            {
                module?.Dispose();
                module = null;
            }
        }
    }
}
