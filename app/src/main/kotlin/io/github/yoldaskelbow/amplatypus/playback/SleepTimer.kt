// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.playback

import android.os.Bundle

/**
 * When playback should stop by itself: after a number of minutes, or when the playing module ends.
 * A timed stop fades the music out over its last [FADE_MS]. Pure logic: the clock comes from outside,
 * so the player drives it and tests can wind it.
 */
class SleepTimer(private val now: () -> Long) {
    /** Clock time at which playback stops, or null when no timed stop is set. */
    var endsAt: Long? = null
        private set

    /** Stop when the playing module ends instead of moving on to the next one. */
    var atEndOfModule: Boolean = false
        private set

    val isSet: Boolean get() = endsAt != null || atEndOfModule

    fun startMinutes(minutes: Int) {
        require(minutes > 0) { "minutes must be positive" }
        endsAt = now() + minutes * 60_000L
        atEndOfModule = false
    }

    fun startEndOfModule() {
        endsAt = null
        atEndOfModule = true
    }

    fun cancel() {
        endsAt = null
        atEndOfModule = false
    }

    fun remainingMs(): Long? = endsAt?.let { (it - now()).coerceAtLeast(0) }

    /** True once the timed stop is due. */
    fun isDue(): Boolean = remainingMs() == 0L

    /** Volume for the fade-out: 1 until the last [FADE_MS], then down to 0. */
    fun volume(): Float {
        val left = remainingMs() ?: return 1f
        return if (left >= FADE_MS) 1f else left.toFloat() / FADE_MS
    }

    companion object {
        const val FADE_MS = 30_000L

        /** Choices offered in the player, in minutes. */
        val CHOICES = listOf(15, 30, 45, 60, 90, 120)

        // Session command and the extras that carry the timer to the UI.
        const val COMMAND = "io.github.yoldaskelbow.amplatypus.SLEEP_TIMER"
        const val ARG_MINUTES = "minutes"
        /** [ARG_MINUTES] value for "when this module ends". */
        const val END_OF_MODULE = -1
        /** [ARG_MINUTES] value that cancels the timer. */
        const val OFF = 0
        const val EXTRA_ENDS_AT_WALL = "sleepEndsAtWallClock"
        const val EXTRA_END_OF_MODULE = "sleepAtEndOfModule"

        fun args(minutes: Int) = Bundle().apply { putInt(ARG_MINUTES, minutes) }
    }
}
