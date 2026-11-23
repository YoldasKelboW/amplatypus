// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.playback.NowPlaying
import io.github.yoldaskelbow.amplatypus.playback.SleepTimer
import io.github.yoldaskelbow.amplatypus.ui.formatTime
import kotlinx.coroutines.delay

/**
 * The sleep timer in the player's header: a moon, and while a timer runs, the time left.
 * Tapping it opens the choices.
 */
@Composable
fun SleepTimerButton(nowPlaying: NowPlaying, onSet: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var leftMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(nowPlaying.sleepEndsAt) {
        while (nowPlaying.sleepEndsAt > 0) {
            leftMs = (nowPlaying.sleepEndsAt - System.currentTimeMillis()).coerceAtLeast(0)
            delay(1_000)
        }
    }
    val active = nowPlaying.sleepTimerSet
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val label = when {
        nowPlaying.sleepAtEndOfModule -> stringResource(R.string.sleep_timer_end_of_module_short)
        nowPlaying.sleepEndsAt > 0 -> formatTime(leftMs + 999)
        else -> null
    }
    val description = when {
        nowPlaying.sleepAtEndOfModule -> stringResource(R.string.sleep_timer_active_end_of_module)
        nowPlaying.sleepEndsAt > 0 -> stringResource(R.string.sleep_timer_active, formatTime(leftMs + 999))
        else -> stringResource(R.string.sleep_timer)
    }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = description }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_bedtime), null, tint = tint)
                if (label != null) Text(" $label", color = tint, style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            SleepTimer.CHOICES.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(pluralStringResource(R.plurals.sleep_timer_minutes, minutes, minutes)) },
                    onClick = { open = false; onSet(minutes) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sleep_timer_end_of_module)) },
                onClick = { open = false; onSet(SleepTimer.END_OF_MODULE) },
            )
            if (active) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_timer_off)) },
                    onClick = { open = false; onSet(SleepTimer.OFF) },
                )
            }
        }
    }
}
