// SPDX-License-Identifier: GPL-3.0-or-later
using System;
using System.Runtime.InteropServices;
using System.Text;

namespace Amplatypus.Xbox
{
    /// <summary>
    /// The Android app's players built for UWP (amplatypus_core.dll, from xbox/core): console and home
    /// computer music through Game Music Emu, AHX and HivelyTracker modules.
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
        private static extern int amp_metadata(IntPtr player, byte[] key, byte[] buffer, int size);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        private static extern IntPtr amp_extensions();

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

        /// <summary>File extensions the players here take, without the dot.</summary>
        internal static string[] SupportedExtensions() =>
            (Marshal.PtrToStringAnsi(amp_extensions()) ?? string.Empty).Split(new[] { ';' }, StringSplitOptions.RemoveEmptyEntries);

        private static byte[] Utf8(string text)
        {
            byte[] bytes = Encoding.UTF8.GetBytes(text ?? string.Empty);
            Array.Resize(ref bytes, bytes.Length + 1);
            return bytes;
        }
    }
}
