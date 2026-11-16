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
        assertFalse(ModuleFormats.isPlayable("spc"))
        assertFalse(ModuleFormats.isPlayable("nsf"))
        assertFalse(ModuleFormats.isPlayable(""))
    }

    @Test
    fun `PreTracker is played by UADE in every build`() {
        assertTrue(ModuleFormats.isPlayable("prt"))
        assertTrue(ModuleFormats.isPlayableOnModland("Pretracker", "prt"))
    }

    @Test
    fun `Modland folders of other machines are not taken for Amiga formats`() {
        // "mus" is one of UADE's extensions, but these folders hold Commodore 64 music.
        assertFalse(ModuleFormats.isPlayableOnModland("Sidplayer", "mus"))
        assertFalse(ModuleFormats.isPlayableOnModland("FamiTracker", "ftm"))
        assertFalse(ModuleFormats.isPlayableOnModland("Playstation Sound Format", "psf"))
    }

    @Test
    fun `tracker formats on Modland do not depend on the folder`() {
        assertTrue(ModuleFormats.isPlayableOnModland("Protracker", "mod"))
        assertTrue(ModuleFormats.isPlayableOnModland("AHX", "ahx"))
    }
}
