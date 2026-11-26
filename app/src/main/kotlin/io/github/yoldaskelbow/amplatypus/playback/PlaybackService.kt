// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.playback

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.yoldaskelbow.amplatypus.AmplatypusApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps playback alive in the background and publishes it as a media session. As a media library it also
 * lets Android Auto and other media browsers browse the modules on the device (see [LibraryBrowser]).
 */
class PlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: MediaLibrarySession? = null

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
        session = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
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

                // Items arrive without a URI; the media id (the file path) is all the player needs. Items a
                // media browser picked carry their list in the id, which is taken off.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = future {
                    mediaItems.flatMap { item ->
                        // A voice command ("play ... on Amplatypus") arrives as an item with a search query.
                        item.requestMetadata.searchQuery?.let { browser.searchQueue(it) } ?: listOf(browser.playable(item))
                    }.toMutableList()
                }

                // A module tapped in a browser's list plays the whole list from there.
                override fun onSetMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
                    val single = mediaItems.singleOrNull()
                    val query = single?.requestMetadata?.searchQuery
                    val queue = if (query != null) browser.searchQueue(query) to 0 else single?.let { browser.queueFor(it.mediaId) }
                    if (queue != null) {
                        MediaSession.MediaItemsWithStartPosition(queue.first, queue.second, C.TIME_UNSET)
                    } else {
                        MediaSession.MediaItemsWithStartPosition(
                            mediaItems.map { browser.playable(it) },
                            startIndex,
                            startPositionMs,
                        )
                    }
                }

                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<MediaItem>> =
                    Futures.immediateFuture(LibraryResult.ofItem(this@PlaybackService.browser.root(), params))

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
                    val all = this@PlaybackService.browser.children(parentId)
                    val from = (page.toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
                    val to = (from.toLong() + pageSize).coerceAtMost(all.size.toLong()).toInt()
                    LibraryResult.ofItemList(all.subList(from, to), params)
                }

                // The browser's search field (Android Auto has one): results come as a list to browse.
                override fun onSearch(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    query: String,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<Void>> = future {
                    val count = this@PlaybackService.browser.search(query).size
                    session.notifySearchResultChanged(browser, query, count, params)
                    LibraryResult.ofVoid()
                }

                override fun onGetSearchResult(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    query: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
                    onGetChildren(session, browser, LibraryBrowser.SEARCH + query, page, pageSize, params)

                override fun onGetItem(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    mediaId: String,
                ): ListenableFuture<LibraryResult<MediaItem>> = future {
                    this@PlaybackService.browser.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                        ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                }
            })
            .build()
    }

    // Inside the callback, Media3's parameters named "browser" shadow this; it is reached as this@PlaybackService.browser there.
    private val browser: LibraryBrowser by lazy {
        LibraryBrowser(this, (application as AmplatypusApplication).container.library)
    }

    /** Runs [block] on the service's scope and hands its result to Media3 as a future. */
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try {
                result.set(block())
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result
    }

    /** The timer as the UI reads it: an end time on the wall clock, or "at the end of this module". */
    private fun sleepExtras(timer: SleepTimer) = Bundle().apply {
        timer.remainingMs()?.let { putLong(SleepTimer.EXTRA_ENDS_AT_WALL, System.currentTimeMillis() + it) }
        putBoolean(SleepTimer.EXTRA_END_OF_MODULE, timer.atEndOfModule)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

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
