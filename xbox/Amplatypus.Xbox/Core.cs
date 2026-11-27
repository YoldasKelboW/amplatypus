// SPDX-License-Identifier: GPL-3.0-or-later
using System;
using System.Runtime.InteropServices;
using System.Text;

namespace Amplatypus.Xbox
{
    /// <summary>
    /// The Android app's players built for UWP (amplatypus_core.dll, from xbox/core): tracker modules through
    /// libopenmpt, console and home computer music through Game Music Emu, AHX and HivelyTracker modules.
    /// </summary>
    internal static class Core
    {
        private const string Lib = "amplatypus_core.dll";

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        private static extern IntPtr amp_load(byte[] data, int length, byte[] name);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern void amp_free(IntPtr player);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern unsafe int amp_read(IntPtr player, int sampleRate, int frames, float* output);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern double amp_duration(IntPtr player);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern double amp_position(IntPtr player);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern void amp_seek(IntPtr player, double seconds);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern int amp_channels(IntPtr player);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern float amp_level(IntPtr player, int channel);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern void amp_mute(IntPtr player, int channel, int mute);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern int amp_can_mute(IntPtr player);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern int amp_own_scopes(IntPtr player);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern unsafe void amp_player_scopes(IntPtr player, int points, float* output);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern IntPtr amp_scopes_create(byte[] data, int length, int channels);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern void amp_scopes_free(IntPtr scopes);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern int amp_scopes_channels(IntPtr scopes);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern void amp_scopes_seek(IntPtr scopes, double seconds);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern unsafe void amp_scopes_render(IntPtr scopes, int sampleRate, int frames, float* output, int points);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern void amp_play_position(IntPtr player, out int order, out int pattern, out int row);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern int amp_order_count(IntPtr player);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern void amp_tempo(IntPtr player, out int speed, out int bpm);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        private static extern int amp_pattern(IntPtr player, int pattern, byte[] buffer, int size);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        private static extern int amp_metadata(IntPtr player, byte[] key, byte[] buffer, int size);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        private static extern IntPtr amp_extensions();

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        private static extern IntPtr amp_tracker_extensions();

        /// <summary>A player for the module, or IntPtr.Zero if none here knows it.</summary>
        internal static IntPtr Load(byte[] data, string fileName) => amp_load(data, data.Length, Utf8(fileName));

        internal static string Metadata(IntPtr player, string key)
        {
            byte[] keyBytes = Utf8(key);
            int length = amp_metadata(player, keyBytes, null, 0);
            if (length <= 0) return string.Empty;
            byte[] buffer = new byte[length + 1];
            amp_metadata(player, keyBytes, buffer, buffer.Length);
            return Encoding.UTF8.GetString(buffer, 0, length);
        }

        /// <summary>A pattern's rows, each split into its channels' cells; empty for formats without patterns.</summary>
        internal static string[][] Pattern(IntPtr player, int pattern)
        {
            int length = amp_pattern(player, pattern, null, 0);
            if (length <= 0) return new string[0][];
            byte[] buffer = new byte[length + 1];
            amp_pattern(player, pattern, buffer, buffer.Length);
            string text = Encoding.UTF8.GetString(buffer, 0, length);
            string[] rows = text.Split('\n');
            var result = new string[rows.Length][];
            for (int i = 0; i < rows.Length; i++) result[i] = rows[i].Split('|');
            return result;
        }

        /// <summary>Extensions of the console and home computer players, without the dot.</summary>
        internal static string[] SupportedExtensions() => Split(amp_extensions());

        /// <summary>Extensions of the tracker formats (libopenmpt), without the dot.</summary>
        internal static string[] TrackerExtensions() => Split(amp_tracker_extensions());

        private static string[] Split(IntPtr list) =>
            (Marshal.PtrToStringAnsi(list) ?? string.Empty).Split(new[] { ';' }, StringSplitOptions.RemoveEmptyEntries);

        private static byte[] Utf8(string text)
        {
            byte[] bytes = Encoding.UTF8.GetBytes(text ?? string.Empty);
            Array.Resize(ref bytes, bytes.Length + 1);
            return bytes;
        }
    }
}
