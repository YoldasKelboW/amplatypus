// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.audio.ModuleEngine

/*
 * A player view in the style of FastTracker 2, for XM files: blue-green bevelled panels, a grid of
 * scopes, and the pattern editor in yellow on black with the volume column. Drawn from the app's
 * own data with the free pixel font Press Start 2P; nothing of the original program is used.
 */

private object Ft {
    // FastTracker 2's blue-green desktop with yellow pattern text.
    val face = Color(0xFF46697A)
    val light = Color(0xFF93B6C4)
    val shadow = Color(0xFF1C2E36)
    val label = Color(0xFFFFFFFF)
    val screen = Color(0xFF000000)
    val value = Color(0xFFFFFFFF)
    val patternText = Color(0xFFFFE36B)
    val patternDim = Color(0xFFB3A04A)
    val beatRow = Color(0xFF0F1E25)
    val rowBar = Color(0xFF3F7088)
    val header = Color(0xFF2A4552)
    val scope = Color(0xFFE8D86A)
    val vu = Color(0xFFFFE36B)
}

/** The view in the style of FastTracker 2, for XM files. */
@Composable
internal fun FastTrackerView(info: ModuleEngine.ModuleInfo, controls: RetroControls, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val visual = rememberVisual(true)
    val position by remember { derivedStateOf { visual.value?.let { Triple(it.order, it.pattern, it.row) } ?: Triple(0, 0, 0) } }
    val tempo by remember { derivedStateOf { visual.value?.let { it.speed to it.bpm } ?: (0 to 0) } }
    val (order, pattern, row) = position
    val rows = remember(info.generation, pattern) { ModuleEngine.current?.patternRows(pattern) ?: emptyList() }
    val names = info.instruments.ifEmpty { info.samples }
    val instrument = remember(rows, row) { lastSampleName(rows, row, names) }
    val label = TextStyle(fontFamily = pixelFont, fontSize = 10.sp, color = Ft.label)
    val value = TextStyle(fontFamily = pixelFont, fontSize = 11.sp, color = Ft.value)

    fun Modifier.panel(sunken: Boolean = false) =
        bevel(sunken = sunken, fill = if (sunken) Ft.screen else Ft.face, light = Ft.light, shadow = Ft.shadow)

    RetroLayout(
        modifier = modifier,
        background = Ft.face,
        pattern = { place -> FtPattern(visual, rows, row, info.channels, place.panel(sunken = true)) },
        controls = {
            val buttonLabel = label.copy(fontSize = 12.sp)
            RetroControlBar(controls) { control, enabled, onClick, slot ->
                val (text, description) = when (control) {
                    RetroControl.PREVIOUS -> "Prev" to stringResource(R.string.previous)
                    RetroControl.PLAY_PAUSE -> if (controls.isPlaying) "Pause" to stringResource(R.string.pause) else "Play" to stringResource(R.string.play)
                    RetroControl.NEXT -> "Next" to stringResource(R.string.next)
                }
                RetroButton(text, description, enabled, onClick, slot, buttonLabel, fill = Ft.face, light = Ft.light, shadow = Ft.shadow)
            }
        },
    ) { wide ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FtReadout("Pos.", "%03d".format(order), label, value)
                FtReadout("Ptn.", "%03d".format(pattern), label, value)
                FtReadout("Songlen.", "%03d".format(info.orders), label, value)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FtReadout("BPM", if (tempo.second > 0) "%03d".format(tempo.second) else "---", label, value)
                FtReadout("Spd.", if (tempo.first > 0) "%02d".format(tempo.first) else "--", label, value)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .panel()
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.retro_close), onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Exit", style = label)
                }
            }
        }

        FtScopes(
            visual,
            info.channels,
            Modifier.fillMaxWidth().then(if (wide) Modifier.weight(1f) else Modifier.height(88.dp)).panel(sunken = true),
        )

        FtNameLine("Songname:", info.title, label, value)
        FtNameLine("Instr.:", instrument, label, value)
    }
}

@Composable
private fun FtReadout(name: String, number: String, label: TextStyle, value: TextStyle) {
    Row(
        Modifier.fillMaxWidth().height(26.dp).bevel(fill = Ft.face, light = Ft.light, shadow = Ft.shadow).padding(start = 6.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = label, modifier = Modifier.weight(1f), maxLines = 1)
        Box(
            Modifier.width(50.dp).fillMaxHeight().padding(vertical = 3.dp).bevel(sunken = true, fill = Ft.screen, light = Ft.light, shadow = Ft.shadow),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = value)
        }
    }
}

@Composable
private fun FtNameLine(name: String, text: String, label: TextStyle, value: TextStyle) {
    Row(
        Modifier.fillMaxWidth().height(24.dp).bevel(fill = Ft.face, light = Ft.light, shadow = Ft.shadow).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = label)
        Text(" $text", style = value, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
    }
}

/** One scope per channel in a grid, up to eight across, as FastTracker lays them out above its editor. */
@Composable
private fun FtScopes(visual: State<ModuleEngine.Visual?>, channels: Int, modifier: Modifier) {
    val path = remember { Path() }
    Box(modifier.padding(3.dp)) {
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val count = channels.coerceAtLeast(1)
            val columns = if (count <= 4) count else ((count + 1) / 2).coerceAtMost(8)
            val rows = (count + columns - 1) / columns
            val gap = 2.dp.toPx()
            val cellWidth = (size.width - gap * (columns - 1)) / columns
            val cellHeight = (size.height - gap * (rows - 1)) / rows
            val waves = visual.value?.takeIf { it.playing }?.waves
            val points = ModuleEngine.SCOPE_POINTS
            val stroke = Stroke(width = 1.dp.toPx())
            for (channel in 0 until count) {
                val left = (channel % columns) * (cellWidth + gap)
                val top = (channel / columns) * (cellHeight + gap)
                val middle = top + cellHeight / 2
                drawRect(Ft.header, Offset(left, top), Size(cellWidth, cellHeight), style = Stroke(1f))
                path.rewind()
                for (i in 0 until points) {
                    val sample = if (waves != null && waves.size >= (channel + 1) * points) {
                        (waves[channel * points + i] * SCOPE_GAIN).coerceIn(-1f, 1f)
                    } else {
                        0f
                    }
                    val x = left + cellWidth * i / (points - 1)
                    val y = middle - sample * (cellHeight / 2 - 2.dp.toPx())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Ft.scope, style = stroke)
            }
        }
    }
}

/**
 * The pattern editor: channel numbers with small level bars at the top, then the rows in yellow on
 * black, every fourth row set off, the playing row on a lighter bar. When the channels do not fit, the
 * cells get shorter (without effect, then note and instrument, then note only) and the rest can be
 * reached by swiping sideways.
 */
@Composable
private fun FtPattern(visual: State<ModuleEngine.Visual?>, rows: List<String>, row: Int, channels: Int, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 512)
    var first by remember(channels) { mutableIntStateOf(0) }
    var drag by remember { mutableFloatStateOf(0f) }
    var stepPx by remember { mutableFloatStateOf(1f) }
    var shown by remember { mutableIntStateOf(channels) }
    val swipe = Modifier.pointerInput(channels) {
        detectHorizontalDragGestures(onDragEnd = { drag = 0f }) { _, amount ->
            drag += amount
            while (drag <= -stepPx && first + shown < channels) { first++; drag += stepPx }
            while (drag >= stepPx && first > 0) { first--; drag -= stepPx }
        }
    }
    Box(modifier.padding(3.dp).then(swipe)) {
        Canvas(Modifier.fillMaxSize()) {
            val count = channels.coerceAtLeast(1)
            val gap = 2.dp.toPx()
            val pad = 2.dp.toPx()
            val minChar = 6.dp.toPx()
            val maxChar = 12.dp.toPx()
            val target = count.coerceAtMost(8)
            // Full cell "C-4 01 v40 A0F", then without effect, note and instrument, note only.
            val details = listOf(14, 10, 6, 3)
            fun charFor(chars: Int, channelsShown: Int) =
                (size.width - gap * channelsShown - pad * 2 * (channelsShown + 1)) / ((3 + chars * channelsShown) * CHAR_ADVANCE)
            val cellChars = details.firstOrNull { charFor(it, target) >= minChar } ?: 3
            val charPx = charFor(cellChars, target).coerceIn(minChar, maxChar)
            val numberWidth = 3 * charPx + pad * 2
            val cellWidth = cellChars * charPx + pad * 2
            val visibleChannels = ((size.width - numberWidth) / (cellWidth + gap)).toInt().coerceIn(1, count)
            shown = visibleChannels
            stepPx = cellWidth + gap
            if (first + visibleChannels > count) first = (count - visibleChannels).coerceAtLeast(0)
            val channelWidth = (size.width - numberWidth - gap * visibleChannels) / visibleChannels
            val style = TextStyle(fontFamily = pixelFont, fontSize = (charPx / density / fontScale).sp)
            val lineHeight = charPx * 1.45f
            val headerHeight = lineHeight * 1.3f
            val textTop = (lineHeight - charPx) / 2

            fun left(column: Int) = if (column == 0) 0f else numberWidth + gap + (column - 1) * (channelWidth + gap)

            // Header: channel numbers and a level bar under each.
            val levels = visual.value?.takeIf { it.playing }?.levels
            for (column in 1..visibleChannels) {
                val channel = first + column - 1
                val x = left(column)
                drawRect(Ft.header, Offset(x, 0f), Size(channelWidth, headerHeight))
                drawText(measurer, "%02d".format(channel + 1), Offset(x + pad, textTop), style.copy(color = Ft.label), softWrap = false, maxLines = 1)
                val level = levels?.getOrNull(channel)?.coerceIn(0f, 1f) ?: 0f
                val barLeft = x + 3 * charPx + pad * 2
                val barWidth = (channelWidth - (barLeft - x) - pad) * level
                if (barWidth > 0f) drawRect(Ft.vu, Offset(barLeft, headerHeight * 0.3f), Size(barWidth, headerHeight * 0.4f))
            }
            if (first > 0) drawText(measurer, "<", Offset(pad, textTop), style.copy(color = Ft.label), softWrap = false, maxLines = 1)
            if (first + visibleChannels < count) {
                drawText(measurer, ">", Offset(charPx + pad, textTop), style.copy(color = Ft.label), softWrap = false, maxLines = 1)
            }

            val bodyTop = headerHeight + gap
            val bodyHeight = size.height - bodyTop
            val visible = (bodyHeight / lineHeight).toInt().let { if (it % 2 == 0) it - 1 else it }.coerceAtLeast(3)
            val half = visible / 2
            val top = bodyTop + (bodyHeight - visible * lineHeight) / 2
            for (offset in -half..half) {
                val index = row + offset
                val cells = rows.getOrNull(index)?.split('|') ?: continue
                val y = top + (offset + half) * lineHeight
                val current = offset == 0
                when {
                    current -> drawRect(Ft.rowBar, Offset(0f, y), Size(size.width, lineHeight))
                    index % 4 == 0 -> drawRect(Ft.beatRow, Offset(0f, y), Size(size.width, lineHeight))
                }
                val color = when {
                    current -> Color.White
                    index % 4 == 0 -> Ft.patternText
                    else -> Ft.patternDim
                }
                drawText(measurer, "%02X".format(index), Offset(pad, y + textTop), style.copy(color = color), softWrap = false, maxLines = 1)
                for (column in 1..visibleChannels) {
                    val text = ftCell(cells.getOrNull(first + column - 1).orEmpty()).take(cellChars)
                    drawText(measurer, text, Offset(left(column) + pad, y + textTop), style.copy(color = color), softWrap = false, maxLines = 1)
                }
            }
        }
    }
}

/**
 * "C-5 01 A0F v40" as FastTracker shows a cell, "C-4 01 v40 A0F": the volume column before the effect,
 * and the octave one lower, as FastTracker counts it. Shortened cells keep the front: note and
 * instrument first.
 */
internal fun ftCell(cell: String): String {
    val padded = cell.padEnd(14)
    val rawNote = padded.substring(0, 3)
    val note = when {
        rawNote.trim('.', ' ').isEmpty() -> "---"
        rawNote[2].isDigit() -> rawNote.substring(0, 2) + ((rawNote[2] - '0') - 1).coerceIn(0, 9)
        else -> rawNote
    }
    val instrument = padded.substring(4, 6).replace(' ', '.')
    val effect = padded.substring(7, 10).replace(' ', '.')
    val volume = padded.substring(11, 14).replace(' ', '.')
    return "$note $instrument $volume $effect"
}
