// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.player

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
}
