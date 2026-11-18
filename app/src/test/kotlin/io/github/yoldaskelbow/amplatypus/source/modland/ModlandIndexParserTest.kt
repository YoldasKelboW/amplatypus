// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.source.modland

import io.github.yoldaskelbow.amplatypus.audio.ModuleFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModlandIndexParserTest {
    private fun sampleLines() = javaClass.getResource("/modland/allmods_sample.txt")!!.readText().lines()

    @Test
    fun parsesPlainModule() {
        val line = ModlandIndexParser.parse("146528\tProtracker/4-Mat/06-11-90 crosswind.mod")!!
        assertEquals("Protracker", line.format)
        assertEquals("4-Mat", line.composer)
        assertEquals("06-11-90 crosswind", line.title)
        assertEquals("mod", line.ext)
        assertEquals(146528L, line.sizeBytes)
        assertEquals("Protracker/4-Mat/06-11-90 crosswind.mod", line.path)
    }

    @Test
    fun readsAmigaStylePrefixes() {
        val line = ModlandIndexParser.parse("43090\tTFMX/Chris Huelsbeck/mdat.apidya (end)")!!
        assertEquals("TFMX", line.format)
        assertEquals("apidya (end)", line.title)
        assertEquals("mdat", line.ext)
    }

    @Test
    fun keepsSubFoldersOutOfTheTitle() {
        val line = ModlandIndexParser.parse("4905\tAd Lib/EdLib D00/Drax/coop-Metal/breaking wind.d00")!!
        assertEquals("Ad Lib", line.format)
        assertEquals("EdLib D00", line.composer)
        assertEquals("breaking wind", line.title)
    }

    @Test
    fun titleMayContainDots() {
        val line = ModlandIndexParser.parse("102\tGameboy Sound Format/_unknown/Poke Inu/2108.000d.minigsf")!!
        assertEquals("2108.000d", line.title)
        assertEquals("minigsf", line.ext)
    }

    @Test
    fun rejectsMalformedLines() {
        assertNull(ModlandIndexParser.parse(""))
        assertNull(ModlandIndexParser.parse("no tab here"))
        assertNull(ModlandIndexParser.parse("abc\tProtracker/4-Mat/x.mod"))
        assertNull(ModlandIndexParser.parse("161652\tAshley Hogg/ash.cj in the usa"))
        assertNull(ModlandIndexParser.parse("10\tProtracker/4-Mat/noextension"))
    }

    @Test
    fun sampleFileParsesAndFiltersToPlayableFormats() {
        val parsed = sampleLines().mapNotNull { ModlandIndexParser.parse(it) }
        assertTrue(parsed.size >= 8)
        val playable = parsed.filter { ModuleFormats.isPlayable(it.ext) }
        assertTrue(playable.any { it.ext == "mod" })
        assertTrue(playable.any { it.ext == "it" })
        assertFalse(playable.any { it.ext == "minigsf" || it.ext == "am" })
    }

    @Test
    fun buildsPrefixQueries() {
        assertEquals("purple* mot*", ModlandIndexParser.ftsQuery("  purple mot "))
        assertEquals("4* mat*", ModlandIndexParser.ftsQuery("4-mat"))
        assertNull(ModlandIndexParser.ftsQuery(" \"*' "))
    }

    @Test
    fun encodesPathsButKeepsSlashes() {
        assertEquals(
            "Protracker/4-Mat/4-mat%27s%20madness.mod",
            ModlandIndexParser.encodePath("Protracker/4-Mat/4-mat's madness.mod"),
        )
        assertEquals("A/B/100%25%20%23.mod", ModlandIndexParser.encodePath("A/B/100% #.mod"))
    }

    @Test
    fun readsHvscPaths() {
        val musician = ModlandIndexParser.parse("4223\tHVSC/MUSICIANS/H/Hubbard_Rob/Commando.sid")!!
        assertEquals("HVSC", musician.format)
        assertEquals("Rob Hubbard", musician.composer)
        assertEquals("Commando", musician.title)
        assertEquals("sid", musician.ext)
        val game = ModlandIndexParser.parse("3000\tHVSC/GAMES/A-F/Bubble_Bobble.sid")!!
        assertEquals("Games", game.composer)
        assertEquals("Bubble Bobble", game.title)
        assertTrue(ModuleFormats.isPlayableOnModland("HVSC", "sid"))
        assertFalse(ModuleFormats.isPlayableOnModland("RealSID", "rsid"))
    }
}
