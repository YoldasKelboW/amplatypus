// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.yoldaskelbow.amplatypus.AmplatypusApplication
import io.github.yoldaskelbow.amplatypus.source.SearchKind
import io.github.yoldaskelbow.amplatypus.source.SourceItem
import io.github.yoldaskelbow.amplatypus.source.modland.ModlandIndex
import io.github.yoldaskelbow.amplatypus.source.modland.ModlandSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
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
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as AmplatypusApplication).container
    private val source = container.modland

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    val indexState: StateFlow<ModlandIndex.State> = container.modlandIndex.state

    val libraryKeys: StateFlow<Set<String>> = container.library.keysOf(ModlandSource.ID)
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var loadJob: Job? = null
    private var page = 0

    init {
        viewModelScope.launch { container.modlandIndex.refreshIfNeeded() }
        viewModelScope.launch {
            // Show content as soon as the index is usable.
            indexState.collect { if (it is ModlandIndex.State.Ready) reload(debounce = false) }
        }
    }

    fun retryIndex() = viewModelScope.launch { container.modlandIndex.refreshIfNeeded(force = true) }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, path = if (query.isBlank()) it.path else emptyList()) }
        reload(debounce = true)
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
        val key = module.ref.key
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

    /** Plays a random module from the index. */
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
            val result = try {
                if (snapshot.query.isBlank()) {
                    source.browse(snapshot.path.lastOrNull(), targetPage)
                } else {
                    source.search(snapshot.query, snapshot.kind, targetPage)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyList()
            }
            page = targetPage
            _state.update {
                it.copy(
                    items = if (append) it.items + result else result,
                    loading = false,
                    endReached = result.size < ModlandSource.PAGE_SIZE,
                )
            }
        }
    }
}
