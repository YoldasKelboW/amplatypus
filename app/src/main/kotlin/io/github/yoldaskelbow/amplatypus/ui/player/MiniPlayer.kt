// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.audio.ModuleEngine
import io.github.yoldaskelbow.amplatypus.playback.PlayerConnection
import kotlinx.coroutines.delay

/** The bar above the navigation that shows what is playing. Tapping it opens the full player. */
@Composable
fun MiniPlayer(player: PlayerConnection, onOpen: () -> Unit, modifier: Modifier = Modifier, animate: Boolean = true) {
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    if (!nowPlaying.isActive) return

    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(nowPlaying.isPlaying, nowPlaying.title) {
        positionMs = player.positionMs
        while (nowPlaying.isPlaying) {
            delay(500)
            positionMs = player.positionMs
        }
    }
    // No need to animate the small meters while the full player covers them.
    val visual = rememberVisual(animate)
    val channels = remember(nowPlaying.title, nowPlaying.durationMs) { ModuleEngine.current?.moduleInfo()?.channels?.takeIf { it > 0 } ?: 4 }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column {
            LinearProgressIndicator(
                progress = {
                    if (nowPlaying.durationMs <= 0) 0f else (positionMs.toFloat() / nowPlaying.durationMs).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                ChannelMeters(visual, channels.coerceAtMost(8), Modifier.size(width = 40.dp, height = 36.dp), segments = 8)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(nowPlaying.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        nowPlaying.composer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = player::togglePlayPause) {
                    Icon(
                        painterResource(if (nowPlaying.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        stringResource(if (nowPlaying.isPlaying) R.string.pause else R.string.play),
                    )
                }
                IconButton(onClick = player::next, enabled = nowPlaying.hasNext) {
                    Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.next))
                }
            }
        }
    }
}
