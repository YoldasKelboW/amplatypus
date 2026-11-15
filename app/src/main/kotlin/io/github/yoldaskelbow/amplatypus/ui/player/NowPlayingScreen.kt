// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yoldaskelbow.amplatypus.AmplatypusApplication
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.audio.ModuleEngine
import io.github.yoldaskelbow.amplatypus.playback.PlayerConnection
import io.github.yoldaskelbow.amplatypus.ui.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The full-screen player: channel meters, the running pattern, the texts inside the module, controls. */
@Composable
fun NowPlayingScreen(player: PlayerConnection, onClose: () -> Unit) {
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    val visual = rememberVisual()
    BackHandler(onBack = onClose)

    // The library entry of the playing module, for the favourite button.
    val library = (LocalContext.current.applicationContext as AmplatypusApplication).container.library
    val scope = rememberCoroutineScope()
    val entry by remember(nowPlaying.localPath) { library.byLocalPath(nowPlaying.localPath) }
        .collectAsStateWithLifecycle(null)

    // Re-read the module facts whenever another module starts.
    var info by remember { mutableStateOf<ModuleEngine.ModuleInfo?>(null) }
    LaunchedEffect(nowPlaying.title, nowPlaying.durationMs) {
        repeat(10) {
            info = ModuleEngine.current?.moduleInfo()
            delay(200)
        }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(nowPlaying.isPlaying, nowPlaying.title) {
        positionMs = player.positionMs
        while (nowPlaying.isPlaying) {
            delay(250)
            positionMs = player.positionMs
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // The oscilloscopes cost CPU and memory, so they only run while this screen shows them.
    DisposableEffect(info?.generation, tab) {
        ModuleEngine.current?.setScopesEnabled(tab == 0)
        onDispose { ModuleEngine.current?.setScopesEnabled(false) }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_expand_more), stringResource(R.string.close_player))
                }
                Text(
                    stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                entry?.let { module ->
                    IconButton(onClick = { scope.launch { library.setFavorite(module, !module.favorite) } }) {
                        Icon(
                            painterResource(if (module.favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                            stringResource(if (module.favorite) R.string.favorite_remove else R.string.favorite_add),
                            tint = if (module.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                nowPlaying.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                nowPlaying.composer,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            info?.let { module ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (module.type.isNotBlank()) AssistChip(onClick = {}, label = { Text(module.type, maxLines = 1) })
                    AssistChip(
                        onClick = {},
                        label = { Text(pluralStringResource(R.plurals.channel_count, module.channels, module.channels)) },
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                listOf(R.string.tab_channels, R.string.tab_text).forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(stringResource(label)) }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
            ) {
                val module = info
                if (tab == 0) {
                    val channels = module?.channels ?: 4
                    val scopeRows = (channels + scopeColumns(channels) - 1) / scopeColumns(channels)
                    Column(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
                        // Muted channels of the playing module; forgotten when the next module starts.
                        var muted by remember(module?.generation) {
                            mutableStateOf(ModuleEngine.current?.mutedChannels() ?: BooleanArray(0))
                        }
                        val haptics = LocalHapticFeedback.current
                        ChannelScopes(
                            visual = visual,
                            channels = channels,
                            muted = muted,
                            onTap = { channel -> ModuleEngine.current?.toggleMute(channel)?.let { muted = it } },
                            onLongPress = { channel ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                ModuleEngine.current?.toggleSolo(channel)?.let { muted = it }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (scopeRows == 1) 64.dp else (36 * scopeRows.coerceAtMost(4)).dp)
                                .padding(horizontal = 12.dp),
                        )
                        Text(
                            stringResource(R.string.scope_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 14.dp, top = 4.dp),
                        )
                        ChannelMeters(
                            visual = visual,
                            channels = channels,
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                            segments = 12,
                        )
                        PatternView(
                            visual = visual,
                            generation = module?.generation ?: 0,
                            channels = channels,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    ModuleText(module)
                }
            }

            var dragging by remember { mutableStateOf<Float?>(null) }
            val duration = nowPlaying.durationMs
            Slider(
                value = dragging ?: if (duration <= 0) 0f else (positionMs.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let {
                        val target = (it * duration).toLong()
                        player.seekTo(target)
                        positionMs = target
                    }
                    dragging = null
                },
                enabled = duration > 0,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(positionMs), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            ) {
                FilledTonalIconButton(onClick = player::previous, modifier = Modifier.size(52.dp)) {
                    Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.previous))
                }
                FilledIconButton(onClick = player::togglePlayPause, modifier = Modifier.size(76.dp)) {
                    Icon(
                        painterResource(if (nowPlaying.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        stringResource(if (nowPlaying.isPlaying) R.string.pause else R.string.play),
                        Modifier.size(36.dp),
                    )
                }
                FilledTonalIconButton(onClick = player::next, enabled = nowPlaying.hasNext, modifier = Modifier.size(52.dp)) {
                    Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.next))
                }
            }
        }
    }
}

/** Song message plus instrument and sample names – where tracker musicians leave their greetings. */
@Composable
private fun ModuleText(module: ModuleEngine.ModuleInfo?) {
    val sections = buildList {
        if (module == null) return@buildList
        if (module.message.isNotBlank()) add(R.string.text_message to module.message.lines())
        if (module.instruments.isNotEmpty()) add(R.string.text_instruments to module.instruments.numbered())
        if (module.samples.isNotEmpty()) add(R.string.text_samples to module.samples.numbered())
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (sections.isEmpty()) {
            Text(stringResource(R.string.text_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        sections.forEach { (title, lines) ->
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                lines.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

private fun List<String>.numbered(): List<String> = mapIndexed { index, name -> "%02d  %s".format(index + 1, name) }
