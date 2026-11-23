// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.playback

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.yoldaskelbow.amplatypus.AmplatypusApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps playback alive in the background and publishes it as a media session. */
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val library = (application as AmplatypusApplication).container.library
        val player = ModulePlayer(
            this,
            mainLooper,
            onStarted = { path -> scope.launch { library.markPlayed(path) } },
            onSleepTimerChanged = { timer -> session?.setSessionExtras(sleepExtras(timer)) },
        )
        val sleepCommand = SessionCommand(SleepTimer.COMMAND, Bundle.EMPTY)
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // Our own controllers may set the sleep timer; everything else keeps the defaults.
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(sleepCommand).build(),
                    )
                    .build()

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction != SleepTimer.COMMAND) {
                        return super.onCustomCommand(session, controller, customCommand, args)
                    }
                    player.setSleepTimer(args.getInt(SleepTimer.ARG_MINUTES, SleepTimer.OFF))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                // Items arrive without a URI; the media id (the file path) is all the player needs.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems)
            })
            .build()
    }

    /** The timer as the UI reads it: an end time on the wall clock, or "at the end of this module". */
    private fun sleepExtras(timer: SleepTimer) = Bundle().apply {
        timer.remainingMs()?.let { putLong(SleepTimer.EXTRA_ENDS_AT_WALL, System.currentTimeMillis() + it) }
        putBoolean(SleepTimer.EXTRA_END_OF_MODULE, timer.atEndOfModule)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }
}
