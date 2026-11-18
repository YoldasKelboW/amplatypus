// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import kotlin.concurrent.thread

/**
 * Renders one module at a time with libopenmpt and plays it through an [AudioTrack]
 * (48 kHz, stereo, float). All native calls happen under [lock].
 *
 * [onEnded] is called from the audio thread when the song has played to its end.
 */
class ModuleEngine(
    private val onEnded: () -> Unit,
    /** Another song inside the same file started (chosen, or the previous one ended). */
    private val onSubsongChanged: () -> Unit = {},
) {
    private val lock = Object()
    private var handle = 0L
    private var playing = false
    private var released = false

    // What the visualiser needs, recorded per rendered chunk and handed out when that chunk is audible.
    private class Frame(val endFrame: Long, val levels: FloatArray, val position: Long, val waves: FloatArray?, val tempo: Int)

    // Per-channel oscilloscopes: only kept alive while the visualiser is on screen.
    private var scopes = 0L
    // The replayer delivers the channel waveforms itself (UADE); no extra copies of the module needed.
    private var ownScopes = false
    private var scopesWanted = false
    private var scopesLoading = false

    // Channels the listener has switched off; cleared with every new module.
    private var muted = BooleanArray(0)
    private var file: File? = null
    private val frames = ArrayDeque<Frame>()
    private var writtenFrames = 0L
    private var headAtFlush = 0L
    private var lastVisual: Visual? = null
    private var info: ModuleInfo? = null
    private var generation = 0

    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(
            maxOf(
                AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT) * 2,
                FRAMES_PER_CHUNK * 2 * 4 * 2,
            ),
        )
        .build()

    private val audioThread = thread(name = "module-audio", priority = Thread.MAX_PRIORITY) { renderLoop() }

    val durationMs: Long
        get() = synchronized(lock) { (OpenMpt.nativeDuration(handle) * 1000).toLong() }

    val positionMs: Long
        get() = synchronized(lock) { (OpenMpt.nativePosition(handle) * 1000).toLong() }

    val channelCount: Int
        get() = synchronized(lock) { OpenMpt.nativeChannelCount(handle) }

    /** Loads [file], replacing the current module. Returns false if it cannot be read or decoded. */
    fun load(file: File): Boolean {
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return false
        }
        val newHandle = OpenMpt.nativeLoad(bytes, file.absolutePath)
        synchronized(lock) {
            if (released) {
                OpenMpt.nativeClose(newHandle)
                return false
            }
            val old = handle
            handle = newHandle
            OpenMpt.nativeClose(old)
            if (newHandle != 0L) OpenMpt.nativeSetStereoSeparation(newHandle, DEFAULT_STEREO_SEPARATION)
            generation++
            closeScopes()
            this.file = if (newHandle != 0L) file else null
            info = if (newHandle != 0L) readInfo(newHandle) else null
            ownScopes = newHandle != 0L && OpenMpt.nativeOwnScopes(newHandle)
            muted = BooleanArray(info?.channels ?: 0)
            lastVisual = null
            discardBuffered()
            lock.notifyAll()
        }
        if (newHandle != 0L) loadScopesIfWanted()
        return newHandle != 0L
    }

    /**
     * Turns the per-channel oscilloscopes on or off. They cost one extra copy of the module per
     * channel, so the UI enables them only while they are visible.
     */
    fun setScopesEnabled(enabled: Boolean) {
        synchronized(lock) {
            scopesWanted = enabled
            if (!enabled) closeScopes()
        }
        if (enabled) loadScopesIfWanted()
    }

    private fun loadScopesIfWanted() {
        val source: File
        val channels: Int
        val wantedGeneration: Int
        synchronized(lock) {
            if (!scopesWanted || scopesLoading || scopes != 0L || ownScopes || released) return
            source = file ?: return
            channels = info?.channels ?: return
            if (channels !in 1..MAX_SCOPE_CHANNELS || source.length() * channels > MAX_SCOPE_BYTES) return
            wantedGeneration = generation
            scopesLoading = true
        }
        // Parsing the copies takes a moment, so it must not happen on the audio or UI thread.
        thread(name = "module-scopes") {
            val created = try {
                OpenMpt.nativeScopesCreate(source.readBytes(), channels)
            } catch (_: Exception) {
                0L
            }
            var retry = false
            synchronized(lock) {
                scopesLoading = false
                if (released || !scopesWanted || generation != wantedGeneration || created == 0L) {
                    OpenMpt.nativeScopesClose(created)
                    retry = !released && scopesWanted && generation != wantedGeneration
                } else {
                    OpenMpt.nativeScopesSeek(created, OpenMpt.nativePosition(handle))
                    scopes = created
                }
            }
            if (retry) loadScopesIfWanted()
        }
    }

    // Caller holds the lock.
    private fun closeScopes() {
        OpenMpt.nativeScopesClose(scopes)
        scopes = 0L
    }

    fun unload() = synchronized(lock) {
        playing = false
        OpenMpt.nativeClose(handle)
        handle = 0L
        closeScopes()
        file = null
        info = null
        lastVisual = null
        discardBuffered()
    }

    /** Which channels are muted, one entry per channel. */
    fun mutedChannels(): BooleanArray = synchronized(lock) { muted.copyOf() }

    /** Mutes or unmutes one channel. Returns the new state of all channels. */
    fun toggleMute(channel: Int): BooleanArray = synchronized(lock) {
        if (channel in muted.indices && info?.canMute == true) setMuted(channel, !muted[channel])
        muted.copyOf()
    }

    /**
     * Plays [channel] alone. If it already is the only one playing, all channels are switched back on.
     * Returns the new state of all channels.
     */
    fun toggleSolo(channel: Int): BooleanArray = synchronized(lock) {
        if (channel in muted.indices && info?.canMute == true) {
            val alreadySolo = muted.indices.all { muted[it] == (it != channel) }
            for (index in muted.indices) setMuted(index, !alreadySolo && index != channel)
        }
        muted.copyOf()
    }

    // Caller holds the lock.
    private fun setMuted(channel: Int, mute: Boolean) {
        muted[channel] = mute
        OpenMpt.nativeSetChannelMute(handle, channel, mute)
    }

    /** Static facts about the loaded module, or null if none is loaded. */
    fun moduleInfo(): ModuleInfo? = synchronized(lock) { info }

    /** The playing song inside the file and how many there are (current from 0, count at least 1). */
    fun subsongs(): Pair<Int, Int> = synchronized(lock) {
        if (handle == 0L) 0 to 1 else OpenMpt.nativeSubsong(handle) to OpenMpt.nativeSubsongCount(handle).coerceAtLeast(1)
    }

    /** Starts the song [index] inside the file from its beginning. */
    fun selectSubsong(index: Int): Boolean {
        val changed = synchronized(lock) {
            if (handle == 0L || !OpenMpt.nativeSelectSubsong(handle, index)) return false
            lastVisual = null
            discardBuffered()
            lock.notifyAll()
            true
        }
        if (changed) onSubsongChanged()
        return changed
    }

    /** The rows of [pattern] as text, one string per row with channels separated by `|`. */
    fun patternRows(pattern: Int): List<String> = synchronized(lock) {
        OpenMpt.nativePattern(handle, pattern)?.toString(Charsets.UTF_8)?.trimEnd('\n')?.split('\n') ?: emptyList()
    }

    /** Channel levels and play position of the audio that is coming out of the speaker right now. */
    fun visual(): Visual? = synchronized(lock) {
        if (released || handle == 0L) return@synchronized null
        val head = ((track.playbackHeadPosition.toLong() and 0xFFFFFFFFL) - headAtFlush) and 0xFFFFFFFFL
        while (frames.size > 1 && frames.first().endFrame <= head) frames.removeFirst()
        val frame = frames.firstOrNull() ?: return@synchronized lastVisual
        if (lastVisual?.source !== frame) {
            lastVisual = Visual(
                levels = frame.levels,
                order = (frame.position ushr 40).toInt(),
                pattern = ((frame.position ushr 20) and 0xFFFFF).toInt(),
                row = (frame.position and 0xFFFFF).toInt(),
                waves = frame.waves,
                playing = playing,
                speed = frame.tempo ushr 16,
                bpm = frame.tempo and 0xFFFF,
                source = frame,
            )
        }
        lastVisual
    }

    fun play() = synchronized(lock) {
        if (released || handle == 0L) return@synchronized
        playing = true
        track.play()
        lock.notifyAll()
    }

    fun pause() = synchronized(lock) {
        playing = false
        if (!released) track.pause()
    }

    fun seekTo(positionMs: Long) = synchronized(lock) {
        OpenMpt.nativeSeek(handle, positionMs / 1000.0)
        OpenMpt.nativeScopesSeek(scopes, positionMs / 1000.0)
        discardBuffered()
    }

    fun setVolume(volume: Float) {
        if (!released) track.setVolume(volume)
    }

    fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            playing = false
            if (current === this) current = null
            closeScopes()
            OpenMpt.nativeClose(handle)
            handle = 0L
            lock.notifyAll()
        }
        track.pause()
        track.flush()
        audioThread.join(500)
        track.release()
    }

    // Caller holds the lock. Drops audio that was rendered before a seek or track change.
    private fun discardBuffered() {
        if (released) return
        val wasPlaying = track.playState == AudioTrack.PLAYSTATE_PLAYING
        track.pause()
        track.flush()
        frames.clear()
        writtenFrames = 0
        headAtFlush = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        if (wasPlaying && playing) track.play()
    }

    private fun renderLoop() {
        val buffer = FloatArray(FRAMES_PER_CHUNK * 2)
        while (true) {
            var frames: Int
            var nextSubsong = false
            synchronized(lock) {
                while (!released && (!playing || handle == 0L)) lock.wait()
                if (released) return
                frames = OpenMpt.nativeRead(handle, SAMPLE_RATE, buffer, FRAMES_PER_CHUNK)
                if (frames == 0) {
                    // A file with several songs goes on with the next one before the queue moves on.
                    val count = OpenMpt.nativeSubsongCount(handle)
                    val current = OpenMpt.nativeSubsong(handle)
                    if (current + 1 < count && OpenMpt.nativeSelectSubsong(handle, current + 1)) {
                        nextSubsong = true
                    } else {
                        playing = false
                    }
                } else {
                    val levels = FloatArray(info?.channels ?: 0)
                    OpenMpt.nativeVu(handle, levels)
                    var waves: FloatArray? = null
                    if (scopes != 0L) {
                        waves = FloatArray(levels.size * SCOPE_POINTS)
                        OpenMpt.nativeScopesRender(scopes, SAMPLE_RATE, frames, waves, SCOPE_POINTS)
                    } else if (ownScopes && scopesWanted) {
                        waves = FloatArray(levels.size * SCOPE_POINTS)
                        OpenMpt.nativeOwnScopesRender(handle, waves, SCOPE_POINTS)
                    }
                    writtenFrames += frames
                    this.frames.addLast(Frame(writtenFrames, levels, OpenMpt.nativePlayPosition(handle), waves, OpenMpt.nativeTempo(handle)))
                    if (this.frames.size > MAX_PENDING_FRAMES) this.frames.removeFirst()
                }
            }
            if (frames > 0) {
                track.write(buffer, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
            } else if (nextSubsong) {
                onSubsongChanged()
            } else {
                onEnded()
            }
        }
    }

    private fun readInfo(handle: Long): ModuleInfo {
        fun meta(key: String) = OpenMpt.nativeMetadata(handle, key)?.toString(Charsets.UTF_8).orEmpty()
        fun names(kind: Int) = OpenMpt.nativeNames(handle, kind)?.toString(Charsets.UTF_8)
            ?.trimEnd('\n')?.split('\n')?.takeIf { list -> list.any { it.isNotBlank() } } ?: emptyList()
        return ModuleInfo(
            generation = generation,
            title = meta("title"),
            type = meta("type_long"),
            tracker = meta("tracker"),
            channels = OpenMpt.nativeChannelCount(handle).coerceAtMost(MAX_CHANNELS),
            message = meta("message_raw").trimEnd(),
            samples = names(0),
            instruments = names(1),
            canMute = OpenMpt.nativeCanMute(handle),
            orders = OpenMpt.nativeOrderCount(handle),
            hasPattern = OpenMpt.nativePattern(handle, 0)?.isNotEmpty() == true,
        )
    }

    init {
        current = this
    }

    /** Facts about a loaded module. [generation] changes with every loaded module. */
    data class ModuleInfo(
        val generation: Int,
        val title: String,
        val type: String,
        val tracker: String,
        val channels: Int,
        val message: String,
        val samples: List<String>,
        val instruments: List<String>,
        /** Channels can be muted while the module plays. */
        val canMute: Boolean = true,
        /** The replayer can show the module's patterns. */
        val hasPattern: Boolean = true,
        /** Song length in orders (positions); 0 if unknown. */
        val orders: Int = 0,
    )

    class Visual(
        /** One level per channel, 0..1. */
        val levels: FloatArray,
        val order: Int,
        val pattern: Int,
        val row: Int,
        /** [SCOPE_POINTS] waveform samples per channel, or null while the scopes are off or loading. */
        val waves: FloatArray?,
        val playing: Boolean,
        /** Ticks per row and beats per minute; 0 where the format has none. */
        val speed: Int = 0,
        val bpm: Int = 0,
        internal val source: Any,
    )

    companion object {
        const val SAMPLE_RATE = 48_000
        private const val FRAMES_PER_CHUNK = 1_024
        private const val MAX_PENDING_FRAMES = 64
        private const val MAX_CHANNELS = 64
        const val SCOPE_POINTS = 128
        private const val MAX_SCOPE_CHANNELS = 32
        private const val MAX_SCOPE_BYTES = 64L * 1024 * 1024

        /** The engine of the running playback service, for the visualiser. Same process, so no IPC. */
        @Volatile
        var current: ModuleEngine? = null

        // Same default as 4champ (0-100).
        private const val DEFAULT_STEREO_SEPARATION = 40
    }
}
