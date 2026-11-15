// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yoldaskelbow.amplatypus.AmplatypusApplication
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.data.DarkMode
import io.github.yoldaskelbow.amplatypus.data.Design
import io.github.yoldaskelbow.amplatypus.ui.theme.designColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val settings = (LocalContext.current.applicationContext as AmplatypusApplication).container.settings
    val design by settings.design.collectAsStateWithLifecycle()
    val darkMode by settings.darkMode.collectAsStateWithLifecycle()
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_settings)) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle(R.string.settings_design)
            Column(Modifier.selectableGroup()) {
                Design.entries.forEach { option ->
                    // Dynamic colour needs Android 12; on older versions the entry would do nothing.
                    if (option == Design.DYNAMIC && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@forEach
                    val scheme = designColorScheme(option, dark)
                    ListItem(
                        headlineContent = { Text(stringResource(option.label())) },
                        supportingContent = { Text(stringResource(option.description())) },
                        leadingContent = { RadioButton(selected = option == design, onClick = null) },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                listOf(scheme.primary, scheme.secondaryContainer, scheme.tertiary).forEach { color ->
                                    Box(Modifier.size(24.dp).clip(CircleShape).background(color))
                                }
                            }
                        },
                        modifier = Modifier.selectable(
                            selected = option == design,
                            role = Role.RadioButton,
                            onClick = { settings.setDesign(option) },
                        ),
                    )
                }
            }

            // Choosing a language per app is an Android 13 feature; older versions follow the system language.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SectionTitle(R.string.settings_language)
                LanguageChooser()
            }

            SectionTitle(R.string.settings_dark_mode)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                DarkMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == darkMode,
                        onClick = { settings.setDarkMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, DarkMode.entries.size),
                    ) {
                        Text(
                            stringResource(
                                when (mode) {
                                    DarkMode.SYSTEM -> R.string.dark_system
                                    DarkMode.LIGHT -> R.string.dark_light
                                    DarkMode.DARK -> R.string.dark_dark
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Sets the app's language through the system, which remembers it and restarts the screen. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageChooser() {
    val localeManager = LocalContext.current.getSystemService(LocaleManager::class.java)
    var current by remember { mutableStateOf(localeManager.applicationLocales.toLanguageTags().substringBefore('-')) }
    val options = listOf("" to stringResource(R.string.dark_system), "en" to "English", "de" to "Deutsch")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { index, (tag, label) ->
            SegmentedButton(
                selected = current == tag,
                onClick = {
                    current = tag
                    localeManager.applicationLocales =
                        if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

private fun Design.label(): Int = when (this) {
    Design.DYNAMIC -> R.string.design_dynamic
    Design.PAULA -> R.string.design_paula
    Design.TRACKER -> R.string.design_tracker
    Design.WORKBENCH -> R.string.design_workbench
    Design.COPPER -> R.string.design_copper
}

private fun Design.description(): Int = when (this) {
    Design.DYNAMIC -> R.string.design_dynamic_text
    Design.PAULA -> R.string.design_paula_text
    Design.TRACKER -> R.string.design_tracker_text
    Design.WORKBENCH -> R.string.design_workbench_text
    Design.COPPER -> R.string.design_copper_text
}
