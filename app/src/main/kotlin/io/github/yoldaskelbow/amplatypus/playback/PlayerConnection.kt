// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.yoldaskelbow.amplatypus.data.LibraryModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlaying(
    val title: String = "",
    val composer: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val error: String? = null,
    /** File path of the playing module, the key to its library entry. */
    val localPath: String = "",
    val index: Int = 0,
    val count: Int = 0,
    val shuffle: Boolean = false,
    /** One of Player.REPEAT_MODE_OFF, _ALL and _ONE. */
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
) {
    val isActive: Boolean get() = title.isNotEmpty()
}

/** The UI's link to [PlaybackService]. Connects on first use and mirrors the player state. */
class PlayerConnection(private val context: Context) {
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying

    val positionMs: Long get() = controller?.currentPosition ?: 0L

    /** Called when something other than the radio starts playback, so the radio can switch off. */
    var onUserPlay: (() -> Unit)? = null

    fun play(queue: List<LibraryModule>, startIndex: Int, fromRadio: Boolean = false) {
        if (!fromRadio) onUserPlay?.invoke()
        withController { c ->
            c.setMediaItems(queue.map { it.toMediaItem() }, startIndex, 0L)
            c.prepare()
            c.play()
        }
    }

    /** Adds [module] to the end of the queue. */
    fun append(module: LibraryModule) = withController { it.addMediaItem(module.toMediaItem()) }

    fun stop() = withController { c ->
        c.stop()
        c.clearMediaItems()
    }

    fun togglePlayPause() = withController { c -> if (c.isPlaying) c.pause() else c.play() }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    /** Off, then the whole queue, then the playing module, then off again. */
    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let { return action(it) }
        pending += action
        if (connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = try {
                future.get()
            } catch (_: Exception) {
                pending.clear()
                return@addListener
            }
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish(player)
            })
            publish(c)
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun publish(player: Player) {
        val metadata = player.mediaMetadata
        val duration = player.duration
        _nowPlaying.value = NowPlaying(
            title = if (player.mediaItemCount == 0) "" else metadata.title?.toString().orEmpty(),
            composer = metadata.artist?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            durationMs = if (duration > 0) duration else 0,
            hasPrevious = player.hasPreviousMediaItem(),
            hasNext = player.hasNextMediaItem(),
            error = player.playerError?.message,
            localPath = player.currentMediaItem?.mediaId.orEmpty(),
            index = player.currentMediaItemIndex,
            count = player.mediaItemCount,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }

    private fun LibraryModule.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(localPath)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(composer)
                .setAlbumTitle(format)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
}
