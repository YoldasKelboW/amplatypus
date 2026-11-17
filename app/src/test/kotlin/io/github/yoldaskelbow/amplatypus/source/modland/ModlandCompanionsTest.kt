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
}
