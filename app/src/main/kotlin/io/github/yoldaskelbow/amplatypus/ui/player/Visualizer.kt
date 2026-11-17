// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yoldaskelbow.amplatypus.audio.ModuleEngine
import kotlin.math.sqrt

/** Follows the engine frame by frame while the composable is on screen and [active]. */
@Composable
fun rememberVisual(active: Boolean = true): State<ModuleEngine.Visual?> = produceState<ModuleEngine.Visual?>(null, active) {
    while (active) {
        withFrameNanos { }
        value = ModuleEngine.current?.visual()
    }
}

// The canvases below sit in their own graphics layer, so their many redraws per second re-record
// only themselves and not the rest of the screen.

/**
 * One level meter per channel, as in tracker programs: the bar jumps up when a note is hit and falls
 * back. Drawn as stacked segments like an LED meter.
 */
@Composable
fun ChannelMeters(visual: State<ModuleEngine.Visual?>, channels: Int, modifier: Modifier = Modifier, segments: Int = 16) {
    val low = MaterialTheme.colorScheme.primary
    val mid = MaterialTheme.colorScheme.tertiary
    val high = MaterialTheme.colorScheme.error
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    // Shown levels fall back smoothly between the values the engine reports.
    val shown = remember(channels) { FloatArray(channels) }

    Canvas(modifier.graphicsLayer()) {
        val current = visual.value
        val count = channels.coerceAtLeast(1)
        val gap = if (count > 16) 2.dp.toPx() else 6.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        val segmentGap = 2.dp.toPx()
        val segmentHeight = (size.height - segmentGap * (segments - 1)) / segments
        val radius = CornerRadius(minOf(barWidth, segmentHeight) / 3)
        for (channel in 0 until count) {
            val raw = current?.levels?.getOrNull(channel) ?: 0f
            val target = if (current?.playing == true) sqrt(raw.coerceIn(0f, 1f)) else 0f
            val level = if (channel < shown.size) {
                shown[channel] = maxOf(target, shown[channel] - 0.035f)
                shown[channel]
            } else {
                target
            }
            val lit = (level * segments + 0.5f).toInt()
            val x = channel * (barWidth + gap)
            for (segment in 0 until segments) {
                val fraction = segment / (segments - 1f)
                val color = when {
                    segment >= lit -> off
                    fraction < 0.6f -> lerp(low, mid, fraction / 0.6f)
                    else -> lerp(mid, high, (fraction - 0.6f) / 0.4f)
                }
                val y = size.height - (segment + 1) * segmentHeight - segment * segmentGap
                drawRoundRect(color, Offset(x, y), Size(barWidth, segmentHeight), radius)
            }
        }
    }
}

/**
 * One oscilloscope per channel, like ProTracker's quadrascope: each box draws the waveform that
 * channel is producing right now. Modules with more than four channels get several rows.
 */
@Composable
fun ChannelScopes(
    visual: State<ModuleEngine.Visual?>,
    channels: Int,
    modifier: Modifier = Modifier,
    muted: BooleanArray = BooleanArray(0),
    onTap: (Int) -> Unit = {},
    onLongPress: (Int) -> Unit = {},
) {
    val line = MaterialTheme.colorScheme.primary
    val mutedLine = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val mutedBox = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    val cross = MaterialTheme.colorScheme.error
    val box = MaterialTheme.colorScheme.surfaceContainerLowest
    val axis = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val path = remember { Path() }

    // Finds the scope under a touch; mirrors the grid drawn below.
    fun channelAt(position: Offset, width: Float, height: Float): Int? {
        val count = channels.coerceAtLeast(1)
        val columns = scopeColumns(count)
        val rows = (count + columns - 1) / columns
        val column = (position.x / width * columns).toInt().coerceIn(0, columns - 1)
        val row = (position.y / height * rows).toInt().coerceIn(0, rows - 1)
        return (row * columns + column).takeIf { it < count }
    }

    val gestures = Modifier.pointerInput(channels) {
        detectTapGestures(
            onTap = { position -> channelAt(position, size.width.toFloat(), size.height.toFloat())?.let(onTap) },
            onLongPress = { position -> channelAt(position, size.width.toFloat(), size.height.toFloat())?.let(onLongPress) },
        )
    }

    Canvas(modifier.then(gestures).graphicsLayer()) {
        val count = channels.coerceAtLeast(1)
        val columns = scopeColumns(count)
        val rows = (count + columns - 1) / columns
        val gap = 6.dp.toPx()
        val cellWidth = (size.width - gap * (columns - 1)) / columns
        val cellHeight = (size.height - gap * (rows - 1)) / rows
        val waves = visual.value?.takeIf { it.playing }?.waves
        val points = ModuleEngine.SCOPE_POINTS
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (channel in 0 until count) {
            val left = (channel % columns) * (cellWidth + gap)
            val top = (channel / columns) * (cellHeight + gap)
            val middle = top + cellHeight / 2
            val isMuted = muted.getOrNull(channel) == true
            drawRoundRect(if (isMuted) mutedBox else box, Offset(left, top), Size(cellWidth, cellHeight), CornerRadius(8.dp.toPx()))
            if (isMuted) {
                // A muted channel shows a struck-through, flat line, as a switched-off channel in a tracker.
                drawLine(mutedLine, Offset(left + 6.dp.toPx(), middle), Offset(left + cellWidth - 6.dp.toPx(), middle), 1.5.dp.toPx())
                val arm = minOf(cellWidth, cellHeight) * 0.18f
                val centre = Offset(left + cellWidth / 2, middle)
                drawLine(cross, centre - Offset(arm, arm), centre + Offset(arm, arm), 2.dp.toPx(), StrokeCap.Round)
                drawLine(cross, centre + Offset(-arm, arm), centre + Offset(arm, -arm), 2.dp.toPx(), StrokeCap.Round)
                continue
            }
            if (waves == null || waves.size < (channel + 1) * points) {
                drawLine(axis, Offset(left + 6.dp.toPx(), middle), Offset(left + cellWidth - 6.dp.toPx(), middle), 1.dp.toPx())
                continue
            }
            path.rewind()
            val inner = cellWidth - 12.dp.toPx()
            for (i in 0 until points) {
                // A single channel is quiet compared with the full mix, so it is drawn enlarged.
                val sample = (waves[channel * points + i] * SCOPE_GAIN).coerceIn(-1f, 1f)
                val x = left + 6.dp.toPx() + inner * i / (points - 1)
                val y = middle - sample * (cellHeight / 2 - 4.dp.toPx())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, line, style = stroke)
        }
    }
}

/** How many scopes sit side by side for a module with [channels] channels. */
fun scopeColumns(channels: Int): Int = when {
    channels <= 4 -> channels.coerceAtLeast(1)
    channels <= 8 -> 4
    else -> 8
}

internal const val SCOPE_GAIN = 3f

/**
 * The pattern scrolling past the play position, as in a tracker: the current row stays in the middle
 * and is highlighted. The text is scaled so that all channels fit the width; with many channels the
 * cells show less (first the effect is dropped, then the instrument) before the view starts to scroll.
 */
@Composable
fun PatternView(visual: State<ModuleEngine.Visual?>, generation: Int, channels: Int, modifier: Modifier = Modifier, visibleRows: Int = 9) {
    // The engine reports many times per row; recompose only when the row actually changes.
    val position by remember { derivedStateOf { visual.value?.let { it.pattern to it.row } ?: (0 to 0) } }
    val (pattern, row) = position
    val rows = remember(generation, pattern) { ModuleEngine.current?.patternRows(pattern) ?: emptyList() }
    val half = visibleRows / 2
    val scroll = rememberScrollState()
    val highlight = MaterialTheme.colorScheme.primaryContainer
    val onHighlight = MaterialTheme.colorScheme.onPrimaryContainer
    val normal = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(modifier) {
        val count = channels.coerceAtLeast(1)
        // Characters per row: "00" + two spaces, then the cells with two spaces between them.
        fun charsFor(cell: Int) = 4 + count * cell + (count - 1) * 2
        val available = maxWidth.value - 32f
        fun sizeFor(cell: Int) = available / (charsFor(cell) * MONO_ADVANCE)
        // Full cell "C-5 01 A0F", then "C-5 01", then just the note.
        val cell = listOf(10, 6, 3).firstOrNull { sizeFor(it) >= MIN_PATTERN_SP } ?: 3
        val fits = sizeFor(cell) >= MIN_PATTERN_SP
        val fontSize = sizeFor(cell).coerceIn(MIN_PATTERN_SP, MAX_PATTERN_SP)
        val rowHeight = (fontSize * 1.5f).dp
        // Sizes are worked out in dp so the system font scale cannot push the text past the edge.
        val density = LocalDensity.current
        val fontSp = with(density) { fontSize.dp.toSp() }
        val lineSp = with(density) { rowHeight.toSp() }

        @Composable
        fun Line(text: String, index: Int, isCurrent: Boolean, lineModifier: Modifier) {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSp,
                lineHeight = lineSp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = when {
                    isCurrent -> onHighlight
                    index % 4 == 0 -> normal
                    else -> normal.copy(alpha = 0.6f)
                },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .height(rowHeight)
                    .background(if (isCurrent) highlight else Color.Transparent)
                    .then(lineModifier),
            )
        }

        fun cells(index: Int) = rows.getOrNull(index)?.split('|')?.joinToString("  ") { it.take(cell) }

        if (fits) {
            Column(Modifier.fillMaxWidth()) {
                for (offset in -half..half) {
                    val index = row + offset
                    val text = cells(index)
                    Line(
                        text = if (text == null) " " else "%02X  %s".format(index, text),
                        index = index,
                        isCurrent = offset == 0,
                        lineModifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
        } else {
            // Too many channels for the width: row numbers stay put, the channels scroll sideways.
            Row(Modifier.fillMaxWidth()) {
                Column {
                    for (offset in -half..half) {
                        val index = row + offset
                        Line(
                            text = if (rows.getOrNull(index) == null) "  " else "%02X".format(index),
                            index = index,
                            isCurrent = offset == 0,
                            lineModifier = Modifier.padding(start = 8.dp, end = 6.dp),
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // Fade the edge where more channels are hidden.
                            val fade = 28.dp.toPx()
                            if (scroll.canScrollBackward) {
                                drawRect(
                                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fade),
                                    size = Size(fade, size.height),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                            if (scroll.canScrollForward) {
                                drawRect(
                                    Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - fade, size.width),
                                    topLeft = Offset(size.width - fade, 0f),
                                    size = Size(fade, size.height),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
                        .horizontalScroll(scroll),
                ) {
                    for (offset in -half..half) {
                        val index = row + offset
                        // Pad missing rows to full width so the highlight bar never changes length.
                        val text = cells(index) ?: " ".repeat(charsFor(cell) - 4)
                        Line(text = text, index = index, isCurrent = offset == 0, lineModifier = Modifier.padding(horizontal = 6.dp))
                    }
                }
            }
        }
    }
}

// Width of one monospace character relative to the font size.
private const val MONO_ADVANCE = 0.602f
private const val MIN_PATTERN_SP = 9.5f
private const val MAX_PATTERN_SP = 17f
