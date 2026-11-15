// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.audio

/**
 * JNI bindings to the bundled replayers: libopenmpt, and the HivelyTracker replayer for AHX and HVL.
 * The native side picks the replayer from the file's contents. A handle of 0 means "no module".
 * Not thread-safe; callers serialise access.
 */
object OpenMpt {
    init {
        System.loadLibrary("amplatypus")
    }

    @JvmStatic external fun nativeLoad(data: ByteArray): Long
    @JvmStatic external fun nativeClose(handle: Long)
    @JvmStatic external fun nativeRead(handle: Long, sampleRate: Int, buffer: FloatArray, frames: Int): Int
    @JvmStatic external fun nativeDuration(handle: Long): Double
    @JvmStatic external fun nativePosition(handle: Long): Double
    @JvmStatic external fun nativeSeek(handle: Long, seconds: Double)
    @JvmStatic external fun nativeSetStereoSeparation(handle: Long, percent: Int)
    @JvmStatic external fun nativeChannelCount(handle: Long): Int
    @JvmStatic external fun nativeSetChannelMute(handle: Long, channel: Int, mute: Boolean)
    @JvmStatic external fun nativeMetadata(handle: Long, key: String): ByteArray?
    @JvmStatic external fun nativeNames(handle: Long, kind: Int): ByteArray?
    @JvmStatic external fun nativeVu(handle: Long, out: FloatArray): Int
    @JvmStatic external fun nativePlayPosition(handle: Long): Long
    @JvmStatic external fun nativePattern(handle: Long, pattern: Int): ByteArray?

    @JvmStatic external fun nativeScopesCreate(data: ByteArray, channels: Int): Long
    @JvmStatic external fun nativeScopesClose(handle: Long)
    @JvmStatic external fun nativeScopesSeek(handle: Long, seconds: Double)
    @JvmStatic external fun nativeScopesRender(handle: Long, sampleRate: Int, frames: Int, out: FloatArray, points: Int)
}
