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

    /// <summary>Where a module is, how loud its channels are and what they play, for the tracker view.</summary>
    internal sealed class TrackerState
    {
        public int Order, Pattern, Row, Orders, Speed, Tempo;
        /// <summary>Which module the state belongs to; a new one starts the rows afresh.</summary>
        public int Generation = -1;
        public float[] Levels = new float[0];
        /// <summary>The playing pattern's rows, split into cells; empty for formats without patterns.</summary>
        public string[][] Rows = new string[0][];
        /// <summary>Each channel's waveform, ScopePoints values after another; empty when there are none.</summary>
        public float[] Scopes = new float[0];
        public const int ScopePoints = 128;
        public bool[] Muted = new bool[0];
        public bool CanMute;
    }

    /// <summary>
    /// Plays one module at a time through an AudioGraph: the graph asks for each quantum of sound, and the
    /// core players render it straight into the frame's buffer. The channel waveforms are rendered with
    /// each quantum too and kept in a ring, from which the tracker view takes the latest.
    /// </summary>
    internal sealed class ModulePlayer : IDisposable
    {
        // The span of a scope: about 43 ms at 48 kHz, enough for a bass note's waves.
        private const int ScopeSpan = 2048;
        private const int RingSize = 4096;

        private readonly object gate = new object();
        private AudioGraph graph;
        private AudioFrameInputNode input;
        private IntPtr module;
        private IntPtr scopes;
        private bool ownScopes;
        private int channels;
        // How many channels the scope source writes; the ring keeps the first `channels` of them.
        private int scopeChannels;
        private bool canMute;
        private bool[] muted = new bool[0];
        private float[] ring = new float[0];
        private int ringWrite;
        private float[] scratch = new float[0];
        private int generation;
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

        /// <summary>
        /// Loads and starts a module from its bytes; false if no player knows it. The console players go
        /// first and libopenmpt last, as on Android: some console formats would otherwise be taken for
        /// something else.
        /// </summary>
        public bool Play(byte[] data, string fileName, out string title, out string type)
        {
            title = type = string.Empty;
            IntPtr next = Core.Load(data, fileName);
            if (next == IntPtr.Zero) return false;
            title = Core.Metadata(next, "title");
            type = Core.Metadata(next, "type_long");
            int nextChannels = Math.Max(0, Core.amp_channels(next));
            bool nextOwn = Core.amp_own_scopes(next) != 0;
            // The others' waveforms come from a copy of the module per channel, made here and not on the
            // audio thread.
            IntPtr nextScopes = nextOwn || nextChannels == 0 ? IntPtr.Zero : Core.amp_scopes_create(data, data.Length, nextChannels);
            int nextScopeChannels = nextOwn ? nextChannels : (nextScopes != IntPtr.Zero ? Core.amp_scopes_channels(nextScopes) : 0);
            IntPtr oldModule, oldScopes;
            lock (gate)
            {
                oldModule = module;
                oldScopes = scopes;
                module = next;
                scopes = nextScopes;
                ownScopes = nextOwn;
                channels = nextChannels;
                scopeChannels = nextScopeChannels;
                canMute = Core.amp_can_mute(next) != 0;
                muted = new bool[nextChannels];
                ring = new float[nextChannels * RingSize];
                ringWrite = 0;
                generation++;
                playing = true;
            }
            if (oldScopes != IntPtr.Zero) Core.amp_scopes_free(oldScopes);
            if (oldModule != IntPtr.Zero) Core.amp_free(oldModule);
            return true;
        }

        public void TogglePause()
        {
            lock (gate) playing = !playing;
        }

        public double Position { get { lock (gate) return module != IntPtr.Zero ? Core.amp_position(module) : 0; } }
        public double Duration { get { lock (gate) return module != IntPtr.Zero ? Core.amp_duration(module) : 0; } }

        /// <summary>Mutes or unmutes one channel.</summary>
        public void ToggleMute(int channel)
        {
            lock (gate)
            {
                if (!canMute || channel < 0 || channel >= channels) return;
                SetMute(channel, !muted[channel]);
            }
        }

        /// <summary>Plays only this channel; again on the only channel playing, plays all of them.</summary>
        public void Solo(int channel)
        {
            lock (gate)
            {
                if (!canMute || channel < 0 || channel >= channels) return;
                bool alone = !muted[channel];
                for (int c = 0; c < channels && alone; c++) if (c != channel && !muted[c]) alone = false;
                for (int c = 0; c < channels; c++) SetMute(c, alone ? false : c != channel);
            }
        }

        // Under the lock.
        private void SetMute(int channel, bool mute)
        {
            if (muted[channel] == mute) return;
            muted[channel] = mute;
            Core.amp_mute(module, channel, mute ? 1 : 0);
        }

        /// <summary>Updates [state] from the playing module; false when nothing plays.</summary>
        public bool Fill(TrackerState state)
        {
            lock (gate)
            {
                if (module == IntPtr.Zero) return false;
                if (state.Generation != generation)
                {
                    state.Generation = generation;
                    state.Rows = new string[0][];
                }
                Core.amp_play_position(module, out int order, out int pattern, out int row);
                if (pattern != state.Pattern || state.Rows.Length == 0) state.Rows = Core.Pattern(module, pattern);
                state.Order = order;
                state.Pattern = pattern;
                state.Row = row;
                state.Orders = Core.amp_order_count(module);
                Core.amp_tempo(module, out int speed, out int bpm);
                state.Speed = speed;
                state.Tempo = bpm;
                if (state.Levels.Length != channels) state.Levels = new float[channels];
                for (int c = 0; c < channels; c++) state.Levels[c] = Core.amp_level(module, c);
                if (state.Muted.Length != channels) state.Muted = new bool[channels];
                Array.Copy(muted, state.Muted, channels);
                state.CanMute = canMute;
                FillScopes(state);
                return true;
            }
        }

        // The latest ScopeSpan samples of each channel, thinned out to ScopePoints.
        private void FillScopes(TrackerState state)
        {
            bool any = ownScopes || scopes != IntPtr.Zero;
            int size = any ? channels * TrackerState.ScopePoints : 0;
            if (state.Scopes.Length != size) state.Scopes = new float[size];
            if (!any) return;
            int step = ScopeSpan / TrackerState.ScopePoints;
            int start = ringWrite - ScopeSpan + RingSize;
            for (int c = 0; c < channels; c++)
            {
                int offset = c * RingSize;
                for (int i = 0; i < TrackerState.ScopePoints; i++)
                {
                    state.Scopes[c * TrackerState.ScopePoints + i] = ring[offset + (start + i * step) % RingSize];
                }
            }
        }

        public void Seek(double seconds)
        {
            lock (gate)
            {
                if (module == IntPtr.Zero) return;
                seconds = Math.Max(0, seconds);
                Core.amp_seek(module, seconds);
                // The scope copies follow to where the player actually is.
                if (scopes != IntPtr.Zero) Core.amp_scopes_seek(scopes, Core.amp_position(module));
            }
        }

        // Under the lock: the channel waveforms of the frames just rendered, into the ring.
        private unsafe void RenderScopes(int frames)
        {
            if (scopeChannels == 0 || frames <= 0 || (!ownScopes && scopes == IntPtr.Zero)) return;
            if (scratch.Length < scopeChannels * frames) scratch = new float[scopeChannels * frames];
            fixed (float* output = scratch)
            {
                // One point per frame: the ring gets every sample, and the view thins them out.
                if (ownScopes) Core.amp_player_scopes(module, frames, output);
                else Core.amp_scopes_render(scopes, sampleRate, frames, output, frames);
            }
            for (int c = 0; c < Math.Min(channels, scopeChannels); c++)
            {
                int offset = c * RingSize;
                for (int i = 0; i < frames; i++) ring[offset + (ringWrite + i) % RingSize] = scratch[c * frames + i];
            }
            ringWrite = (ringWrite + frames) % RingSize;
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
                        rendered = Core.amp_read(module, sampleRate, frames, samples);
                        if (rendered == 0)
                        {
                            playing = false;
                            ended = true;
                        }
                        RenderScopes(rendered);
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
                if (scopes != IntPtr.Zero) Core.amp_scopes_free(scopes);
                if (module != IntPtr.Zero) Core.amp_free(module);
                scopes = module = IntPtr.Zero;
            }
        }
    }
}
