// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XsfLibrariesTest {
    /** A minimal xSF file: header, [reserved] and [program] sections, then [tag] if given. */
    private fun xsf(tag: String?, reserved: Int = 0, program: Int = 4): ByteArray {
        val header = byteArrayOf('P'.code.toByte(), 'S'.code.toByte(), 'F'.code.toByte(), 0x22) +
            le(reserved) + le(program) + le(0)
        val body = ByteArray(reserved + program)
        return header + body + (tag?.let { "[TAG]$it".toByteArray() } ?: ByteArray(0))
    }

    private fun le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    @Test
    fun libraryNamesInOrder() {
        val file = xsf("title=Title\n_lib3=c.gsflib\n_LIB=Puyo Pop.gsflib\n_lib2=b.gsflib\nlength=1:30\n", reserved = 8, program = 12)
        assertEquals(listOf("Puyo Pop.gsflib", "b.gsflib", "c.gsflib"), XsfLibraries.namesIn(file))
    }

    @Test
    fun noTagsNoLibraries() {
        assertTrue(XsfLibraries.namesIn(xsf(null)).isEmpty())
        assertTrue(XsfLibraries.namesIn(xsf("title=Only a title\n")).isEmpty())
        assertTrue(XsfLibraries.namesIn("not an xsf file".toByteArray()).isEmpty())
    }

    @Test
    fun namesOutsideTheFolderAreIgnored() {
        val file = xsf("_lib=../../secret\n_lib2=/etc/passwd\n_lib3=sub/ok.2sflib\n")
        assertEquals(listOf("sub/ok.2sflib"), XsfLibraries.namesIn(file))
    }

    @Test
    fun windowsLineEndings() {
        assertEquals(listOf("game.usflib"), XsfLibraries.namesIn(xsf("_lib=game.usflib\r\ntitle=x\r\n")))
    }
}
