// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.audio.ModuleEngine
import io.github.yoldaskelbow.amplatypus.ui.formatTime

/*
 * The view for the formats UADE plays, in the style of the Amiga module players of the time
 * (DeliTracker, EaglePlayer) on the grey Workbench 2 desktop: these replay routines have no pattern to
 * show, so the window holds what the player knows - module, author, replay routine, time - and one
 * large scope with a level bar per Paula channel.
 */

private object Wb {
    // The Workbench 2 palette: grey, black, white and the selection blue.
    val face = Color(0xFFAAAAAA)
    val light = Color(0xFFFFFFFF)
    val shadow = Color(0xFF000000)
    val blue = Color(0xFF6688BB)
    val text = Color(0xFF000000)
    val screen = Color(0xFF000000)
    val scope = Color(0xFFFFFFFF)
    val value = Color(0xFF000000)
}

@Composable
internal fun DeliTrackerView(info: ModuleEngine.ModuleInfo, controls: RetroControls, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val visual = rememberVisual(true)
    val label = TextStyle(fontFamily = pixelFont, fontSize = 9.sp, color = Wb.text)
    val value = TextStyle(fontFamily = pixelFont, fontSize = 10.sp, color = Wb.value)
    val title = info.title.ifBlank { controls.title }
    val length = if (controls.durationMs > 0) formatTime(controls.durationMs) else "--:--"

    RetroLayout(
        modifier = modifier,
        background = Wb.face,
        pattern = { place -> PaulaChannels(visual, info.channels, place) },
        controls = {
            val buttonLabel = label.copy(fontSize = 11.sp)
            RetroControlBar(controls) { control, enabled, onClick, slot ->
                val (text, description) = when (control) {
                    RetroControl.PREVIOUS -> "Prev" to stringResource(R.string.previous)
                    RetroControl.PLAY_PAUSE -> if (controls.isPlaying) {
                        "Pause" to stringResource(R.string.pause)
                    } else {
                        "Play" to stringResource(R.string.play)
                    }
                    RetroControl.NEXT -> "Next" to stringResource(R.string.next)
                }
                RetroButton(text, description, enabled, onClick, slot, buttonLabel, fill = Wb.face, light = Wb.light, shadow = Wb.shadow)
            }
        },
    ) { _ ->
        // The title bar of a Workbench window.
        Row(
            Modifier.fillMaxWidth().height(24.dp).bevel(fill = Wb.blue, light = Wb.light, shadow = Wb.shadow).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("AMPlatypus", style = label.copy(color = Wb.light), modifier = Modifier.weight(1f), maxLines = 1)
            Box(
                Modifier.width(48.dp).fillMaxHeight().padding(vertical = 3.dp)
                    .bevel(fill = Wb.face, light = Wb.light, shadow = Wb.shadow)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.retro_close), onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("Exit", style = label)
            }
        }
        InfoLine("Module", title, label, value)
        InfoLine("Author", controls.composer, label, value)
        InfoLine("Player", info.tracker, label, value)
        InfoLine("Format", info.type, label, value)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.weight(1f)) { InfoLine("Time", formatTime(controls.positionMs), label, value) }
            Box(Modifier.weight(1f)) { InfoLine("Length", length, label, value) }
        }
    }
}

@Composable
private fun InfoLine(name: String, text: String, label: TextStyle, value: TextStyle) {
    Row(
        Modifier.fillMaxWidth().height(26.dp).bevel(fill = Wb.face, light = Wb.light, shadow = Wb.shadow).padding(start = 6.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = label, modifier = Modifier.width(62.dp), maxLines = 1)
        // A string gadget: sunken, white, black text.
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(vertical = 3.dp)
                .bevel(sunken = true, fill = Wb.light, light = Wb.light, shadow = Wb.shadow)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text.ifBlank { "-" }, style = value, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
        }
    }
}

/** One row per Paula channel: its number, a scope across the window and a level bar. */
@Composable
private fun PaulaChannels(visual: State<ModuleEngine.Visual?>, channels: Int, modifier: Modifier) {
    val path = remember { Path() }
    val measurer = rememberTextMeasurer()
    Column(modifier.bevel(sunken = true, fill = Wb.screen, light = Wb.light, shadow = Wb.shadow).padding(4.dp)) {
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val count = channels.coerceAtLeast(1)
            val gap = 4.dp.toPx()
            val rowHeight = (size.height - gap * (count - 1)) / count
            val charPx = 10.dp.toPx()
            val style = TextStyle(fontFamily = pixelFont, fontSize = (charPx / density / fontScale).sp)
            val labelWidth = charPx * 2 + 6.dp.toPx()
            val barWidth = 14.dp.toPx()
            val scopeLeft = labelWidth
            val scopeWidth = size.width - labelWidth - barWidth - 6.dp.toPx()
            val current = visual.value?.takeIf { it.playing }
            val waves = current?.waves
            val levels = current?.levels
            val points = ModuleEngine.SCOPE_POINTS
            val stroke = Stroke(width = 1.5.dp.toPx())
            if (channels < 1) {
                drawTextIn(measurer, "No channel data", Offset(4.dp.toPx(), size.height / 2 - charPx / 2), style, Wb.scope)
                return@Canvas
            }
            for (channel in 0 until count) {
                val top = channel * (rowHeight + gap)
                val middle = top + rowHeight / 2
                drawTextIn(measurer, "${channel + 1}", Offset(4.dp.toPx(), middle - charPx / 2), style, Wb.blue)
                drawRect(Wb.blue.copy(alpha = 0.6f), Offset(scopeLeft, top), Size(scopeWidth, rowHeight), style = Stroke(1f))
                path.rewind()
                for (i in 0 until points) {
                    val sample = if (waves != null && waves.size >= (channel + 1) * points) {
                        (waves[channel * points + i] * SCOPE_GAIN).coerceIn(-1f, 1f)
                    } else {
                        0f
                    }
                    val x = scopeLeft + scopeWidth * i / (points - 1)
                    val y = middle - sample * (rowHeight / 2 - 3.dp.toPx())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Wb.scope, style = stroke)
                val level = levels?.getOrNull(channel)?.coerceIn(0f, 1f) ?: 0f
                val barLeft = size.width - barWidth
                drawRect(Wb.face.copy(alpha = 0.25f), Offset(barLeft, top), Size(barWidth, rowHeight))
                val height = rowHeight * level
                if (height > 0f) drawRect(Wb.blue, Offset(barLeft, top + rowHeight - height), Size(barWidth, height))
            }
        }
    }
}
