// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.ui.library

import io.github.yoldaskelbow.amplatypus.data.LibraryModule
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private fun module(id: Long, title: String, composer: String?, favorite: Boolean = false, addedAt: Long = id) =
        LibraryModule(id, "modland", "k$id", title, composer, "MOD", 1000, "/m/$id", addedAt, favorite)

    private val modules = listOf(
        module(1, "space debris", "Captain"),
        module(2, "Axel F", "Audiomonster", favorite = true),
        module(3, "enigma", "Tip", favorite = true),
        module(4, "Beyond music", null),
    )

    private fun ids(filter: LibraryFilter) = modules.filtered(filter).map { it.id }

    @Test
    fun sortsByTitleIgnoringCase() = assertEquals(listOf(2L, 4L, 3L, 1L), ids(LibraryFilter()))

    @Test
    fun sortsByComposerWithUnknownFirst() =
        assertEquals(listOf(4L, 2L, 1L, 3L), ids(LibraryFilter(sort = LibrarySort.COMPOSER)))

    @Test
    fun sortsNewestFirst() = assertEquals(listOf(4L, 3L, 2L, 1L), ids(LibraryFilter(sort = LibrarySort.RECENT)))

    @Test
    fun favouritesOnly() = assertEquals(listOf(2L, 3L), ids(LibraryFilter(favoritesOnly = true)))

    @Test
    fun searchMatchesTitleOrComposerAndAllWords() {
        assertEquals(listOf(1L), ids(LibraryFilter(query = "captain")))
        assertEquals(listOf(1L), ids(LibraryFilter(query = "  SPACE  cap ")))
        assertEquals(emptyList<Long>(), ids(LibraryFilter(query = "space tip")))
        assertEquals(listOf(3L), ids(LibraryFilter(query = "e", favoritesOnly = true, sort = LibrarySort.RECENT)).take(1))
    }
}
