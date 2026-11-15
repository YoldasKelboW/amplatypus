// SPDX-License-Identifier: GPL-3.0-or-later
//
// The flow follows RadioInteractor.swift of 4champ,
// Copyright (c) Aleksi Sitomaniemi, used under the MIT license.
package io.github.yoldaskelbow.amplatypus.radio

import io.github.yoldaskelbow.amplatypus.data.Library
import io.github.yoldaskelbow.amplatypus.data.LibraryModule
import io.github.yoldaskelbow.amplatypus.playback.PlayerConnection
import io.github.yoldaskelbow.amplatypus.source.MusicSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Endless playback: a random pick from the whole archive, or from the user's favourites.
 *
 * To spare the servers the radio never fetches more than the one module that follows the playing one
 * (4champ buffers three). Archive modules are stored without being added to the library; they stay
 * available through the history and are deleted when they drop out of it, unless the user keeps them.
 */
class Radio(
    private val scope: CoroutineScope,
    private val library: Library,
    private val archive: MusicSource,
    private val player: PlayerConnection,
) {
    enum class Channel { ARCHIVE, FAVORITES }

    sealed interface State {
        data object Off : State
        data class Tuning(val channel: Channel) : State
        data class On(val channel: Channel) : State
        /** The favourites channel was started without any favourites. */
        data class Empty(val channel: Channel) : State
        data class Failed(val channel: Channel) : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state

    private var job: Job? = null
    private var lastId = -1L

    init {
        // Anything else that starts playing takes over from the radio.
        player.onUserPlay = {
            job?.cancel()
            _state.value = State.Off
        }
        scope.launch {
            // Whenever the player moves on, line up the module after it.
            player.nowPlaying.map { it.index to it.count }.distinctUntilChanged().collect { (index, count) ->
                val current = _state.value
                if (current is State.On && count > 0 && index >= count - 1) {
                    fetchNext(current.channel)?.let { player.append(it) }
                    library.removeExpiredRadioModules()
                }
            }
        }
    }

    fun start(channel: Channel) {
        job?.cancel()
        job = scope.launch {
            _state.value = State.Tuning(channel)
            if (channel == Channel.FAVORITES && library.favoriteCount() == 0) {
                _state.value = State.Empty(channel)
                return@launch
            }
            val first = fetchNext(channel)
            if (first == null) {
                _state.value = State.Failed(channel)
                return@launch
            }
            // "On" must be set before playback starts, so the next module is lined up for the first one too.
            _state.value = State.On(channel)
            player.play(listOf(first), 0, fromRadio = true)
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = State.Off
        player.stop()
    }

    /** Picks and, if needed, downloads the next module. Tries a few times before giving up. */
    private suspend fun fetchNext(channel: Channel): LibraryModule? {
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val module = when (channel) {
                    Channel.FAVORITES -> library.randomFavorite(exceptId = lastId)
                    Channel.ARCHIVE -> archive.random()?.let { library.obtain(it, keep = false) }
                }
                if (module != null) {
                    lastId = module.id
                    return module
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed download: wait a little longer each time, then try another module.
                delay(1000L shl attempt)
            }
        }
        return null
    }

    private companion object {
        const val MAX_ATTEMPTS = 4
    }
}
