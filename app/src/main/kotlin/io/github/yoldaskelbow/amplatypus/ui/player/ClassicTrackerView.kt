// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.player

import androidx.compose.foundation.Canvas
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
 * Player views in the style of further trackers: Impulse Tracker (IT), Scream Tracker 3 (S3M) and
 * HivelyTracker (AHX/HVL). They share one layout - readouts, scopes, names, the pattern editor and the
 * play buttons - and differ in colours, labels and how a pattern cell is written. The colours follow
 * each program's own palette; everything is drawn from the app's data with the pixel font Press Start 2P.
 */

/** The look of one tracker. */
internal data class TrackerTheme(
    val face: Color,
    val light: Color,
    val shadow: Color,
    val label: Color,
    val valueBox: Color,
    val value: Color,
    val screen: Color,
    val patternText: Color,
    val patternDim: Color,
    /** Background of every [beat]th row, and of every [bar]th row; 0 switches either off. */
    val beatRow: Color,
    val barRow: Color,
    val beat: Int,
    val bar: Int,
    val rowBar: Color,
    val rowText: Color,
    val header: Color,
    val headerText: Color,
    val scope: Color,
    val vu: Color,
    val readouts: List<String>,
    val exit: String,
    val songLabel: String,
    val instrumentLabel: String,
    val channelLabel: String,
    val buttons: List<String>,
    /** How many digits the row numbers have (decimal). */
    val rowDigits: Int,
    /** A pattern cell as this tracker writes it, and the shorter forms used when channels do not fit. */
    val cell: (String) -> String,
    val cellLengths: List<Int>,
    /** How the replayer writes instrument numbers in a cell: libopenmpt in hex, HivelyTracker in decimal. */
    val cellRadix: Int = 16,
    /** Whether the tracker shows instrument numbers in decimal (IT, ST3, HivelyTracker) or in hex (OctaMED). */
    val decimalInstruments: Boolean = true,
)

/** Impulse Tracker's own palette ("Camouflage" in Schism Tracker): khaki panels, green notes on black. */
internal val ImpulseTheme = TrackerTheme(
    face = Color(0xFFB69579),
    light = Color(0xFFEAEACA),
    shadow = Color(0xFF7D5945),
    label = Color(0xFF000000),
    valueBox = Color(0xFF000000),
    value = Color(0xFFFFFF55),
    screen = Color(0xFF000000),
    patternText = Color(0xFF66C46A),
    patternDim = Color(0xFF459948),
    beatRow = Color(0xFF34302C),
    barRow = Color(0xFF59403C),
    beat = 4,
    bar = 16,
    rowBar = Color(0xFF205500),
    rowText = Color(0xFFEAEACA),
    header = Color(0xFFB69579),
    headerText = Color(0xFF000000),
    scope = Color(0xFF66C46A),
    vu = Color(0xFFFFFF55),
    readouts = listOf("Order", "Pattern", "Length", "Tempo", "Speed"),
    exit = "Exit",
    songLabel = "Song Name",
    instrumentLabel = "Instrument",
    channelLabel = "Channel",
    buttons = listOf("Prev", "Play", "Pause", "Next"),
    rowDigits = 3,
    cell = ::itCell,
    cellLengths = listOf(14, 10, 6, 3),
)

/** Scream Tracker 3's gold palette ("Gold (Vintage)" in Schism Tracker), grey notes on black. */
internal val ScreamTheme = TrackerTheme(
    face = Color(0xFFA69255),
    light = Color(0xFFFFDF85),
    shadow = Color(0xFF51452A),
    label = Color(0xFF000000),
    valueBox = Color(0xFF000000),
    value = Color(0xFFFFDF85),
    screen = Color(0xFF000000),
    patternText = Color(0xFFD4D0CC),
    patternDim = Color(0xFF9A9692),
    beatRow = Color(0xFF302C28),
    barRow = Color(0xFF302C28),
    beat = 4,
    bar = 0,
    rowBar = Color(0xFF183075),
    rowText = Color(0xFFFFFFFF),
    header = Color(0xFF142459),
    headerText = Color(0xFFFFDF85),
    scope = Color(0xFFFFDF85),
    vu = Color(0xFF00B200),
    readouts = listOf("ORDER", "PATTERN", "LENGTH", "TEMPO", "SPEED"),
    exit = "EXIT",
    songLabel = "SONGNAME:",
    instrumentLabel = "INSTR.:",
    channelLabel = "CH",
    buttons = listOf("PREV", "PLAY", "PAUSE", "NEXT"),
    rowDigits = 2,
    cell = ::itCell,
    cellLengths = listOf(14, 10, 6, 3),
)

/**
 * ZX Spectrum trackers, in the Spectrum's own colours: blue panels with cyan bevels, white notes on black,
 * a red bar with yellow text on the playing row. The notes are what the music played (see engine_zx.cpp).
 */
internal val ZxSpectrumTheme = TrackerTheme(
    face = Color(0xFF0000D7),
    light = Color(0xFF00D7D7),
    shadow = Color(0xFF000000),
    label = Color(0xFFFFFFFF),
    valueBox = Color(0xFF000000),
    value = Color(0xFFFFFF00),
    screen = Color(0xFF000000),
    patternText = Color(0xFFD7D7D7),
    patternDim = Color(0xFF00D7D7),
    beatRow = Color(0xFF000040),
    barRow = Color(0xFF00006B),
    beat = 4,
    bar = 16,
    rowBar = Color(0xFFD70000),
    rowText = Color(0xFFFFFF00),
    header = Color(0xFF0000D7),
    headerText = Color(0xFFFFFFFF),
    scope = Color(0xFF00FF00),
    vu = Color(0xFF00D700),
    readouts = listOf("POSITION", "PATTERN", "LENGTH", "TEMPO", "DELAY"),
    exit = "EXIT",
    songLabel = "TITLE",
    // Sample numbers are not known; the cells carry the noise period where trackers have the instrument.
    instrumentLabel = "NOISE",
    channelLabel = "CHANNEL",
    buttons = listOf("PREV", "PLAY", "PAUSE", "NEXT"),
    rowDigits = 2,
    cell = ::zxCell,
    cellLengths = listOf(12, 5, 3),
    decimalInstruments = false,
)

/** HivelyTracker's steel-blue skin: brushed blue panels, light blue tracks on black. */
internal val HivelyTheme = TrackerTheme(
    face = Color(0xFF6E8CA8),
    light = Color(0xFFA8C4E0),
    shadow = Color(0xFF203A54),
    label = Color(0xFFE0ECF8),
    valueBox = Color(0xFF000000),
    value = Color(0xFFC0D4E8),
    screen = Color(0xFF000000),
    patternText = Color(0xFFB8C8E0),
    patternDim = Color(0xFF8AA2BC),
    beatRow = Color(0xFF000000),
    barRow = Color(0xFF000000),
    beat = 0,
    bar = 0,
    rowBar = Color(0xFF607490),
    rowText = Color(0xFFFFFFFF),
    header = Color(0xFF7088A0),
    headerText = Color(0xFFFFFFFF),
    scope = Color(0xFFB8C8E0),
    vu = Color(0xFFB8C8E0),
    readouts = listOf("Position", "Track", "Length", "Tempo", "Speed"),
    exit = "Exit",
    songLabel = "Songname",
    instrumentLabel = "Instrument",
    channelLabel = "Track",
    buttons = listOf("Prev", "Play", "Pause", "Next"),
    rowDigits = 2,
    cell = ::hvlCell,
    cellLengths = listOf(10, 6, 3),
    cellRadix = 10,
)

/** OctaMED in the colours of the Amiga Workbench 1.3: blue panels, white and orange. */
internal val OctaMedTheme = TrackerTheme(
    face = Color(0xFF0055AA),
    light = Color(0xFFFFFFFF),
    shadow = Color(0xFF000022),
    label = Color(0xFFFFFFFF),
    valueBox = Color(0xFF000022),
    value = Color(0xFFFF8800),
    screen = Color(0xFF002A55),
    patternText = Color(0xFFFFFFFF),
    patternDim = Color(0xFFA9C2E0),
    beatRow = Color(0xFF003B77),
    barRow = Color(0xFF003B77),
    beat = 4,
    bar = 0,
    rowBar = Color(0xFFFF8800),
    rowText = Color(0xFF000022),
    header = Color(0xFFFF8800),
    headerText = Color(0xFF000022),
    scope = Color(0xFFFFFFFF),
    vu = Color(0xFF000022),
    readouts = listOf("Song pos", "Block", "Length", "Tempo", "Speed"),
    exit = "Exit",
    songLabel = "Song:",
    instrumentLabel = "Instr:",
    channelLabel = "Trk",
    buttons = listOf("Prev", "Play", "Pause", "Next"),
    rowDigits = 3,
    cell = ::medCell,
    cellLengths = listOf(10, 6, 3),
    decimalInstruments = false,
)

/** A tracker view drawn in [theme]. */
@Composable
internal fun ClassicTrackerView(
    info: ModuleEngine.ModuleInfo,
    theme: TrackerTheme,
    controls: RetroControls,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = rememberVisual(true)
    val position by remember { derivedStateOf { visual.value?.let { Triple(it.order, it.pattern, it.row) } ?: Triple(0, 0, 0) } }
    val tempo by remember { derivedStateOf { visual.value?.let { it.speed to it.bpm } ?: (0 to 0) } }
    val (order, pattern, row) = position
    val rows = remember(info.generation, pattern) { ModuleEngine.current?.patternRows(pattern) ?: emptyList() }
    val names = info.instruments.ifEmpty { info.samples }
    val instrument = remember(rows, row, names) {
        lastSampleName(rows, row, names, cellRadix = theme.cellRadix, decimal = theme.decimalInstruments)
    }
    val label = TextStyle(fontFamily = pixelFont, fontFeatureSettings = NO_LIGATURES, fontSize = 9.sp, color = theme.label)
    val value = TextStyle(fontFamily = pixelFont, fontFeatureSettings = NO_LIGATURES, fontSize = 10.sp, color = theme.value)
    fun Modifier.panel(sunken: Boolean = false) =
        bevel(sunken = sunken, fill = if (sunken) theme.screen else theme.face, light = theme.light, shadow = theme.shadow)

    RetroLayout(
        modifier = modifier,
        background = theme.face,
        pattern = { place -> ThemedPattern(visual, rows, row, info.channels, theme, place.panel(sunken = true)) },
        controls = {
            val buttonLabel = label.copy(fontSize = 11.sp)
            RetroControlBar(controls) { control, enabled, onClick, slot ->
                val (text, description) = when (control) {
                    RetroControl.PREVIOUS -> theme.buttons[0] to stringResource(R.string.previous)
                    RetroControl.PLAY_PAUSE -> if (controls.isPlaying) {
                        theme.buttons[2] to stringResource(R.string.pause)
                    } else {
                        theme.buttons[1] to stringResource(R.string.play)
                    }
                    RetroControl.NEXT -> theme.buttons[3] to stringResource(R.string.next)
                }
                RetroButton(text, description, enabled, onClick, slot, buttonLabel, fill = theme.face, light = theme.light, shadow = theme.shadow)
            }
        },
    ) { wide ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ThemedReadout(theme.readouts[0], "%03d".format(order), theme, label, value)
                ThemedReadout(theme.readouts[1], "%03d".format(pattern), theme, label, value)
                ThemedReadout(theme.readouts[2], "%03d".format(info.orders), theme, label, value)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ThemedReadout(theme.readouts[3], if (tempo.second > 0) "%03d".format(tempo.second) else "---", theme, label, value)
                ThemedReadout(theme.readouts[4], if (tempo.first > 0) "%03d".format(tempo.first) else "---", theme, label, value)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .panel()
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.retro_close), onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(theme.exit, style = label)
                }
            }
        }
        ThemedScopes(
            visual,
            info.channels,
            theme,
            Modifier.fillMaxWidth().then(if (wide) Modifier.weight(1f) else Modifier.height(80.dp)).panel(sunken = true),
        )
        ThemedNameLine(theme.songLabel, info.title, theme, label, value)
        ThemedNameLine(theme.instrumentLabel, instrument, theme, label, value)
    }
}

@Composable
private fun ThemedReadout(name: String, number: String, theme: TrackerTheme, label: TextStyle, value: TextStyle) {
    Row(
        Modifier.fillMaxWidth().height(26.dp).bevel(fill = theme.face, light = theme.light, shadow = theme.shadow).padding(start = 6.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = label, modifier = Modifier.weight(1f), maxLines = 1)
        Box(
            Modifier.width(44.dp).fillMaxHeight().padding(vertical = 3.dp).bevel(sunken = true, fill = theme.valueBox, light = theme.light, shadow = theme.shadow),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = value)
        }
    }
}

@Composable
private fun ThemedNameLine(name: String, text: String, theme: TrackerTheme, label: TextStyle, value: TextStyle) {
    Row(
        Modifier.fillMaxWidth().height(24.dp).bevel(fill = theme.face, light = theme.light, shadow = theme.shadow).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = label)
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(start = 6.dp, top = 3.dp, bottom = 3.dp)
                .bevel(sunken = true, fill = theme.valueBox, light = theme.light, shadow = theme.shadow)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text, style = value, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
        }
    }
}

/** One scope per channel, up to eight across. */
@Composable
private fun ThemedScopes(visual: State<ModuleEngine.Visual?>, channels: Int, theme: TrackerTheme, modifier: Modifier) {
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
                if (top >= size.height) break
                val middle = top + cellHeight / 2
                drawRect(theme.shadow, Offset(left, top), Size(cellWidth, cellHeight), style = Stroke(1f))
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
                drawPath(path, theme.scope, style = stroke)
            }
        }
    }
}

/**
 * The pattern editor: a header per channel with its number and a level bar, then the rows around the
 * playing one. When the channels do not fit, cells get shorter and the rest can be swiped into view.
 */
@Composable
private fun ThemedPattern(
    visual: State<ModuleEngine.Visual?>,
    rows: List<String>,
    row: Int,
    channels: Int,
    theme: TrackerTheme,
    modifier: Modifier,
) {
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
            val digits = theme.rowDigits
            fun charFor(chars: Int, channelsShown: Int) =
                (size.width - gap * channelsShown - pad * 2 * (channelsShown + 1)) / ((digits + chars * channelsShown) * CHAR_ADVANCE)
            val cellChars = theme.cellLengths.firstOrNull { charFor(it, target) >= minChar } ?: theme.cellLengths.last()
            val charPx = charFor(cellChars, target).coerceIn(minChar, maxChar)
            val numberWidth = digits * charPx + pad * 2
            val cellWidth = cellChars * charPx + pad * 2
            val visibleChannels = ((size.width - numberWidth) / (cellWidth + gap)).toInt().coerceIn(1, count)
            shown = visibleChannels
            stepPx = cellWidth + gap
            if (first + visibleChannels > count) first = (count - visibleChannels).coerceAtLeast(0)
            val channelWidth = (size.width - numberWidth - gap * visibleChannels) / visibleChannels
            val style = TextStyle(fontFamily = pixelFont, fontFeatureSettings = NO_LIGATURES, fontSize = (charPx / density / fontScale).sp)
            val lineHeight = charPx * 1.45f
            val headerHeight = lineHeight * 1.3f
            val textTop = (lineHeight - charPx) / 2

            fun left(column: Int) = if (column == 0) 0f else numberWidth + gap + (column - 1) * (channelWidth + gap)

            val levels = visual.value?.takeIf { it.playing }?.levels
            for (column in 1..visibleChannels) {
                val channel = first + column - 1
                val x = left(column)
                drawRect(theme.header, Offset(x, 0f), Size(channelWidth, headerHeight))
                val name = if (cellChars >= 10) "${theme.channelLabel} ${channel + 1}" else "%02d".format(channel + 1)
                val nameWidth = name.length * charPx
                drawTextIn(measurer, name, Offset(x + pad, textTop), style, theme.headerText)
                val level = levels?.getOrNull(channel)?.coerceIn(0f, 1f) ?: 0f
                val barLeft = x + nameWidth + pad * 3
                val barWidth = (channelWidth - (barLeft - x) - pad) * level
                if (barWidth > 0f) drawRect(theme.vu, Offset(barLeft, headerHeight * 0.3f), Size(barWidth, headerHeight * 0.4f))
            }
            if (first > 0) drawTextIn(measurer, "<", Offset(pad, textTop), style, theme.headerText)
            if (first + visibleChannels < count) {
                drawTextIn(measurer, ">", Offset(charPx + pad, textTop), style, theme.headerText)
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
                val onBar = theme.bar > 0 && index % theme.bar == 0
                val onBeat = theme.beat > 0 && index % theme.beat == 0
                when {
                    current -> drawRect(theme.rowBar, Offset(0f, y), Size(size.width, lineHeight))
                    onBar -> drawRect(theme.barRow, Offset(0f, y), Size(size.width, lineHeight))
                    onBeat -> drawRect(theme.beatRow, Offset(0f, y), Size(size.width, lineHeight))
                }
                val color = when {
                    current -> theme.rowText
                    onBeat || theme.beat == 0 -> theme.patternText
                    else -> theme.patternDim
                }
                val number = index.toString().padStart(digits, '0')
                drawTextIn(measurer, number, Offset(pad, y + textTop), style, color)
                for (column in 1..visibleChannels) {
                    val text = theme.cell(cells.getOrNull(first + column - 1).orEmpty()).take(cellChars)
                    drawTextIn(measurer, text, Offset(left(column) + pad, y + textTop), style, color)
                }
            }
        }
    }
}

/**
 * A cell as Impulse Tracker and Scream Tracker write it: note, instrument in decimal, volume column,
 * effect, with dots where nothing is set - "C-5 01 v40 A0F". Octaves are libopenmpt's, as in IT.
 */
internal fun itCell(cell: String): String {
    val padded = cell.padEnd(14)
    val rawNote = padded.substring(0, 3)
    val note = if (rawNote.trim('.', ' ').isEmpty()) "..." else rawNote
    val rawInstrument = padded.substring(4, 6).trim('.', ' ')
    val instrument = rawInstrument.toIntOrNull(16)?.let { "%02d".format(it % 100) } ?: ".."
    val effect = padded.substring(7, 10).replace(' ', '.')
    val volume = padded.substring(11, 14).replace(' ', '.')
    return "$note $instrument $volume $effect"
}

/**
 * A Spectrum cell from what the AY chip played: note, volume, envelope and its shape, noise period -
 * "C-4 F E0E 1F". The engine writes it in libopenmpt's layout, "C-4 1F E0E v0F".
 */
internal fun zxCell(cell: String): String {
    val padded = cell.padEnd(14)
    val note = padded.substring(0, 3).let { if (it.isBlank()) "..." else it }
    val noise = padded.substring(4, 6).replace(' ', '.')
    val envelope = padded.substring(7, 10).replace(' ', '.')
    val volume = padded.substring(11, 14).let { if (it.startsWith("v")) it.last().toString() else "." }
    return "$note $volume $envelope $noise"
}

/** A cell as HivelyTracker writes it: "C-3 01 C10", and "--- 00 000" for an empty step. */
internal fun hvlCell(cell: String): String {
    val padded = cell.padEnd(10)
    val rawNote = padded.substring(0, 3)
    val note = if (rawNote.trim('.', ' ').isEmpty()) "---" else rawNote
    val instrument = padded.substring(4, 6).let { if (it.trim('.', ' ').isEmpty()) "00" else it }
    val effect = padded.substring(7, 10).let { if (it.trim('.', ' ').isEmpty()) "000" else it }
    return "$note $instrument $effect"
}

/**
 * A cell as OctaMED writes it: note with the octave three lower than libopenmpt (as on the Amiga),
 * instrument in hex and the command - "C-2 01 C40", and "--- 00 000" for an empty step.
 */
internal fun medCell(cell: String): String {
    val padded = cell.padEnd(10)
    val rawNote = padded.substring(0, 3)
    val note = when {
        rawNote.trim('.', ' ').isEmpty() -> "---"
        rawNote[2].isDigit() -> rawNote.substring(0, 2) + ((rawNote[2] - '0') - 3).coerceIn(0, 9)
        else -> rawNote
    }
    val instrument = padded.substring(4, 6).replace('.', '0').replace(' ', '0')
    val effect = padded.substring(7, 10).replace('.', '0').replace(' ', '0')
    return "$note $instrument $effect"
}
