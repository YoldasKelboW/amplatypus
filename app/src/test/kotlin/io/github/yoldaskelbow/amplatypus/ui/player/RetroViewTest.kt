// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.player

import io.github.yoldaskelbow.amplatypus.audio.ModuleEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class RetroViewTest {
    @Test
    fun `cells are shown as the classic eight characters`() {
        // Octaves as ProTracker counts them, three lower than libopenmpt.
        assertEquals("C-201A0F", retroCell("C-5 01 A0F"))
        assertEquals("---00000", retroCell("... .. ..."))
        assertEquals("G#31BC10", retroCell("G#6 1B C10"))
        assertEquals("---00C07", retroCell("... .. C07"))
    }

    @Test
    fun `the current sample is the last one struck`() {
        val rows = listOf(
            "C-5 01 ...|... .. ...",
            "... .. ...|D-5 0A ...",
            "... .. ...|... .. ...",
        )
        val samples = List(12) { "sample ${it + 1}" }
        assertEquals("01: sample 1", lastSampleName(rows, 0, samples))
        assertEquals("0A: sample 10", lastSampleName(rows, 2, samples))
        // Many modules leave sample names empty; the number still shows.
        assertEquals("0A", lastSampleName(rows, 2, List(12) { "" }))
        assertEquals("", lastSampleName(emptyList(), 5, samples))
    }

    @Test
    fun `FastTracker cells put the volume column before the effect`() {
        // One octave lower than libopenmpt, as FastTracker counts.
        assertEquals("C-4 01 v40 A0F", ftCell("C-5 01 A0F v40"))
        assertEquals("--- .. ... ...", ftCell("... .. ... ..."))
        // Cells without volume column (older native output) still work.
        assertEquals("G#5 1B ... C10", ftCell("G#6 1B C10"))
    }

    @Test
    fun `Impulse and Scream Tracker cells show the instrument in decimal`() {
        assertEquals("C-5 27 v40 A0F", itCell("C-5 1B A0F v40"))
        assertEquals("... .. ... ...", itCell("... .. ... ..."))
        assertEquals("G#6 01 ... C10", itCell("G#6 01 C10"))
    }

    @Test
    fun `Spectrum cells put volume before envelope and noise`() {
        assertEquals("C-4 F E0E 1F", zxCell("C-4 1F E0E v0F"))
        assertEquals("... 7 ... ..", zxCell("... .. ... v07"))
        assertEquals("... . ... ..", zxCell("... .. ... ..."))
    }

    @Test
    fun `Spectrum modules with patterns get the Spectrum tracker view`() {
        val info = ModuleEngine.ModuleInfo(generation = 1, title = "", type = "ZX Spectrum (ProTracker 3.5)", tracker = "", channels = 3,
            message = "", samples = emptyList(), instruments = emptyList())
        assertEquals(RetroStyle.ZX_SPECTRUM, retroStyleOf(info))
        // Register dumps (VTX) and two-chip files have no patterns.
        assertEquals(RetroStyle.DELITRACKER, retroStyleOf(info.copy(hasPattern = false)))
    }

    @Test
    fun `HivelyTracker cells fill empty steps with dashes and zeros`() {
        assertEquals("C-3 01 C10", hvlCell("C-3 01 C10"))
        assertEquals("--- 00 000", hvlCell("... .. ..."))
        assertEquals("D#2 12 000", hvlCell("D#2 12 ..."))
    }

    @Test
    fun `HivelyTracker instrument numbers are decimal`() {
        val rows = listOf("C-3 12 ...|... .. ...")
        assertEquals("12: twelve", lastSampleName(rows, 0, List(20) { if (it == 11) "twelve" else "" }, cellRadix = 10, decimal = true))
    }

    @Test
    fun `OctaMED cells count octaves as on the Amiga`() {
        assertEquals("C-2 01 C40", medCell("C-5 01 C40"))
        assertEquals("--- 00 000", medCell("... .. ..."))
    }
}
