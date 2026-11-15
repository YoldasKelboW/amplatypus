// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.yoldaskelbow.amplatypus.data.DarkMode
import io.github.yoldaskelbow.amplatypus.data.Design

/**
 * The hues (0–360) a fixed design is built from. The full Material 3 colour roles are derived from
 * them by lightness, the same way Material's tonal palettes work.
 */
private class Palette(
    val primary: Float,
    val secondary: Float,
    val tertiary: Float,
    val saturation: Float = 0.55f,
    val neutralSaturation: Float = 0.08f,
)

private val palettes = mapOf(
    // Teal, the app's own colour.
    Design.PAULA to Palette(primary = 190f, secondary = 200f, tertiary = 255f),
    // The grey-blue panels and amber accents of classic Amiga trackers.
    Design.TRACKER to Palette(primary = 215f, secondary = 220f, tertiary = 40f, saturation = 0.35f, neutralSaturation = 0.14f),
    // Workbench 1.x: blue and orange.
    Design.WORKBENCH to Palette(primary = 212f, secondary = 28f, tertiary = 28f, saturation = 0.85f, neutralSaturation = 0.25f),
    // Copper bars: warm orange into magenta.
    Design.COPPER to Palette(primary = 22f, secondary = 345f, tertiary = 300f, saturation = 0.7f, neutralSaturation = 0.1f),
)

private fun tone(hue: Float, saturation: Float, lightness: Int) = Color.hsl(hue, saturation, lightness / 100f)

private fun Palette.scheme(dark: Boolean): ColorScheme {
    fun p(l: Int) = tone(primary, saturation, l)
    fun s(l: Int) = tone(secondary, saturation * 0.6f, l)
    fun t(l: Int) = tone(tertiary, saturation, l)
    fun n(l: Int) = tone(primary, neutralSaturation, l)
    return if (dark) {
        darkColorScheme(
            primary = p(76), onPrimary = p(16), primaryContainer = p(28), onPrimaryContainer = p(90),
            secondary = s(76), onSecondary = s(16), secondaryContainer = s(26), onSecondaryContainer = s(90),
            tertiary = t(74), onTertiary = t(16), tertiaryContainer = t(28), onTertiaryContainer = t(90),
            background = n(7), onBackground = n(90), surface = n(7), onSurface = n(90),
            surfaceVariant = n(26), onSurfaceVariant = n(78), outline = n(56), outlineVariant = n(28),
            surfaceDim = n(7), surfaceBright = n(24),
            surfaceContainerLowest = n(4), surfaceContainerLow = n(10), surfaceContainer = n(12),
            surfaceContainerHigh = n(16), surfaceContainerHighest = n(21),
            inverseSurface = n(90), inverseOnSurface = n(18), inversePrimary = p(38),
        )
    } else {
        lightColorScheme(
            primary = p(36), onPrimary = Color.White, primaryContainer = p(88), onPrimaryContainer = p(14),
            secondary = s(38), onSecondary = Color.White, secondaryContainer = s(88), onSecondaryContainer = s(14),
            tertiary = t(38), onTertiary = Color.White, tertiaryContainer = t(88), onTertiaryContainer = t(14),
            background = n(98), onBackground = n(10), surface = n(98), onSurface = n(10),
            surfaceVariant = n(89), onSurfaceVariant = n(30), outline = n(48), outlineVariant = n(80),
            surfaceDim = n(86), surfaceBright = n(98),
            surfaceContainerLowest = Color.White, surfaceContainerLow = n(96), surfaceContainer = n(94),
            surfaceContainerHigh = n(91), surfaceContainerHighest = n(88),
            inverseSurface = n(18), inverseOnSurface = n(94), inversePrimary = p(76),
        )
    }
}

/** The colour scheme of [design]; used by the theme and by the previews in the settings. */
@Composable
fun designColorScheme(design: Design, dark: Boolean): ColorScheme {
    if (design == Design.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    // Before Android 12 there is no dynamic colour; the app's own design stands in.
    return (palettes[design] ?: palettes.getValue(Design.PAULA)).scheme(dark)
}

@Composable
fun AmplatypusTheme(
    design: Design = Design.DYNAMIC,
    darkMode: DarkMode = DarkMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    MaterialTheme(colorScheme = designColorScheme(design, dark), content = content)
}
