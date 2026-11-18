// SPDX-License-Identifier: GPL-3.0-or-later
//
// The flow follows RadioInteractor.swift of 4champ,
// Copyright (c) Aleksi Sitomaniemi, used under the MIT license.
package io.github.yoldaskelbow.amplatypus.radio

import io.github.yoldaskelbow.amplatypus.data.Library
import io.github.yoldaskelbow.amplatypus.data.LibraryModule
import io.github.yoldaskelbow.amplatypus.playback.PlayerConnection
import io.github.yoldaskelbow.amplatypus.source.ModuleRef
import io.github.yoldaskelbow.amplatypus.source.MusicSource
import io.github.yoldaskelbow.amplatypus.source.SourceItem
import io.github.yoldaskelbow.amplatypus.source.amp.AmpSource
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
    /** Amiga Music Preservation, for its random and "sounds like" channels; `null` if switched off. */
    private val amp: AmpSource?,
    private val player: PlayerConnection,
) {
    /**
     * ARCHIVE: random from Modland. AMP: random from AMP. SIMILAR: each module sounds like the one
     * before it, by AMP's sonic analysis. FAVORITES: the user's hearts.
     */
    enum class Channel { ARCHIVE, AMP, SIMILAR, FAVORITES }

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

    /** The AMP module the next similar pick is based on. */
    private var seed: ModuleRef? = null

    /** AMP modules played on the similar channel lately, so it does not circle back to them. */
    private val recent = ArrayDeque<String>()

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

    /** Starts [channel]. For SIMILAR, [from] is the AMP module to start from; without it a random one is used. */
    fun start(channel: Channel, from: ModuleRef? = null) {
        job?.cancel()
        if (channel == Channel.SIMILAR) {
            seed = from?.takeIf { it.sourceId == AmpSource.ID }
            recent.clear()
            seed?.let { remember(it.key) }
        }
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
                    Channel.AMP -> amp?.random()?.let { library.obtain(it, keep = false) }
                    Channel.SIMILAR -> nextSimilar()?.let { library.obtain(it, keep = false) }?.also {
                        seed = ModuleRef(AmpSource.ID, it.sourceKey)
                        remember(it.sourceKey)
                    }
                }
                if (module != null) {
                    lastId = module.id
                    return module
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AmpSource.HttpError) {
                // AMP refuses this call (e.g. not cleared for this app yet): trying again will not help.
                if (e.code == 401 || e.code == 403) return null
                delay(1000L shl attempt)
            } catch (_: Exception) {
                // A failed download: wait a little longer each time, then try another module.
                delay(1000L shl attempt)
            }
        }
        return null
    }

    /** The best match for the seed that was not played lately; a random AMP module to begin with or at a dead end. */
    private suspend fun nextSimilar(): SourceItem.Module? {
        val amp = amp ?: return null
        val from = seed ?: return amp.random()
        return amp.similar(from).firstOrNull { it.ref.key != from.key && it.ref.key !in recent } ?: amp.random()
    }

    private fun remember(key: String) {
        recent.remove(key)
        recent.addLast(key)
        while (recent.size > RECENT_SIZE) recent.removeFirst()
    }

    private companion object {
        const val MAX_ATTEMPTS = 4
        const val RECENT_SIZE = 50
    }
}
