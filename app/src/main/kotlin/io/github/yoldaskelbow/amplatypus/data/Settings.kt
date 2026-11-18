// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.data

import android.content.Context
import io.github.yoldaskelbow.amplatypus.audio.FormatFilter
import io.github.yoldaskelbow.amplatypus.audio.FormatGroup
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

    private val _formatFilter = MutableStateFlow(
        FormatFilter(
            tracker = prefs.getBoolean(KEY_SHOW_TRACKER, true),
            hively = prefs.getBoolean(KEY_SHOW_HIVELY, true),
            amiga = prefs.getBoolean(KEY_SHOW_AMIGA, true),
            c64 = prefs.getBoolean(KEY_SHOW_C64, true),
        ),
    )

    /** Which format groups search, browsing, random picks and the radio show. */
    val formatFilter: StateFlow<FormatFilter> = _formatFilter

    fun setFormatShown(group: FormatGroup, shown: Boolean) {
        val next = _formatFilter.value.with(group, shown)
        _formatFilter.value = next
        prefs.edit()
            .putBoolean(KEY_SHOW_TRACKER, next.tracker)
            .putBoolean(KEY_SHOW_HIVELY, next.hively)
            .putBoolean(KEY_SHOW_AMIGA, next.amiga)
            .putBoolean(KEY_SHOW_C64, next.c64)
            .apply()
    }

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
        const val KEY_SHOW_TRACKER = "show_tracker"
        const val KEY_SHOW_HIVELY = "show_hively"
        const val KEY_SHOW_AMIGA = "show_amiga"
        const val KEY_SHOW_C64 = "show_c64"
    }
}
