// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import io.github.yoldaskelbow.amplatypus.AmplatypusApplication
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.audio.ModuleEngine
import io.github.yoldaskelbow.amplatypus.playback.PlayerConnection
import io.github.yoldaskelbow.amplatypus.radio.Radio
import io.github.yoldaskelbow.amplatypus.source.ModuleRef
import io.github.yoldaskelbow.amplatypus.source.amp.AmpSource
import io.github.yoldaskelbow.amplatypus.ui.HideKeyboardOnEnter
import io.github.yoldaskelbow.amplatypus.ui.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The full-screen player: channel meters, the running pattern, the texts inside the module, controls. */
@Composable
fun NowPlayingScreen(player: PlayerConnection, onClose: () -> Unit) {
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    val visual = rememberVisual()
    BackHandler(onBack = onClose)
    // The player covers the search field: a keyboard left open from a search would cover the channels.
    HideKeyboardOnEnter()

    // The library entry of the playing module, for the favourite button.
    val container = (LocalContext.current.applicationContext as AmplatypusApplication).container
    val library = container.library
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

    // Songs inside the file (current from 0, count): C64 tunes and Amiga game music often hold several.
    var subsongs by remember { mutableStateOf(0 to 1) }
    LaunchedEffect(info?.generation) {
        while (true) {
            subsongs = ModuleEngine.current?.subsongs() ?: (0 to 1)
            delay(500)
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
    // The tracker-style view, opened by tapping the format chip.
    var showRetro by rememberSaveable { mutableStateOf(false) }
    val retro = showRetro && hasRetroView(info)

    // The oscilloscopes cost CPU and memory, so they only run while this screen shows them.
    DisposableEffect(info?.generation, tab, retro) {
        ModuleEngine.current?.setScopesEnabled(tab == 0 || retro)
        onDispose { ModuleEngine.current?.setScopesEnabled(false) }
    }

    Box(Modifier.fillMaxSize()) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            // Landscape and tablets: details and buttons on the left, channels or text on the right.
            val wide = maxWidth >= 600.dp && maxWidth > maxHeight
            val header: @Composable () -> Unit = {
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
                    SleepTimerButton(nowPlaying, onSet = player::setSleepTimer)
                    // AMP modules can start a radio of modules that sound like them.
                    entry?.takeIf { it.sourceId == AmpSource.ID && container.amp != null }?.let { module ->
                        IconButton(onClick = { container.radio.start(Radio.Channel.SIMILAR, ModuleRef(module.sourceId, module.sourceKey)) }) {
                            Icon(painterResource(R.drawable.ic_radio), stringResource(R.string.radio_similar_from_here))
                        }
                    }
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

                // What the "sounds alike" radio button started: searching, or why nothing happened.
                val radioState by container.radio.state.collectAsStateWithLifecycle()
                when (radioState) {
                    Radio.State.Tuning(Radio.Channel.SIMILAR) ->
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                    Radio.State.Failed(Radio.Channel.SIMILAR) -> Text(
                        stringResource(R.string.radio_similar_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    else -> {}
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
                        if (module.type.isNotBlank()) {
                            val hasRetro = hasRetroView(module)
                            AssistChip(
                                onClick = { if (hasRetro) showRetro = true },
                                label = { Text(module.type, maxLines = 1) },
                                // A small hint that this chip opens something.
                                trailingIcon = if (hasRetro) {
                                    { Icon(painterResource(R.drawable.ic_retro), null, Modifier.size(AssistChipDefaults.IconSize)) }
                                } else {
                                    null
                                },
                            )
                        }
                        if (module.channels > 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text(pluralStringResource(R.plurals.channel_count, module.channels, module.channels)) },
                            )
                        }
                    }
                }
                val (subsong, subsongCount) = subsongs
                if (subsongCount > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (ModuleEngine.current?.selectSubsong(subsong - 1) == true) subsongs = subsong - 1 to subsongCount },
                            enabled = subsong > 0,
                        ) {
                            Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.subsong_previous))
                        }
                        Text(stringResource(R.string.subsong_of, subsong + 1, subsongCount), style = MaterialTheme.typography.labelLarge)
                        IconButton(
                            onClick = { if (ModuleEngine.current?.selectSubsong(subsong + 1) == true) subsongs = subsong + 1 to subsongCount },
                            enabled = subsong + 1 < subsongCount,
                        ) {
                            Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.subsong_next))
                        }
                    }
                }
            }
            val views: @Composable ColumnScope.() -> Unit = {
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
                    if (tab == 0 && module?.channels == 0) {
                        // UADE formats, SPC and FM music give no per-channel data.
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.no_channel_view),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else if (tab == 0) {
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
                                stringResource(if (module?.canMute == false) R.string.scope_hint_no_mute else R.string.scope_hint),
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
                            // Replay routines in an emulated Amiga (UADE) have no pattern to show.
                            if (module?.hasPattern != false) {
                                PatternView(
                                    visual = visual,
                                    generation = module?.generation ?: 0,
                                    channels = channels,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    } else {
                        ModuleText(module)
                    }
                }
            }
            val transport: @Composable () -> Unit = {
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
                    // Many Amiga formats have no known length; the replay routine only says when it is done.
                    Text(if (duration > 0) formatTime(duration) else "–:––", style = MaterialTheme.typography.labelMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                ) {
                    // Shuffle and repeat flank the main buttons as plain icons, lit while they are on.
                    val on = MaterialTheme.colorScheme.primary
                    val off = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    IconButton(onClick = player::toggleShuffle) {
                        Icon(
                            painterResource(R.drawable.ic_shuffle),
                            stringResource(if (nowPlaying.shuffle) R.string.shuffle_on else R.string.shuffle_off),
                            tint = if (nowPlaying.shuffle) on else off,
                        )
                    }
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
                    IconButton(onClick = player::cycleRepeat) {
                        val (icon, label) = when (nowPlaying.repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one to R.string.repeat_one
                            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat to R.string.repeat_all
                            else -> R.drawable.ic_repeat to R.string.repeat_off
                        }
                        Icon(
                            painterResource(icon),
                            stringResource(label),
                            tint = if (nowPlaying.repeatMode == Player.REPEAT_MODE_OFF) off else on,
                        )
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        header()
                        transport()
                    }
                    Column(Modifier.weight(1.3f).fillMaxHeight().padding(bottom = 16.dp)) { views() }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    header()
                    views()
                    transport()
                }
            }
        }
    }
    info?.takeIf { retro }?.let { module ->
        // Declared after the player's own handler, so Back closes this view first.
        BackHandler { showRetro = false }
        RetroView(
            module,
            controls = RetroControls(
                isPlaying = nowPlaying.isPlaying,
                hasNext = nowPlaying.hasNext,
                onPrevious = player::previous,
                onPlayPause = player::togglePlayPause,
                onNext = player::next,
                title = nowPlaying.title,
                composer = nowPlaying.composer,
                positionMs = positionMs,
                durationMs = nowPlaying.durationMs,
            ),
            onClose = { showRetro = false },
        )
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
