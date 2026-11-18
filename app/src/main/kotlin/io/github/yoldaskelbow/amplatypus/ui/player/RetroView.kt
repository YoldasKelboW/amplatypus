// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.audio.ModuleEngine

/*
 * A player view in the style of the classic Amiga trackers: grey bevelled panels with readouts, a
 * quadrascope and the pattern editor in blue on black, with level bars rising above the playing row.
 * It is drawn from the app's own data. The pixel font is Press Start 2P (SIL Open Font License);
 * no graphics, fonts or logos of the original programs are used.
 */

/** The tracker whose look a retro view takes. */
enum class RetroStyle { PROTRACKER, FASTTRACKER, IMPULSE, SCREAM, HIVELY }

/** Which retro view fits this module, if any: MOD, XM, IT, S3M, AHX and HVL. */
fun retroStyleOf(info: ModuleEngine.ModuleInfo?): RetroStyle? {
    if (info == null || !info.hasPattern || info.channels < 1) return null
    val type = info.type.lowercase()
    val mod = ("mod" in type && "mmd" !in type) || "soundtracker" in type || "noisetracker" in type || "startrekker" in type
    return when {
        mod && info.channels <= 8 -> RetroStyle.PROTRACKER
        "fasttracker 2" in type && info.channels <= 32 -> RetroStyle.FASTTRACKER
        ("impulse tracker" in type || "openmpt" in type) && info.channels <= 64 -> RetroStyle.IMPULSE
        "scream tracker 3" in type && info.channels <= 32 -> RetroStyle.SCREAM
        "ahx" in type || "hivelytracker" in type -> RetroStyle.HIVELY
        else -> null
    }
}

fun hasRetroView(info: ModuleEngine.ModuleInfo?): Boolean = retroStyleOf(info) != null

/** Opens the retro view that fits [info]; nothing if there is none. */
@Composable
fun RetroView(info: ModuleEngine.ModuleInfo, controls: RetroControls, onClose: () -> Unit, modifier: Modifier = Modifier) {
    when (retroStyleOf(info)) {
        RetroStyle.PROTRACKER -> ProTrackerView(info, controls, onClose, modifier)
        RetroStyle.FASTTRACKER -> FastTrackerView(info, controls, onClose, modifier)
        RetroStyle.IMPULSE -> ClassicTrackerView(info, ImpulseTheme, controls, onClose, modifier)
        RetroStyle.SCREAM -> ClassicTrackerView(info, ScreamTheme, controls, onClose, modifier)
        RetroStyle.HIVELY -> ClassicTrackerView(info, HivelyTheme, controls, onClose, modifier)
        null -> Unit
    }
}

private object Retro {
    val face = Color(0xFFAAAAAA)
    val light = Color(0xFFDDDDDD)
    val shadow = Color(0xFF555555)
    val label = Color(0xFFEEEEEE)
    val value = Color(0xFF000000)
    val screen = Color(0xFF000000)
    val patternText = Color(0xFF4455EE)
    val rowBar = Color(0xFFBBBBBB)
    val divider = Color(0xFF888888)
    val scope = Color(0xFFEEDD00)
    val vuBottom = Color(0xFF22DD22)
    val vuMiddle = Color(0xFFDDDD00)
    val vuTop = Color(0xFFFF8800)
}

internal val pixelFont = FontFamily(Font(R.font.press_start_2p))

/**
 * Draws one line of [text] in [color]. The measurer caches layouts without regard to colour, so a cell
 * that appears in several rows would keep the colour it was first drawn in; the colour is therefore
 * given at drawing time.
 */
internal fun DrawScope.drawTextIn(measurer: TextMeasurer, text: String, topLeft: Offset, style: TextStyle, color: Color) {
    drawText(measurer.measure(text, style, softWrap = false, maxLines = 1), color = color, topLeft = topLeft)
}

// Press Start 2P is a square 8 x 8 font: every character is one em wide.
internal const val CHAR_ADVANCE = 1.0f

/** A raised panel: light top-left edge, dark bottom-right edge. Sunken swaps the edges. */
internal fun Modifier.bevel(
    sunken: Boolean = false,
    fill: Color = Retro.face,
    edge: Dp = 2.dp,
    light: Color = Retro.light,
    shadow: Color = Retro.shadow,
): Modifier = drawBehind {
    val e = edge.toPx()
    drawRect(fill)
    val topLeft = if (sunken) shadow else light
    val bottomRight = if (sunken) light else shadow
    drawRect(topLeft, Offset.Zero, Size(size.width, e))
    drawRect(topLeft, Offset.Zero, Size(e, size.height))
    drawRect(bottomRight, Offset(0f, size.height - e), Size(size.width, e))
    drawRect(bottomRight, Offset(size.width - e, 0f), Size(e, size.height))
}

/** What the control bar of every tracker view operates. */
class RetroControls(
    val isPlaying: Boolean,
    val hasNext: Boolean,
    val onPrevious: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
)

enum class RetroControl { PREVIOUS, PLAY_PAUSE, NEXT }

/**
 * The control bar of the tracker views. Every view has it at the bottom, with the same three buttons in
 * the same order and size, so they are always where the thumb expects them; only [button] differs and
 * draws each one in the style of its tracker.
 */
@Composable
fun RetroControlBar(
    controls: RetroControls,
    modifier: Modifier = Modifier,
    button: @Composable (control: RetroControl, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) -> Unit,
) {
    Row(modifier.fillMaxWidth().height(RETRO_CONTROL_HEIGHT), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val slot = Modifier.weight(1f).fillMaxHeight()
        button(RetroControl.PREVIOUS, true, controls.onPrevious, slot)
        button(RetroControl.PLAY_PAUSE, true, controls.onPlayPause, slot)
        button(RetroControl.NEXT, controls.hasNext, controls.onNext, slot)
    }
}

val RETRO_CONTROL_HEIGHT = 48.dp

/** A bevelled tracker button: raised, pressed in while held. The colours default to ProTracker's grey. */
@Composable
internal fun RetroButton(
    text: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    style: TextStyle,
    fill: Color = Retro.face,
    light: Color = Retro.light,
    shadow: Color = Retro.shadow,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .bevel(sunken = pressed, fill = fill, light = light, shadow = shadow)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (enabled) style else style.copy(color = shadow, shadow = null))
    }
}

/** The view in the style of ProTracker, for MOD files. */
@Composable
private fun ProTrackerView(info: ModuleEngine.ModuleInfo, controls: RetroControls, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val visual = rememberVisual(true)
    val position by remember { derivedStateOf { visual.value?.let { Triple(it.order, it.pattern, it.row) } ?: Triple(0, 0, 0) } }
    val tempo by remember { derivedStateOf { visual.value?.let { it.speed to it.bpm } ?: (0 to 0) } }
    val (order, pattern, row) = position
    val rows = remember(info.generation, pattern) { ModuleEngine.current?.patternRows(pattern) ?: emptyList() }
    val sample = remember(rows, row) { lastSampleName(rows, row, info.samples) }
    // Labels are pressed into the panel: light letters with a dark edge below.
    val label = TextStyle(fontFamily = pixelFont, fontSize = 10.sp, color = Retro.label, shadow = Shadow(Retro.shadow, Offset(2f, 2f), 0f))
    val value = TextStyle(fontFamily = pixelFont, fontSize = 11.sp, color = Retro.value)

    RetroLayout(
        modifier = modifier,
        background = Retro.face,
        pattern = { place -> RetroPattern(visual, rows, row, info.channels, place) },
        controls = {
            val buttonLabel = label.copy(fontSize = 12.sp)
            RetroControlBar(controls) { control, enabled, onClick, slot ->
                when (control) {
                    RetroControl.PREVIOUS ->
                        RetroButton("PREV", stringResource(R.string.previous), enabled, onClick, slot, buttonLabel)
                    RetroControl.PLAY_PAUSE -> if (controls.isPlaying) {
                        RetroButton("PAUSE", stringResource(R.string.pause), enabled, onClick, slot, buttonLabel)
                    } else {
                        RetroButton("PLAY", stringResource(R.string.play), enabled, onClick, slot, buttonLabel)
                    }
                    RetroControl.NEXT ->
                        RetroButton("NEXT", stringResource(R.string.next), enabled, onClick, slot, buttonLabel)
                }
            }
        },
    ) { wide ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Readout("POS", "%04d".format(order), label, value)
                Readout("PATTERN", "%04d".format(pattern), label, value)
                Readout("LENGTH", "%04d".format(info.orders), label, value)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Readout("TEMPO", if (tempo.second > 0) "%04d".format(tempo.second) else "----", label, value)
                Readout("SPEED", if (tempo.first > 0) "%04d".format(tempo.first) else "----", label, value)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .bevel()
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.retro_close), onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("EXIT", style = label)
                }
            }
        }

        Quadrascope(visual, info.channels, Modifier.fillMaxWidth().then(if (wide) Modifier.weight(1f) else Modifier.height(84.dp)))

        NameLine("SONGNAME:", info.title, label, value)
        NameLine("SAMPLE:", sample, label, value)
    }
}

/**
 * The frame every tracker view shares: [panels] (readouts, scopes, names) on top, the [pattern] below
 * and the [controls] at the very bottom. On a phone held sideways the pattern moves to the right of the
 * panels, so it keeps enough rows; the controls stay at the bottom either way. [panels] is told which.
 */
@Composable
internal fun RetroLayout(
    modifier: Modifier,
    background: Color,
    pattern: @Composable (Modifier) -> Unit,
    controls: @Composable () -> Unit,
    panels: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(background).systemBarsPadding().padding(4.dp)) {
        val wide = maxWidth > maxHeight && maxHeight < 520.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (wide) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) { panels(true) }
                    pattern(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                panels(false)
                pattern(Modifier.fillMaxWidth().weight(1f))
            }
            controls()
        }
    }
}

@Composable
private fun Readout(name: String, number: String, label: TextStyle, value: TextStyle) {
    Row(Modifier.fillMaxWidth().height(26.dp).bevel().padding(start = 6.dp, end = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = label, modifier = Modifier.weight(1f), maxLines = 1)
        Box(Modifier.width(62.dp).fillMaxHeight().padding(vertical = 3.dp).bevel(sunken = true), contentAlignment = Alignment.Center) {
            Text(number, style = value)
        }
    }
}

@Composable
private fun NameLine(name: String, text: String, label: TextStyle, value: TextStyle) {
    Row(Modifier.fillMaxWidth().height(24.dp).bevel().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = label)
        Text(
            " " + text.uppercase().replace(' ', '_'),
            style = value,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
    }
}

/** Four (or up to eight) scopes side by side in a black window, split by grey bars. */
@Composable
private fun Quadrascope(visual: State<ModuleEngine.Visual?>, channels: Int, modifier: Modifier) {
    val path = remember { Path() }
    Box(modifier.bevel(sunken = true, fill = Retro.screen)) {
        Canvas(Modifier.fillMaxSize().padding(2.dp).graphicsLayer()) {
            val count = channels.coerceAtLeast(1)
            val bar = 4.dp.toPx()
            val cell = (size.width - bar * (count - 1)) / count
            val middle = size.height / 2
            val waves = visual.value?.takeIf { it.playing }?.waves
            val points = ModuleEngine.SCOPE_POINTS
            val stroke = Stroke(width = 1.5.dp.toPx())
            for (channel in 0 until count) {
                val left = channel * (cell + bar)
                if (channel > 0) drawRect(Retro.divider, Offset(left - bar, 0f), Size(bar, size.height))
                path.rewind()
                for (i in 0 until points) {
                    val sample = if (waves != null && waves.size >= (channel + 1) * points) {
                        (waves[channel * points + i] * SCOPE_GAIN).coerceIn(-1f, 1f)
                    } else {
                        0f
                    }
                    val x = left + cell * i / (points - 1)
                    val y = middle - sample * (middle - 3.dp.toPx())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Retro.scope, style = stroke)
            }
        }
    }
}

/**
 * The pattern editor: row numbers and one column per channel, blue on black, the playing row on a grey
 * bar, and each channel's level as a bar rising from that row.
 */
@Composable
private fun RetroPattern(visual: State<ModuleEngine.Visual?>, rows: List<String>, row: Int, channels: Int, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 256)
    Box(modifier.bevel(sunken = true, fill = Retro.screen)) {
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            val count = channels.coerceAtLeast(1)
            val gap = 3.dp.toPx()
            val pad = 2.dp.toPx()
            // Width in characters: two for the row number, eight per channel ("G#31B972").
            val columnsWidth = size.width - gap * count - pad * 2 * (count + 1)
            val charPx = (columnsWidth / ((2 + 8 * count) * CHAR_ADVANCE)).coerceAtMost(14.dp.toPx())
            val style = TextStyle(fontFamily = pixelFont, fontSize = (charPx / density / fontScale).sp)
            val lineHeight = charPx * 1.45f
            val numberWidth = 2 * charPx * CHAR_ADVANCE + pad * 2
            val channelWidth = (size.width - numberWidth - gap * count) / count
            val visible = (size.height / lineHeight).toInt().let { if (it % 2 == 0) it - 1 else it }.coerceAtLeast(3)
            val half = visible / 2
            val top = (size.height - visible * lineHeight) / 2
            val textTop = (lineHeight - charPx) / 2

            fun columnLeft(column: Int) = if (column == 0) 0f else numberWidth + gap + (column - 1) * (channelWidth + gap)
            fun columnWidth(column: Int) = if (column == 0) numberWidth else channelWidth

            for (column in 1..count) drawRect(Retro.divider, Offset(columnLeft(column) - gap, 0f), Size(gap, size.height))

            for (offset in -half..half) {
                val index = row + offset
                val cells = rows.getOrNull(index)?.split('|') ?: continue
                val y = top + (offset + half) * lineHeight
                val current = offset == 0
                val color = if (current) Retro.value else Retro.patternText
                for (column in 0..count) {
                    val left = columnLeft(column)
                    if (current) drawRect(Retro.rowBar, Offset(left, y), Size(columnWidth(column), lineHeight))
                    val text = if (column == 0) "%02d".format(index) else retroCell(cells.getOrNull(column - 1).orEmpty())
                    drawTextIn(measurer, text, Offset(left + pad, y + textTop), style, color)
                }
            }

            // Level bars rise from the playing row, as in the trackers of the time.
            val levels = visual.value?.takeIf { it.playing }?.levels
            if (levels != null) {
                val rowTop = top + half * lineHeight
                val maxHeight = (half.coerceAtMost(7)) * lineHeight
                val brush = Brush.verticalGradient(
                    listOf(Retro.vuTop, Retro.vuMiddle, Retro.vuBottom),
                    startY = rowTop - maxHeight,
                    endY = rowTop,
                )
                for (channel in 0 until count) {
                    val level = levels.getOrNull(channel)?.coerceIn(0f, 1f) ?: 0f
                    if (level <= 0.01f) continue
                    val height = maxHeight * level
                    val left = columnLeft(channel + 1) + channelWidth * 0.36f
                    drawRect(brush, Offset(left, rowTop - height), Size(channelWidth * 0.14f, height))
                }
            }
        }
    }
}

/**
 * "C-5 01 A0F" (or "... .. ...") as the classic eight characters "C-201A0F" (or "---00000"). The octave
 * is shown as the trackers of the time counted it, three lower than libopenmpt.
 */
internal fun retroCell(cell: String): String {
    val padded = cell.padEnd(10)
    val rawNote = padded.substring(0, 3)
    val note = when {
        rawNote.trim('.', ' ').isEmpty() -> "---"
        rawNote[2].isDigit() -> rawNote.substring(0, 2) + ((rawNote[2] - '0') - 3).coerceIn(0, 9)
        else -> rawNote
    }
    val instrument = padded.substring(4, 6).replace('.', '0').replace(' ', '0')
    val effect = padded.substring(7, 10).replace('.', '0').replace(' ', '0')
    return note + instrument + effect
}

/**
 * The sample last struck at or before [row], as the tracker shows the current sample: its number in
 * hex and its name ("0C: BASS"), or "" before the first note.
 */
internal fun lastSampleName(
    rows: List<String>,
    row: Int,
    samples: List<String>,
    /** How the cells write the number (HivelyTracker: decimal), and how the tracker shows it. */
    cellRadix: Int = 16,
    decimal: Boolean = false,
): String {
    for (index in row.coerceAtMost(rows.size - 1) downTo 0) {
        val cells = rows[index].split('|')
        for (cell in cells.asReversed()) {
            val number = cell.padEnd(10).substring(4, 6).trim().toIntOrNull(cellRadix) ?: continue
            if (number > 0) {
                val name = samples.getOrNull(number - 1)?.trim().orEmpty()
                return (if (decimal) "%02d" else "%02X").format(number) + if (name.isEmpty()) "" else ": $name"
            }
        }
    }
    return ""
}
