// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryFileNameTest {
    @Test
    fun extensionAtTheEnd() = assertEquals("space debris" to "mod", Library.splitFileName("space debris.MOD"))

    @Test
    fun amigaStylePrefix() = assertEquals("space debris" to "mod", Library.splitFileName("mod.space debris"))

    @Test
    fun dotsInTheTitle() = assertEquals("v1.2 final" to "xm", Library.splitFileName("v1.2 final.xm"))

    @Test
    fun unknownExtensionIsReportedAsIs() = assertEquals("notes" to "txt", Library.splitFileName("notes.txt"))

    @Test
    fun noExtension() = assertNull(Library.splitFileName("README"))
}
