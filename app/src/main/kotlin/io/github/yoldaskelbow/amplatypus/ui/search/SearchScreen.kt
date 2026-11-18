// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.yoldaskelbow.amplatypus.audio.FormatGroup
import io.github.yoldaskelbow.amplatypus.audio.FormatFilter
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.source.FolderKind
import io.github.yoldaskelbow.amplatypus.source.SearchKind
import io.github.yoldaskelbow.amplatypus.source.SourceItem
import io.github.yoldaskelbow.amplatypus.net.Endpoints
import io.github.yoldaskelbow.amplatypus.source.amp.AmpSource
import io.github.yoldaskelbow.amplatypus.source.modland.ModlandIndex
import io.github.yoldaskelbow.amplatypus.source.modland.ModlandSource
import io.github.yoldaskelbow.amplatypus.ui.FormatBadge
import io.github.yoldaskelbow.amplatypus.ui.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val libraryKeys by viewModel.libraryKeys.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val folder = state.path.lastOrNull()

    BackHandler(enabled = state.path.isNotEmpty() || state.query.isNotEmpty()) {
        if (!viewModel.back()) viewModel.setQuery("")
    }

    val errorText = state.error?.let { stringResource(R.string.download_failed, it) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbar.showSnackbar(errorText)
            viewModel.errorShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (folder == null) {
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    amp = state.sourceId == AmpSource.ID,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                TopAppBar(
                    title = { Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.back() }) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                        }
                    },
                )
            }

            if (folder == null) {
                val filter by viewModel.formatFilter.collectAsStateWithLifecycle()
                var filtering by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (viewModel.ampAvailable) SourceSwitch(state.sourceId, viewModel::setSource)
                    }
                    IconButton(onClick = { filtering = true }) {
                        Icon(
                            painterResource(R.drawable.ic_filter),
                            stringResource(if (filter.showsAll) R.string.filter_formats else R.string.filter_formats_active),
                            tint = if (filter.showsAll) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (filtering) FormatFilterDialog(filter, viewModel::setFormatShown) { filtering = false }
            }

            if (state.query.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.kind == SearchKind.MODULE,
                        onClick = { viewModel.setKind(SearchKind.MODULE) },
                        label = { Text(stringResource(R.string.search_modules)) },
                    )
                    FilterChip(
                        selected = state.kind == SearchKind.COMPOSER,
                        onClick = { viewModel.setKind(SearchKind.COMPOSER) },
                        label = { Text(stringResource(R.string.search_composers)) },
                    )
                }
            }

            // AMP is searched online and needs no index.
            if (state.sourceId == AmpSource.ID) Results(state, libraryKeys, viewModel) else when (val index = indexState) {
                is ModlandIndex.State.Ready -> Results(state, libraryKeys, viewModel)
                is ModlandIndex.State.Failed -> IndexMessage(stringResource(R.string.index_failed, index.message)) {
                    Button(onClick = { viewModel.retryIndex() }) { Text(stringResource(R.string.retry)) }
                }
                is ModlandIndex.State.Importing -> IndexMessage(stringResource(R.string.index_importing, index.entries)) {
                    CircularProgressIndicator()
                }
                else -> IndexMessage(stringResource(R.string.index_downloading)) { CircularProgressIndicator() }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Results(state: SearchUiState, libraryKeys: Set<String>, viewModel: SearchViewModel) {
    val amp = state.sourceId == AmpSource.ID
    if (state.items.isEmpty() && !state.loading) {
        when {
            state.offline -> IndexMessage(stringResource(R.string.amp_offline)) {
                Button(onClick = { viewModel.setQuery(state.query) }) { Text(stringResource(R.string.retry)) }
            }
            state.query.isNotBlank() -> IndexMessage(stringResource(R.string.search_no_results)) {}
            amp && state.path.isEmpty() -> AmpHero(viewModel)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (amp && state.query.isBlank() && state.path.isEmpty()) {
            item(key = "hero") { AmpHero(viewModel) }
            item(key = "header") {
                Text(
                    text = stringResource(R.string.amp_latest),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        } else if (state.query.isBlank() && state.path.isEmpty()) {
            item(key = "hero") {
                val total = (viewModel.indexState.collectAsStateWithLifecycle().value as? ModlandIndex.State.Ready)?.entries ?: 0
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.hero_title, "%,d".format(total)),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(stringResource(R.string.hero_text), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { viewModel.playRandom() }, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.hero_random), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            item(key = "header") {
                Text(
                    text = stringResource(R.string.browse_formats),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        itemsIndexed(state.items) { position, item ->
            if (position >= state.items.size - 10) LaunchedEffect(state.items.size) { viewModel.loadMore() }
            when (item) {
                is SourceItem.Folder -> FolderRow(item) { viewModel.open(item) }
                is SourceItem.Module -> ModuleRow(
                    module = item,
                    inLibrary = "${item.ref.sourceId}/${item.ref.key}" in libraryKeys,
                    progress = state.downloads["${item.ref.sourceId}/${item.ref.key}"],
                    onClick = { viewModel.play(item) },
                )
            }
        }
        if (state.loading) {
            item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        if (amp && !state.loading) {
            item(key = "credit") { AmpCredit() }
        }
    }
}

/** Show or hide whole groups of formats: in search, browsing, random picks and the radio. */
@Composable
private fun FormatFilterDialog(filter: FormatFilter, onChange: (FormatGroup, Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.filter_formats)) },
        text = {
            Column {
                Text(stringResource(R.string.filter_text), style = MaterialTheme.typography.bodyMedium)
                listOf(
                    Triple(FormatGroup.TRACKER, R.string.filter_tracker, R.string.filter_tracker_text),
                    Triple(FormatGroup.HIVELY, R.string.filter_hively, R.string.filter_hively_text),
                    Triple(FormatGroup.AMIGA, R.string.filter_amiga, R.string.filter_amiga_text),
                    Triple(FormatGroup.C64, R.string.filter_c64, R.string.filter_c64_text),
                ).forEach { (group, title, text) ->
                    val shown = filter.shows(group)
                    // The last shown group cannot be switched off.
                    val enabled = !shown || filter.with(group, false) != filter
                    ListItem(
                        headlineContent = { Text(stringResource(title)) },
                        supportingContent = { Text(stringResource(text)) },
                        leadingContent = { Checkbox(checked = shown, onCheckedChange = null, enabled = enabled) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(value = shown, enabled = enabled, role = Role.Checkbox) { onChange(group, it) },
                    )
                }
            }
        },
    )
}

/** Modland or AMP, side by side under the search field. */
@Composable
private fun SourceSwitch(selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(ModlandSource.ID to R.string.source_modland, AmpSource.ID to R.string.source_amp).forEachIndexed { index, (id, label) ->
            SegmentedButton(
                selected = selected == id,
                onClick = { onSelect(id) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun AmpHero(viewModel: SearchViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.amp_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.amp_text), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { viewModel.playRandom() }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.hero_random), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** AMP's data and files are used with the staff's permission; the app says where they come from. */
@Composable
private fun AmpCredit() {
    val uri = LocalUriHandler.current
    Text(
        stringResource(R.string.amp_credit),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.amp_open_site)) { uri.openUri(Endpoints.AMP_SITE) }
            .padding(16.dp),
    )
}

@Composable
private fun FolderRow(folder: SourceItem.Folder, onClick: () -> Unit) {
    val count = folder.subtitle?.toIntOrNull()
    ListItem(
        headlineContent = { Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (count != null || folder.detail != null) {
            {
                Text(
                    listOfNotNull(count?.let { pluralStringResource(R.plurals.module_count, it, it) }, folder.detail).joinToString(" · "),
                )
            }
        } else {
            null
        },
        leadingContent = {
            Icon(
                painterResource(if (folder.kind == FolderKind.COMPOSER) R.drawable.ic_person else R.drawable.ic_folder),
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ModuleRow(module: SourceItem.Module, inLibrary: Boolean, progress: Float?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(module.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(module.composer, module.format, module.sizeBytes?.let(::formatSize)).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { FormatBadge(module.format) },
        trailingContent = {
            when {
                progress != null && progress >= 0f -> CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp))
                progress != null -> CircularProgressIndicator(Modifier.size(24.dp))
                inLibrary -> Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.in_library),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun IndexMessage(text: String, extra: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        extra()
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, amp: Boolean, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    Surface(
        shape = SearchBarDefaults.inputFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(if (amp) R.string.search_hint_amp else R.string.search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
