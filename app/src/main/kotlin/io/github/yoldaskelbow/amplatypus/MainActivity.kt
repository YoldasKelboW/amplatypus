// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yoldaskelbow.amplatypus.ui.AmplatypusApp
import io.github.yoldaskelbow.amplatypus.ui.theme.AmplatypusTheme

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
    }
}
