// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.library

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.yoldaskelbow.amplatypus.AmplatypusApplication
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.data.Library
import io.github.yoldaskelbow.amplatypus.data.LibraryModule
import io.github.yoldaskelbow.amplatypus.data.PlaylistSummary
import io.github.yoldaskelbow.amplatypus.source.modland.ModlandSource
import io.github.yoldaskelbow.amplatypus.ui.FormatBadge
import io.github.yoldaskelbow.amplatypus.ui.formatSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibrarySort { TITLE, COMPOSER, RECENT }

data class LibraryFilter(
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val sort: LibrarySort = LibrarySort.TITLE,
)

/** Applies search text, favourites filter and sort order; kept apart from the UI so it can be tested. */
fun List<LibraryModule>.filtered(filter: LibraryFilter): List<LibraryModule> {
    val words = filter.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val matching = filter { module ->
        (!filter.favoritesOnly || module.favorite) && words.all { word ->
            module.title.lowercase().contains(word) || module.composer.orEmpty().lowercase().contains(word)
        }
    }
    return when (filter.sort) {
        LibrarySort.TITLE -> matching.sortedBy { it.title.lowercase() }
        LibrarySort.COMPOSER -> matching.sortedWith(compareBy({ it.composer.orEmpty().lowercase() }, { it.title.lowercase() }))
        LibrarySort.RECENT -> matching.sortedByDescending { it.addedAt }
    }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as AmplatypusApplication).container
    private val library = container.library

    private val _filter = MutableStateFlow(LibraryFilter())
    val filter: StateFlow<LibraryFilter> = _filter

    /** Number of modules in the library before filtering, to tell "empty" from "nothing matches". */
    val total: StateFlow<Int> = library.modules
        .combine(_filter) { all, _ -> all.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val modules: StateFlow<List<LibraryModule>> = library.modules
        .combine(_filter) { all, filter -> all.filtered(filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<PlaylistSummary>> = library.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The playlist that is open, or null while the overview is shown. */
    private val _openPlaylist = MutableStateFlow<Long?>(null)
    val openPlaylist: StateFlow<Long?> = _openPlaylist

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistModules: StateFlow<List<LibraryModule>> = _openPlaylist
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else library.playlistModules(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A one-off note for the user, e.g. the result of an import. */
    private val _message = MutableStateFlow<Pair<Int, Int>?>(null)
    val message: StateFlow<Pair<Int, Int>?> = _message

    fun setQuery(query: String) = _filter.update { it.copy(query = query) }
    fun setFavoritesOnly(only: Boolean) = _filter.update { it.copy(favoritesOnly = only) }
    fun setSort(sort: LibrarySort) = _filter.update { it.copy(sort = sort) }

    /** Plays the shown list from [index] on, so the following modules play automatically. */
    fun play(index: Int) = container.player.play(modules.value, index)

    fun playPlaylist(index: Int) = container.player.play(playlistModules.value, index)

    fun toggleFavorite(module: LibraryModule) = viewModelScope.launch { library.setFavorite(module, !module.favorite) }

    fun delete(module: LibraryModule) = viewModelScope.launch { library.delete(module) }

    fun openPlaylist(id: Long?) {
        _openPlaylist.value = id
    }

    fun createPlaylist(name: String, thenAdd: LibraryModule? = null) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = library.createPlaylist(name)
        if (thenAdd != null) library.addToPlaylist(id, thenAdd)
    }

    fun addToPlaylist(playlistId: Long, module: LibraryModule) = viewModelScope.launch { library.addToPlaylist(playlistId, module) }

    fun removeFromPlaylist(module: LibraryModule) = viewModelScope.launch {
        _openPlaylist.value?.let { library.removeFromPlaylist(it, module) }
    }

    fun deleteOpenPlaylist() = viewModelScope.launch {
        _openPlaylist.value?.let { library.deletePlaylist(it) }
        _openPlaylist.value = null
    }

    /** Imports the picked files; reports how many were added and how many were not playable. */
    fun import(uris: List<Uri>) = viewModelScope.launch {
        val resolver = getApplication<Application>().contentResolver
        var imported = 0
        var skipped = 0
        for (uri in uris) {
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: continue
            val added = try {
                library.importFile(name) { resolver.openInputStream(uri) }
            } catch (_: Exception) {
                null
            }
            if (added != null) imported++ else skipped++
        }
        _message.value = imported to skipped
    }

    fun messageShown() {
        _message.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel = viewModel()) {
    val openPlaylist by viewModel.openPlaylist.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<LibraryModule?>(null) }
    var toAdd by remember { mutableStateOf<LibraryModule?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.import(uris)
    }

    val importText = message?.let { (imported, skipped) ->
        if (skipped == 0) {
            pluralStringResource(R.plurals.import_done, imported, imported)
        } else {
            stringResource(R.string.import_done_skipped, imported, skipped)
        }
    }
    LaunchedEffect(importText) {
        if (importText != null) {
            snackbar.showSnackbar(importText)
            viewModel.messageShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        val open = playlists.firstOrNull { it.id == openPlaylist }
        if (open != null) {
            PlaylistDetail(open, viewModel, onDelete = { toDelete = it }, onAdd = { toAdd = it })
        } else {
            Column(Modifier.fillMaxSize()) {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.nav_library)) },
                    actions = {
                        if (tab == 0) {
                            IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.import_files))
                            }
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(painterResource(R.drawable.ic_sort), stringResource(R.string.sort))
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                listOf(
                                    LibrarySort.TITLE to R.string.sort_title,
                                    LibrarySort.COMPOSER to R.string.sort_composer,
                                    LibrarySort.RECENT to R.string.sort_recent,
                                ).forEach { (sort, label) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        leadingIcon = {
                                            if (filter.sort == sort) Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                                        },
                                        onClick = {
                                            viewModel.setSort(sort)
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = { newPlaylist = true }) {
                                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.playlist_new))
                            }
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.search_modules)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.playlists)) })
                }
                if (tab == 0) {
                    ModulesTab(viewModel, filter, onDelete = { toDelete = it }, onAdd = { toAdd = it })
                } else {
                    PlaylistsTab(playlists, onOpen = viewModel::openPlaylist, onNew = { newPlaylist = true })
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    toDelete?.let { module ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_text, module.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(module)
                    toDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    toAdd?.let { module ->
        AlertDialog(
            onDismissRequest = { toAdd = null },
            title = { Text(stringResource(R.string.playlist_add)) },
            text = {
                Column {
                    playlists.forEach { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(painterResource(R.drawable.ic_queue_music), contentDescription = null) },
                            // Inside a dialog the row must not paint its own background.
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                viewModel.addToPlaylist(playlist.id, module)
                                toAdd = null
                            },
                        )
                    }
                    NameField(stringResource(R.string.playlist_new)) { name ->
                        viewModel.createPlaylist(name, thenAdd = module)
                        toAdd = null
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { toAdd = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (newPlaylist) {
        AlertDialog(
            onDismissRequest = { newPlaylist = false },
            title = { Text(stringResource(R.string.playlist_new)) },
            text = {
                NameField(stringResource(R.string.playlist_name)) { name ->
                    viewModel.createPlaylist(name)
                    newPlaylist = false
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { newPlaylist = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ModulesTab(
    viewModel: LibraryViewModel,
    filter: LibraryFilter,
    onDelete: (LibraryModule) -> Unit,
    onAdd: (LibraryModule) -> Unit,
) {
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    if (total == 0) {
        Message(stringResource(R.string.library_empty))
        return
    }
    OutlinedTextField(
        value = filter.query,
        onValueChange = viewModel::setQuery,
        placeholder = { Text(stringResource(R.string.library_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
    )
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !filter.favoritesOnly,
            onClick = { viewModel.setFavoritesOnly(false) },
            label = { Text(stringResource(R.string.library_all)) },
        )
        FilterChip(
            selected = filter.favoritesOnly,
            onClick = { viewModel.setFavoritesOnly(true) },
            label = { Text(stringResource(R.string.library_favorites)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_favorite), contentDescription = null) },
        )
    }
    if (modules.isEmpty()) {
        Message(stringResource(if (filter.favoritesOnly && filter.query.isBlank()) R.string.library_no_favorites else R.string.search_no_results))
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(modules, key = { _, module -> module.id }) { index, module ->
                ModuleRow(
                    module = module,
                    onClick = { viewModel.play(index) },
                    onFavorite = { viewModel.toggleFavorite(module) },
                    onAdd = { onAdd(module) },
                    onDelete = { onDelete(module) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistsTab(playlists: List<PlaylistSummary>, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    if (playlists.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.playlists_empty),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onNew) { Text(stringResource(R.string.playlist_new)) }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { _, playlist ->
            ListItem(
                headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(pluralStringResource(R.plurals.module_count, playlist.count, playlist.count)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_queue_music), contentDescription = null) },
                modifier = Modifier.clickable { onOpen(playlist.id) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetail(
    playlist: PlaylistSummary,
    viewModel: LibraryViewModel,
    onDelete: (LibraryModule) -> Unit,
    onAdd: (LibraryModule) -> Unit,
) {
    val modules by viewModel.playlistModules.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler { viewModel.openPlaylist(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = { viewModel.openPlaylist(null) }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                }
            },
            actions = {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.playlist_delete))
                }
            },
        )
        if (modules.isEmpty()) {
            Message(stringResource(R.string.playlist_empty))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(modules, key = { _, module -> module.id }) { index, module ->
                    ModuleRow(
                        module = module,
                        onClick = { viewModel.playPlaylist(index) },
                        onFavorite = { viewModel.toggleFavorite(module) },
                        onAdd = { onAdd(module) },
                        onDelete = { onDelete(module) },
                        onRemoveFromPlaylist = { viewModel.removeFromPlaylist(module) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.playlist_delete)) },
            text = { Text(stringResource(R.string.playlist_delete_text, playlist.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteOpenPlaylist()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A module with heart and a menu: add to playlist, share, remove, delete. */
@Composable
private fun ModuleRow(
    module: LibraryModule,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(module.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(module.composer, formatSize(module.sizeBytes)).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { FormatBadge(module.format) },
        trailingContent = {
            Row {
                IconButton(onClick = onFavorite) {
                    Icon(
                        painterResource(if (module.favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                        stringResource(if (module.favorite) R.string.favorite_remove else R.string.favorite_add),
                        tint = if (module.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_add)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_playlist_add), contentDescription = null) },
                            onClick = {
                                menu = false
                                onAdd()
                            },
                        )
                        // Only archive modules have a public address that can be shared.
                        if (module.sourceId == ModlandSource.ID) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_share), contentDescription = null) },
                                onClick = {
                                    menu = false
                                    val text = listOfNotNull(module.title, module.composer).joinToString(" – ") +
                                        "\n" + ModlandSource.shareUrl(module.sourceKey)
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                    context.startActivity(Intent.createChooser(send, null))
                                },
                            )
                        }
                        if (onRemoveFromPlaylist != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_remove)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onRemoveFromPlaylist()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A text field with a confirm button, used for naming playlists. */
@Composable
private fun NameField(label: String, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(label) },
            singleLine = true,
            // The keyboard's confirm key does the same as the button next to the field.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
            Icon(painterResource(R.drawable.ic_check), stringResource(R.string.create))
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
