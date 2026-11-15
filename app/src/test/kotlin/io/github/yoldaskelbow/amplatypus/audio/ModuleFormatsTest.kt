// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleFormatsTest {
    @Test
    fun `tracker formats are playable whatever the case`() {
        assertTrue(ModuleFormats.isPlayable("mod"))
        assertTrue(ModuleFormats.isPlayable("XM"))
    }

    @Test
    fun `AHX and HivelyTracker are playable`() {
        assertTrue(ModuleFormats.isPlayable("ahx"))
        assertTrue(ModuleFormats.isPlayable("hvl"))
    }

    @Test
    fun `formats without a bundled replayer are not playable`() {
        assertFalse(ModuleFormats.isPlayable("sid"))
        assertFalse(ModuleFormats.isPlayable("dss"))
        assertFalse(ModuleFormats.isPlayable(""))
    }
}
