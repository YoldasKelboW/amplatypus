// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import io.github.yoldaskelbow.amplatypus.AmplatypusApplication
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.ui.library.LibraryScreen
import io.github.yoldaskelbow.amplatypus.ui.player.MiniPlayer
import io.github.yoldaskelbow.amplatypus.ui.player.NowPlayingScreen
import io.github.yoldaskelbow.amplatypus.ui.radio.RadioScreen
import io.github.yoldaskelbow.amplatypus.ui.search.SearchScreen
import io.github.yoldaskelbow.amplatypus.ui.settings.SettingsScreen

/** The four top-level areas of the app, mirroring 4champ (search, local, radio, settings). */
enum class TopLevelDestination(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    Search(R.string.nav_search, R.drawable.ic_search),
    Library(R.string.nav_library, R.drawable.ic_library),
    Radio(R.string.nav_radio, R.drawable.ic_radio),
    Settings(R.string.nav_settings, R.drawable.ic_settings),
}

/** The navigation suite types that sit at the bottom of the screen. */
private val BOTTOM_BARS = setOf(
    NavigationSuiteType.NavigationBar,
    NavigationSuiteType.ShortNavigationBarCompact,
    NavigationSuiteType.ShortNavigationBarMedium,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmplatypusApp() {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.Search) }
    val player = (LocalContext.current.applicationContext as AmplatypusApplication).container.player

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    // Picks bottom bar (phone), rail (tablet) or drawer depending on the window size.
    val adaptiveType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    // While the keyboard is up, a bottom bar would only sit hidden under it; dropping it lets the
    // mini player ride on top of the keyboard, so you can see what plays while you search.
    val keyboardOpen = WindowInsets.isImeVisible
    NavigationSuiteScaffold(
        layoutType = if (keyboardOpen && adaptiveType in BOTTOM_BARS) {
            NavigationSuiteType.None
        } else {
            adaptiveType
        },
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = destination == current,
                    onClick = { current = destination },
                    icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f)) {
                when (current) {
                    TopLevelDestination.Search -> SearchScreen()
                    TopLevelDestination.Library -> LibraryScreen()
                    TopLevelDestination.Radio -> RadioScreen()
                    TopLevelDestination.Settings -> SettingsScreen()
                }
            }
            MiniPlayer(player, onOpen = { showPlayer = true }, animate = !showPlayer)
        }
    }

    // The player rises from the mini player and sinks back into it.
    AnimatedVisibility(
        visible = showPlayer,
        enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it } + fadeOut(),
    ) {
        NowPlayingScreen(player, onClose = { showPlayer = false })
    }
}
