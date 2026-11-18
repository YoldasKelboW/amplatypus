// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yoldaskelbow.amplatypus.AmplatypusApplication
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.data.LibraryModule
import io.github.yoldaskelbow.amplatypus.radio.Radio
import io.github.yoldaskelbow.amplatypus.ui.FormatBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen() {
    val container = (LocalContext.current.applicationContext as AmplatypusApplication).container
    val radio = container.radio
    val state by radio.state.collectAsStateWithLifecycle()
    val history by container.library.history.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_radio)) })
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "archive") {
                ChannelCard(
                    title = stringResource(R.string.radio_archive),
                    text = stringResource(R.string.radio_archive_text),
                    channel = Radio.Channel.ARCHIVE,
                    state = state,
                    primary = true,
                    onStart = { radio.start(Radio.Channel.ARCHIVE) },
                    onStop = radio::stop,
                )
            }
            if (container.amp != null) {
                item(key = "amp") {
                    ChannelCard(
                        title = stringResource(R.string.radio_amp),
                        text = stringResource(R.string.radio_amp_text),
                        channel = Radio.Channel.AMP,
                        state = state,
                        primary = false,
                        onStart = { radio.start(Radio.Channel.AMP) },
                        onStop = radio::stop,
                    )
                }
                item(key = "similar") {
                    ChannelCard(
                        title = stringResource(R.string.radio_similar),
                        text = stringResource(R.string.radio_similar_text),
                        channel = Radio.Channel.SIMILAR,
                        state = state,
                        primary = false,
                        onStart = { radio.start(Radio.Channel.SIMILAR) },
                        onStop = radio::stop,
                    )
                }
            }
            item(key = "favorites") {
                ChannelCard(
                    title = stringResource(R.string.radio_favorites),
                    text = stringResource(R.string.radio_favorites_text),
                    channel = Radio.Channel.FAVORITES,
                    state = state,
                    primary = false,
                    onStart = { radio.start(Radio.Channel.FAVORITES) },
                    onStop = radio::stop,
                )
            }
            if (history.isNotEmpty()) {
                item(key = "history") {
                    Text(
                        stringResource(R.string.radio_history),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                items(history, key = { it.id }) { module ->
                    HistoryRow(
                        module = module,
                        onPlay = { container.player.play(listOf(module), 0) },
                        onKeep = { scope.launch { container.library.keep(module) } },
                        onFavorite = { scope.launch { container.library.setFavorite(module, !module.favorite) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    title: String,
    text: String,
    channel: Radio.Channel,
    state: Radio.State,
    primary: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val on = state == Radio.State.On(channel)
    val tuning = state == Radio.State.Tuning(channel)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (primary) scheme.primaryContainer else scheme.secondaryContainer,
            contentColor = if (primary) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            when (state) {
                Radio.State.Empty(channel) -> Text(stringResource(R.string.radio_no_favorites), style = MaterialTheme.typography.bodyMedium)
                Radio.State.Failed(channel) -> Text(stringResource(R.string.radio_failed), style = MaterialTheme.typography.bodyMedium)
                else -> {}
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                if (on || tuning) {
                    FilledTonalButton(onClick = onStop) {
                        Icon(painterResource(R.drawable.ic_stop), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.radio_stop), modifier = Modifier.padding(start = 8.dp))
                    }
                    if (tuning) CircularProgressIndicator(Modifier.padding(start = 16.dp).size(24.dp), strokeWidth = 3.dp)
                } else {
                    Button(onClick = onStart) {
                        Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.radio_start), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(module: LibraryModule, onPlay: () -> Unit, onKeep: () -> Unit, onFavorite: () -> Unit) {
    ListItem(
        headlineContent = { Text(module.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(module.composer.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { FormatBadge(module.format) },
        trailingContent = {
            Row {
                if (!module.kept) {
                    IconButton(onClick = onKeep) {
                        Icon(painterResource(R.drawable.ic_save), stringResource(R.string.keep))
                    }
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        painterResource(if (module.favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                        stringResource(if (module.favorite) R.string.favorite_remove else R.string.favorite_add),
                        tint = if (module.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onPlay),
    )
}
