// SPDX-License-Identifier: GPL-3.0-or-later
using System;
using System.Runtime.InteropServices;
using System.Text;

namespace Amplatypus.Xbox
{
    /// <summary>The parts of libopenmpt's C interface the player uses (libopenmpt-small.dll, built for UWP).</summary>
    internal static class OpenMpt
    {
        private const string Lib = "libopenmpt-small.dll";

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        private static extern IntPtr openmpt_module_create_from_memory2(
            byte[] data, UIntPtr size, IntPtr logFunc, IntPtr logUser, IntPtr errorFunc, IntPtr errorUser,
            out int error, out IntPtr errorMessage, IntPtr initialCtls);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern void openmpt_module_destroy(IntPtr module);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern unsafe UIntPtr openmpt_module_read_interleaved_float_stereo(
            IntPtr module, int sampleRate, UIntPtr count, float* buffer);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern double openmpt_module_get_duration_seconds(IntPtr module);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern double openmpt_module_get_position_seconds(IntPtr module);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern double openmpt_module_set_position_seconds(IntPtr module, double seconds);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        internal static extern int openmpt_module_set_repeat_count(IntPtr module, int count);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        private static extern IntPtr openmpt_module_get_metadata(IntPtr module, [MarshalAs(UnmanagedType.LPStr)] string key);

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        private static extern IntPtr openmpt_get_supported_extensions();

        [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
        private static extern void openmpt_free_string(IntPtr text);

        /// <summary>A module loaded from its bytes, or IntPtr.Zero if libopenmpt does not know the format.</summary>
        internal static IntPtr Load(byte[] data)
        {
            IntPtr message;
            int error;
            IntPtr module = openmpt_module_create_from_memory2(
                data, (UIntPtr)data.Length, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero,
                out error, out message, IntPtr.Zero);
            if (message != IntPtr.Zero) openmpt_free_string(message);
            if (module != IntPtr.Zero) openmpt_module_set_repeat_count(module, 0);
            return module;
        }

        /// <summary>A metadata value: "title", "artist", "type_long", "tracker", "message".</summary>
        internal static string Metadata(IntPtr module, string key) => TakeString(openmpt_module_get_metadata(module, key));

        /// <summary>File extensions libopenmpt plays, without the dot.</summary>
        internal static string[] SupportedExtensions() =>
            TakeString(openmpt_get_supported_extensions()).Split(new[] { ';' }, StringSplitOptions.RemoveEmptyEntries);

        // libopenmpt's strings are UTF-8 and freed with openmpt_free_string.
        private static string TakeString(IntPtr text)
        {
            if (text == IntPtr.Zero) return string.Empty;
            try
            {
                int length = 0;
                while (Marshal.ReadByte(text, length) != 0) length++;
                byte[] bytes = new byte[length];
                Marshal.Copy(text, bytes, 0, length);
                return Encoding.UTF8.GetString(bytes);
            }
            finally
            {
                openmpt_free_string(text);
            }
        }
    }
}
