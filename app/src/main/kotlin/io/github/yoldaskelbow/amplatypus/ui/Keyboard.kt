// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Closes the on-screen keyboard and takes the focus off the text field. Clearing the focus alone leaves
 * the keyboard open on some devices.
 */
@Composable
fun rememberHideKeyboard(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    return {
        keyboard?.hide()
        focus.clearFocus()
    }
}

/**
 * Closes the keyboard when the user touches this area, to tap or to scroll. The touch itself goes on to
 * what lies beneath, so a tapped row still opens.
 */
fun Modifier.hideKeyboardOnTouch(hide: () -> Unit): Modifier = pointerInput(hide) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        hide()
    }
}

/** Closes the keyboard once, when this screen appears. */
@Composable
fun HideKeyboardOnEnter() {
    val hide = rememberHideKeyboard()
    LaunchedEffect(Unit) { hide() }
}
