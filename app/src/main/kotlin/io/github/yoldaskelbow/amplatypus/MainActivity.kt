// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.yoldaskelbow.amplatypus.playback.LibraryBrowser
import io.github.yoldaskelbow.amplatypus.ui.AmplatypusApp
import io.github.yoldaskelbow.amplatypus.ui.theme.AmplatypusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = (application as AmplatypusApplication).container.settings
        setContent {
            val design by settings.design.collectAsStateWithLifecycle()
            val darkMode by settings.darkMode.collectAsStateWithLifecycle()
            AmplatypusTheme(design, darkMode) {
                AmplatypusApp()
            }
        }
        if (savedInstanceState == null) playFromSearch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        playFromSearch(intent)
    }

    /** "Play ... on Amplatypus": plays the library's modules that match, as Android Auto does. */
    private fun playFromSearch(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        val container = (application as AmplatypusApplication).container
        lifecycleScope.launch {
            val found = LibraryBrowser(this@MainActivity, container.library).search(query)
            if (found.isNotEmpty()) container.player.play(found, 0)
        }
    }
}
