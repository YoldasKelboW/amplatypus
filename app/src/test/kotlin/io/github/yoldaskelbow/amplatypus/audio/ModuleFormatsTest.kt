// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.audio

import org.junit.Assert.assertEquals
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
        assertFalse(ModuleFormats.isPlayable("minigsf"))
        assertFalse(ModuleFormats.isPlayable("fxm"))
        assertFalse(ModuleFormats.isPlayable(""))
    }

    @Test
    fun `console and home computer music is playable and grouped`() {
        for (ext in listOf("nsf", "NSFE", "spc", "gbs", "vgm", "vgz", "gym", "hes", "kss", "sap", "ay", "emul", "sndh", "sc68", "ym",
            "pt3", "PT2", "stc", "asc", "sqt", "vtx")) {
            assertTrue(ext, ModuleFormats.isPlayable(ext))
            assertEquals(ext, FormatGroup.CONSOLE, ModuleFormats.groupOf(ext))
        }
    }

    @Test
    fun `console music on Modland comes from its own folders`() {
        assertTrue(ModuleFormats.isPlayableOnModland("Nintendo SPC", "spc"))
        assertTrue(ModuleFormats.isPlayableOnModland("Video Game Music", "vgz", "Video Game Music/Sega Megadrive/Yuzo Koshiro/Bare Knuckle/01.vgz"))
        // Systems whose sound chips Game Music Emu lacks.
        assertFalse(ModuleFormats.isPlayableOnModland("Video Game Music", "vgz", "Video Game Music/Sega 32X/X/Y/01.vgz"))
        assertTrue(ModuleFormats.isPlayableOnModland("AY Emul", "emul"))
        assertTrue(ModuleFormats.isPlayableOnModland("Slight Atari Player", "sap"))
        assertTrue(ModuleFormats.isPlayableOnModland("SNDH", "sndh"))
        assertTrue(ModuleFormats.isPlayableOnModland("YM", "ym"))
        assertTrue(ModuleFormats.isPlayableOnModland("Spectrum", "pt3"))
        assertTrue(ModuleFormats.isPlayableOnModland("Spectrum", "stp"))
        // Spectrum formats ZXTune does not read, and another machine's format with a Spectrum extension.
        assertFalse(ModuleFormats.isPlayableOnModland("Spectrum", "fxm"))
        assertFalse(ModuleFormats.isPlayableOnModland("Spectrum", "chi"))
        assertFalse(ModuleFormats.isPlayableOnModland("Quartet PSG", "sqt"))
        // Amiga replayers for YM-ST files are another format.
        assertFalse(ModuleFormats.isPlayableOnModland("YMST", "ymst"))
        // Same machines, other emulators the app does not bundle.
        assertFalse(ModuleFormats.isPlayableOnModland("Gameboy Sound Format", "minigsf"))
        assertFalse(ModuleFormats.isPlayableOnModland("Super Nintendo Sound Format", "minisnsf"))
    }

    @Test
    fun `hiding the console group keeps its extensions out of searches`() {
        val filter = FormatFilter().with(FormatGroup.CONSOLE, false)
        assertFalse(filter.shows(FormatGroup.CONSOLE))
        assertFalse("nsf" in filter.shownExtensions())
        assertTrue("nsf" in FormatFilter().shownExtensions())
    }

    @Test
    fun `PreTracker is played by UADE in every build`() {
        assertTrue(ModuleFormats.isPlayable("prt"))
        assertTrue(ModuleFormats.isPlayableOnModland("Pretracker", "prt"))
    }

    @Test
    fun `Modland folders of other machines are not taken for Amiga formats`() {
        // "mus" is one of UADE's extensions, but this folder holds Commodore 64 music, played as such.
        assertTrue(ModuleFormats.isPlayableOnModland("Sidplayer", "mus"))
        assertEquals(FormatGroup.C64, ModuleFormats.groupOf("sid"))
        assertFalse(ModuleFormats.isPlayableOnModland("FamiTracker", "ftm"))
        assertFalse(ModuleFormats.isPlayableOnModland("Playstation Sound Format", "psf"))
    }

    @Test
    fun `tracker formats on Modland do not depend on the folder`() {
        assertTrue(ModuleFormats.isPlayableOnModland("Protracker", "mod"))
        assertTrue(ModuleFormats.isPlayableOnModland("AHX", "ahx"))
    }
}
