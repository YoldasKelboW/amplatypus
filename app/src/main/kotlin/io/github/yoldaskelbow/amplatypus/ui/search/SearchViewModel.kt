// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.yoldaskelbow.amplatypus.AmplatypusApplication
import io.github.yoldaskelbow.amplatypus.audio.FormatFilter
import io.github.yoldaskelbow.amplatypus.audio.FormatGroup
import io.github.yoldaskelbow.amplatypus.source.SearchKind
import io.github.yoldaskelbow.amplatypus.source.SourceItem
import io.github.yoldaskelbow.amplatypus.source.amp.AmpSource
import io.github.yoldaskelbow.amplatypus.source.modland.ModlandIndex
import io.github.yoldaskelbow.amplatypus.source.modland.ModlandSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    /** The archive being searched: [ModlandSource.ID] or [AmpSource.ID]. */
    val sourceId: String = ModlandSource.ID,
    val query: String = "",
    val kind: SearchKind = SearchKind.MODULE,
    /** Folders opened so far; empty means the top level. */
    val path: List<SourceItem.Folder> = emptyList(),
    val items: List<SourceItem> = emptyList(),
    val loading: Boolean = false,
    val endReached: Boolean = false,
    /** Download progress (0–1, or -1 if unknown) by module key. */
    val downloads: Map<String, Float> = emptyMap(),
    val error: String? = null,
    /** The last load failed, e.g. without a connection (AMP is searched online). */
    val offline: Boolean = false,
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as AmplatypusApplication).container
    private val source get() = container.sources[_state.value.sourceId] ?: container.modland

    /** Whether the AMP source exists in this build. */
    val ampAvailable: Boolean = container.amp != null

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    val indexState: StateFlow<ModlandIndex.State> = container.modlandIndex.state

    /** Library keys per source, as "source/key", to mark modules already on the device. */
    val libraryKeys: StateFlow<Set<String>> = combine(container.sources.keys.map { id ->
        container.library.keysOf(id).map { keys -> keys.map { "$id/$it" } }
    }) { lists -> lists.flatMap { it }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Which format groups are shown; changing it reloads what is on screen. */
    val formatFilter: StateFlow<FormatFilter> = container.settings.formatFilter

    private var loadJob: Job? = null
    private var page = 0

    init {
        viewModelScope.launch { container.modlandIndex.refreshIfNeeded() }
        viewModelScope.launch {
            // Show content as soon as the index is usable.
            indexState.collect { if (it is ModlandIndex.State.Ready) reload(debounce = false) }
        }
        viewModelScope.launch {
            formatFilter.drop(1).collect { reload(debounce = false) }
        }
    }

    fun setFormatShown(group: FormatGroup, shown: Boolean) = container.settings.setFormatShown(group, shown)

    fun retryIndex() = viewModelScope.launch { container.modlandIndex.refreshIfNeeded(force = true) }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, path = if (query.isBlank()) it.path else emptyList()) }
        reload(debounce = true)
    }

    /** Switches between Modland and AMP; the query is kept, the opened folders are not. */
    fun setSource(sourceId: String) {
        if (sourceId == _state.value.sourceId || sourceId !in container.sources) return
        _state.update { it.copy(sourceId = sourceId, path = emptyList(), items = emptyList()) }
        reload(debounce = false)
    }

    fun setKind(kind: SearchKind) {
        _state.update { it.copy(kind = kind) }
        reload(debounce = false)
    }

    fun open(folder: SourceItem.Folder) {
        _state.update { it.copy(query = "", path = it.path + folder) }
        reload(debounce = false)
    }

    /** Leaves the current folder. Returns false if there was nothing to go back from. */
    fun back(): Boolean {
        if (_state.value.path.isEmpty()) return false
        _state.update { it.copy(path = it.path.dropLast(1)) }
        reload(debounce = false)
        return true
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.endReached || current.items.isEmpty()) return
        load(page + 1, append = true, debounce = false)
    }

    fun play(module: SourceItem.Module) {
        val key = module.ref.sourceId + "/" + module.ref.key
        if (key in _state.value.downloads) return
        viewModelScope.launch {
            _state.update { it.copy(downloads = it.downloads + (key to -1f), error = null) }
            try {
                val stored = container.library.obtain(module, onProgress = { progress ->
                    _state.update { it.copy(downloads = it.downloads + (key to progress)) }
                })
                container.player.play(listOf(stored), 0)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            } finally {
                _state.update { it.copy(downloads = it.downloads - key) }
            }
        }
    }

    /** Plays a random module from the archive being searched. */
    fun playRandom() {
        viewModelScope.launch { source.random()?.let(::play) }
    }

    fun errorShown() = _state.update { it.copy(error = null) }

    private fun reload(debounce: Boolean) = load(0, append = false, debounce = debounce)

    private fun load(targetPage: Int, append: Boolean, debounce: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Same typing delay as 4champ before a search is triggered.
            if (debounce) delay(300)
            _state.update { it.copy(loading = true) }
            val snapshot = _state.value
            val source = this@SearchViewModel.source
            var failed = false
            val result = try {
                if (snapshot.query.isBlank()) {
                    source.browse(snapshot.path.lastOrNull(), targetPage)
                } else {
                    source.search(snapshot.query, snapshot.kind, targetPage)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failed = true
                emptyList()
            }
            page = targetPage
            // AMP leaves formats this build cannot play out of a page, so a short page is not the end there.
            val end = when {
                source is AmpSource && snapshot.query.isNotBlank() && snapshot.kind == SearchKind.MODULE -> source.lastPageWasLast
                source is AmpSource -> true
                else -> result.size < ModlandSource.PAGE_SIZE
            }
            _state.update {
                it.copy(
                    items = if (append) it.items + result else result,
                    loading = false,
                    endReached = end || failed,
                    offline = failed && !append,
                )
            }
        }
    }
}
