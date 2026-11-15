// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The colour designs the user can pick. [DYNAMIC] follows the wallpaper (Android 12 and newer). */
enum class Design { DYNAMIC, PAULA, TRACKER, WORKBENCH, COPPER }

enum class DarkMode { SYSTEM, LIGHT, DARK }

/** User settings, kept in shared preferences and exposed as flows so the UI follows changes at once. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _design = MutableStateFlow(read(KEY_DESIGN, Design.DYNAMIC))
    val design: StateFlow<Design> = _design

    private val _darkMode = MutableStateFlow(read(KEY_DARK, DarkMode.SYSTEM))
    val darkMode: StateFlow<DarkMode> = _darkMode

    fun setDesign(design: Design) {
        _design.value = design
        prefs.edit().putString(KEY_DESIGN, design.name).apply()
    }

    fun setDarkMode(mode: DarkMode) {
        _darkMode.value = mode
        prefs.edit().putString(KEY_DARK, mode.name).apply()
    }

    private inline fun <reified T : Enum<T>> read(key: String, default: T): T =
        prefs.getString(key, null)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    private companion object {
        const val KEY_DESIGN = "design"
        const val KEY_DARK = "dark_mode"
    }
}
