// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {
    private var clock = 1_000_000L
    private val timer = SleepTimer { clock }

    @Test
    fun countsDownAndFiresAfterTheMinutes() {
        timer.startMinutes(15)
        assertTrue(timer.isSet)
        assertEquals(15 * 60_000L, timer.remainingMs())
        clock += 14 * 60_000L
        assertFalse(timer.isDue())
        clock += 60_000L
        assertTrue(timer.isDue())
        clock += 5_000L
        assertEquals(0L, timer.remainingMs()) // never negative
    }

    @Test
    fun fadesOutOverTheLastThirtySeconds() {
        timer.startMinutes(1)
        assertEquals(1f, timer.volume(), 0f)
        clock += 30_000L
        assertEquals(1f, timer.volume(), 0.001f)
        clock += 15_000L
        assertEquals(0.5f, timer.volume(), 0.001f)
        clock += 15_000L
        assertEquals(0f, timer.volume(), 0.001f)
    }

    @Test
    fun endOfModuleHasNoCountdownAndKeepsFullVolume() {
        timer.startEndOfModule()
        assertTrue(timer.isSet)
        assertTrue(timer.atEndOfModule)
        assertNull(timer.remainingMs())
        assertFalse(timer.isDue())
        assertEquals(1f, timer.volume(), 0f)
    }

    @Test
    fun choosingAgainReplacesTheTimer() {
        timer.startEndOfModule()
        timer.startMinutes(30)
        assertFalse(timer.atEndOfModule)
        assertEquals(30 * 60_000L, timer.remainingMs())
        timer.startEndOfModule()
        assertNull(timer.endsAt)
    }

    @Test
    fun cancelClearsEverything() {
        timer.startMinutes(45)
        clock += 44 * 60_000L
        timer.cancel()
        assertFalse(timer.isSet)
        assertNull(timer.remainingMs())
        assertEquals(1f, timer.volume(), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroMinutes() = timer.startMinutes(0)
}
