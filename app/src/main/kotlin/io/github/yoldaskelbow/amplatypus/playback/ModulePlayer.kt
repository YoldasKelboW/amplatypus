// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.yoldaskelbow.amplatypus.audio.ModuleEngine
import io.github.yoldaskelbow.amplatypus.audio.UadeFiles
import java.io.File

/**
 * A Media3 [Player] that plays tracker modules through [ModuleEngine]. Each [MediaItem] carries the
 * absolute path of the module file as its media id. Media3 builds the media session, the notification
 * and the lock-screen controls on top of this player.
 *
 * [onStarted] is called with the file path every time a module starts playing.
 */
@UnstableApi
class ModulePlayer(
    private val context: Context,
    looper: Looper,
    private val onStarted: (String) -> Unit,
) : SimpleBasePlayer(looper) {
    private val handler = Handler(looper)
    private val engine = ModuleEngine(onEnded = { handler.post(::onTrackEnded) })
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var items: List<MediaItem> = emptyList()
    private var index = 0
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var durationMs = C.TIME_UNSET
    private var error: PlaybackException? = null
    private var hasFocus = false
    private var pausedByFocusLoss = false
    private var repeatMode = Player.REPEAT_MODE_OFF
    // Shuffle reorders the queue itself and keeps the order it had before, to go back to.
    private var shuffled = false
    private var unshuffled: List<MediaItem> = emptyList()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        handler.post { onFocusChange(change) }
    }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    // Pause when headphones are unplugged.
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (playWhenReady) {
                setPlaying(false)
                invalidateState()
            }
        }
    }

    init {
        context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setAudioAttributes(AUDIO_ATTRIBUTES)
            .setPlayerError(error)
            .setRepeatMode(repeatMode)
            .setShuffleModeEnabled(shuffled)
        if (items.isEmpty()) {
            return builder
                .setPlaybackState(if (playbackState == Player.STATE_ENDED) Player.STATE_ENDED else Player.STATE_IDLE)
                .build()
        }
        val playlist = items.mapIndexed { i, item ->
            MediaItemData.Builder("$i:${item.mediaId}")
                .setMediaItem(item)
                .setMediaMetadata(item.mediaMetadata)
                .setIsSeekable(true)
                .setDurationUs(if (i == index && durationMs != C.TIME_UNSET) durationMs * 1000 else C.TIME_UNSET)
                .build()
        }
        return builder
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(index)
            .setPlaybackState(playbackState)
            .setContentPositionMs { if (playbackState == Player.STATE_READY) engine.positionMs else 0L }
            .build()
    }

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        items = mediaItems.toList()
        index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, maxOf(0, items.lastIndex))
        if (shuffled) {
            unshuffled = items
            shuffleKeepingCurrent()
        }
        if (items.isEmpty()) {
            engine.unload()
            playbackState = Player.STATE_IDLE
        } else if (playbackState != Player.STATE_IDLE) {
            loadCurrent(startPositionMs)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        this.repeatMode = repeatMode
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        if (shuffleModeEnabled == shuffled) return Futures.immediateVoidFuture()
        shuffled = shuffleModeEnabled
        if (shuffled) {
            unshuffled = items
            shuffleKeepingCurrent()
        } else if (items.isNotEmpty()) {
            // Back to the order the queue had, staying on the module that plays now.
            val current = items[index]
            val added = items.filter { it !in unshuffled }
            items = unshuffled.filter { it in items } + added
            index = items.indexOf(current).coerceAtLeast(0)
        }
        return Futures.immediateVoidFuture()
    }

    /** Puts the playing module first and the rest in random order. */
    private fun shuffleKeepingCurrent() {
        if (items.isEmpty()) return
        val current = items[index]
        items = listOf(current) + (items.toMutableList().apply { removeAt(index) }).shuffled()
        index = 0
    }

    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        val at = index.coerceIn(0, items.size)
        items = items.subList(0, at) + mediaItems + items.subList(at, items.size)
        if (at <= this.index && items.size > mediaItems.size) this.index += mediaItems.size
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val removedCurrent = index in fromIndex until toIndex
        items = items.subList(0, fromIndex) + items.subList(toIndex, items.size)
        when {
            items.isEmpty() -> {
                index = 0
                engine.unload()
                playbackState = Player.STATE_ENDED
            }
            removedCurrent -> {
                index = fromIndex.coerceAtMost(items.lastIndex)
                if (playbackState != Player.STATE_IDLE) loadCurrent(0)
            }
            index >= toIndex -> index -= toIndex - fromIndex
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        error = null
        if (items.isNotEmpty()) loadCurrent(0)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        setPlaying(playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (items.isEmpty()) return Futures.immediateVoidFuture()
        val position = if (positionMs == C.TIME_UNSET) 0L else positionMs
        val target = if (mediaItemIndex == C.INDEX_UNSET) index else mediaItemIndex.coerceIn(0, items.lastIndex)
        if (target != index || playbackState == Player.STATE_ENDED) {
            index = target
            if (playbackState != Player.STATE_IDLE) loadCurrent(position)
        } else if (playbackState == Player.STATE_READY) {
            engine.seekTo(position)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        engine.pause()
        engine.unload()
        abandonFocus()
        durationMs = C.TIME_UNSET
        playbackState = Player.STATE_IDLE
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        context.unregisterReceiver(noisyReceiver)
        abandonFocus()
        engine.release()
        return Futures.immediateVoidFuture()
    }

    private fun loadCurrent(positionMs: Long) {
        val path = items[index].mediaId
        engine.pause()
        UadeFiles.ensureReady(context)
        if (engine.load(File(path))) {
            error = null
            durationMs = engine.durationMs.takeIf { it > 0 } ?: C.TIME_UNSET
            if (positionMs > 0) engine.seekTo(positionMs)
            playbackState = Player.STATE_READY
            if (playWhenReady) startEngine()
        } else {
            durationMs = C.TIME_UNSET
            // Skip files that cannot be decoded instead of stopping the whole queue.
            if (index < items.lastIndex) {
                index++
                loadCurrent(0)
            } else {
                error = PlaybackException("Cannot play ${File(path).name}", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
                playbackState = Player.STATE_IDLE
            }
        }
    }

    private fun setPlaying(play: Boolean) {
        playWhenReady = play
        pausedByFocusLoss = false
        if (play) {
            if (playbackState == Player.STATE_READY) startEngine()
        } else {
            engine.pause()
        }
    }

    private fun startEngine() {
        if (!hasFocus) {
            hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (!hasFocus) {
            playWhenReady = false
            return
        }
        engine.setVolume(1f)
        engine.play()
        onStarted(items[index].mediaId)
    }

    private fun abandonFocus() {
        if (hasFocus) audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private fun onTrackEnded() {
        if (playbackState != Player.STATE_READY) return
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            loadCurrent(0)
        } else if (index < items.lastIndex) {
            index++
            loadCurrent(0)
        } else if (repeatMode == Player.REPEAT_MODE_ALL && items.isNotEmpty()) {
            index = 0
            loadCurrent(0)
        } else {
            playbackState = Player.STATE_ENDED
            playWhenReady = false
            abandonFocus()
        }
        invalidateState()
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                engine.setVolume(1f)
                if (pausedByFocusLoss && playWhenReady && playbackState == Player.STATE_READY) engine.play()
                pausedByFocusLoss = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> engine.setVolume(0.25f)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (playWhenReady) {
                    pausedByFocusLoss = true
                    engine.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                setPlaying(false)
                invalidateState()
            }
        }
    }

    private companion object {
        val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_RELEASE,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_SET_SHUFFLE_MODE,
            )
            .build()
    }
}
