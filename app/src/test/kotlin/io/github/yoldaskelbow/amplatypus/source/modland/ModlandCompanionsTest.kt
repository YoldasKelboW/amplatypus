// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.source.modland

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModlandCompanionsTest {
    @Test
    fun `TFMX songs bring their sample file`() {
        val companions = ModlandCompanions.companionsOf("TFMX/Chris Huelsbeck/mdat.apidya (end)")
        assertEquals(listOf(ModlandCompanions.Companion("TFMX/Chris Huelsbeck/smpl.apidya (end)", false)), companions)
    }

    @Test
    fun `sample files are not listed on their own`() {
        assertTrue(ModlandCompanions.isCompanion("TFMX", "TFMX/Chris Huelsbeck/smpl.apidya (end)"))
        assertTrue(ModlandCompanions.isCompanion("Richard Joseph", "Richard Joseph/Richard Joseph/blob.ins"))
        assertTrue(ModlandCompanions.isCompanion("Audio Sculpture", "Audio Sculpture/Darren Ithell/daisy.adsc.as"))
        assertFalse(ModlandCompanions.isCompanion("TFMX", "TFMX/Chris Huelsbeck/mdat.apidya (end)"))
    }

    @Test
    fun `Richard Joseph and Audio Sculpture need their companion`() {
        assertEquals(
            listOf(ModlandCompanions.Companion("Richard Joseph/Richard Joseph/blob.ins", true)),
            ModlandCompanions.companionsOf("Richard Joseph/Richard Joseph/blob.sng"),
        )
        assertEquals(
            listOf(ModlandCompanions.Companion("Audio Sculpture/Darren Ithell/daisy.adsc.as", true)),
            ModlandCompanions.companionsOf("Audio Sculpture/Darren Ithell/daisy.adsc"),
        )
    }

    @Test
    fun `other formats have no companions`() {
        assertTrue(ModlandCompanions.companionsOf("Protracker/4-Mat/1978.mod").isEmpty())
        // The same extension in another folder is not a companion.
        assertFalse(ModlandCompanions.isCompanion("Ad Lib", "Ad Lib/Someone/song.ins"))
    }

    @Test
    fun `IFF-SMUS songs bring their instrument folder`() {
        val files = listOf(
            "IFF-SMUS/Allister Brimble/ACE II/Instruments/hihat.instr",
            "IFF-SMUS/Allister Brimble/ACE II/Instruments/hihat.ss",
            "IFF-SMUS/Allister Brimble/creation3/instruments/Bass1.MS6.instr",
        )
        val companions = ModlandCompanions.folderCompanionsOf("IFF-SMUS/Allister Brimble/ACE II/ACE II.smus", files)
        assertEquals(files.take(2), companions.map { it.path })
        assertTrue(companions.none { it.required })
        assertTrue(ModlandCompanions.isCompanion("IFF-SMUS", files[0]))
        assertFalse(ModlandCompanions.isCompanion("IFF-SMUS", "IFF-SMUS/Allister Brimble/ACE II/ACE II.smus"))
        // Other formats have no instrument folder.
        assertTrue(ModlandCompanions.folderCompanionsOf("Protracker/4-Mat/1978.mod", files).isEmpty())
    }

    @Test
    fun `Stereo Sidplayer songs bring their second-SID file`() {
        val companions = ModlandCompanions.companionsOf("Stereo Sidplayer/Andrew Hackard/jaws.mus")
        assertEquals(listOf("Stereo Sidplayer/Andrew Hackard/jaws.str"), companions.map { it.path })
        assertTrue(ModlandCompanions.isCompanion("Stereo Sidplayer", "Stereo Sidplayer/Andrew Hackard/jaws.str"))
    }
}
