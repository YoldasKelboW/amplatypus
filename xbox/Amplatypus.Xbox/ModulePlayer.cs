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
    /// <summary>Where a module is and how loud its channels are, for the tracker view.</summary>
    internal sealed class TrackerState
    {
        public int Order, Pattern, Row, Orders, Speed, Tempo;
        /// <summary>Which module the state belongs to; a new one starts the rows afresh.</summary>
        public int Generation = -1;
        public float[] Levels = new float[0];
        /// <summary>The playing pattern's rows, split into cells; empty for formats without patterns.</summary>
        public string[][] Rows = new string[0][];
    }

    internal unsafe interface IModule : IDisposable
    {
        int Read(int sampleRate, int frames, float* output);
        double Position { get; }
        double Duration { get; }
        void Seek(double seconds);
        /// <summary>Fills in position, tempo and levels; the rows only when the pattern changed.</summary>
        void Fill(TrackerState state);
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

        public void Fill(TrackerState state)
        {
            int pattern = OpenMpt.openmpt_module_get_current_pattern(handle);
            int channels = OpenMpt.openmpt_module_get_num_channels(handle);
            if (pattern != state.Pattern || state.Rows.Length == 0)
            {
                int rows = Math.Max(0, OpenMpt.openmpt_module_get_pattern_num_rows(handle, pattern));
                state.Rows = new string[rows][];
                for (int row = 0; row < rows; row++)
                {
                    var cells = new string[channels];
                    for (int channel = 0; channel < channels; channel++) cells[channel] = OpenMpt.Cell(handle, pattern, row, channel);
                    state.Rows[row] = cells;
                }
            }
            state.Pattern = pattern;
            state.Order = OpenMpt.openmpt_module_get_current_order(handle);
            state.Row = OpenMpt.openmpt_module_get_current_row(handle);
            state.Orders = OpenMpt.openmpt_module_get_num_orders(handle);
            state.Speed = OpenMpt.openmpt_module_get_current_speed(handle);
            state.Tempo = (int)Math.Round(OpenMpt.openmpt_module_get_current_tempo2(handle));
            if (state.Levels.Length != channels) state.Levels = new float[channels];
            for (int channel = 0; channel < channels; channel++) state.Levels[channel] = OpenMpt.openmpt_module_get_current_channel_vu_mono(handle, channel);
        }

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

        public void Fill(TrackerState state)
        {
            Core.amp_play_position(handle, out int order, out int pattern, out int row);
            if (pattern != state.Pattern || state.Rows.Length == 0) state.Rows = Core.Pattern(handle, pattern);
            state.Order = order;
            state.Pattern = pattern;
            state.Row = row;
            state.Orders = Core.amp_order_count(handle);
            Core.amp_tempo(handle, out int speed, out int bpm);
            state.Speed = speed;
            state.Tempo = bpm;
            int channels = Core.amp_channels(handle);
            if (state.Levels.Length != channels) state.Levels = new float[channels];
            for (int channel = 0; channel < channels; channel++) state.Levels[channel] = Core.amp_level(handle, channel);
        }

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
        private int generation;
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
                generation++;
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

        /// <summary>Updates [state] from the playing module; false when nothing plays.</summary>
        public bool Fill(TrackerState state)
        {
            lock (gate)
            {
                if (module == null) return false;
                if (state.Generation != generation)
                {
                    state.Generation = generation;
                    state.Rows = new string[0][];
                }
                module.Fill(state);
                return true;
            }
        }

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
