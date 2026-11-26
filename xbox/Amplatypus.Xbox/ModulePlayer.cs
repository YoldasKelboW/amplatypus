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

    /// <summary>
    /// Plays one module at a time through an AudioGraph: the graph asks for each quantum of sound, and
    /// libopenmpt renders it straight into the frame's buffer.
    /// </summary>
    internal sealed class ModulePlayer : IDisposable
    {
        private readonly object gate = new object();
        private AudioGraph graph;
        private AudioFrameInputNode input;
        private IntPtr module = IntPtr.Zero;
        private int sampleRate;
        private bool playing;

        /// <summary>Raised on the audio thread when the module has played to its end.</summary>
        public event EventHandler Ended;

        public bool IsPlaying { get { lock (gate) return playing && module != IntPtr.Zero; } }

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

        /// <summary>Loads and starts a module from its bytes; false if libopenmpt cannot play it.</summary>
        public bool Play(byte[] data, out string title, out string type)
        {
            title = type = string.Empty;
            IntPtr next = OpenMpt.Load(data);
            if (next == IntPtr.Zero) return false;
            title = OpenMpt.Metadata(next, "title");
            type = OpenMpt.Metadata(next, "type_long");
            lock (gate)
            {
                if (module != IntPtr.Zero) OpenMpt.openmpt_module_destroy(module);
                module = next;
                playing = true;
            }
            return true;
        }

        public void TogglePause()
        {
            lock (gate) playing = !playing;
        }

        public double Position { get { lock (gate) return module == IntPtr.Zero ? 0 : OpenMpt.openmpt_module_get_position_seconds(module); } }
        public double Duration { get { lock (gate) return module == IntPtr.Zero ? 0 : OpenMpt.openmpt_module_get_duration_seconds(module); } }

        public void Seek(double seconds)
        {
            lock (gate)
            {
                if (module != IntPtr.Zero) OpenMpt.openmpt_module_set_position_seconds(module, Math.Max(0, seconds));
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
                    if (playing && module != IntPtr.Zero)
                    {
                        rendered = (int)OpenMpt.openmpt_module_read_interleaved_float_stereo(module, sampleRate, (UIntPtr)frames, samples);
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
                if (module != IntPtr.Zero) OpenMpt.openmpt_module_destroy(module);
                module = IntPtr.Zero;
            }
        }
    }
}
