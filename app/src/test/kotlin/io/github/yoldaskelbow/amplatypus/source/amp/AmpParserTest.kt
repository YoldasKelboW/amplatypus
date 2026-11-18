// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.source.amp

import io.github.yoldaskelbow.amplatypus.source.FolderKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The samples are shortened answers of the AMP API (https://amp.dascene.net/api). */
class AmpParserTest {
    private val playable: (String) -> Boolean = { it in setOf("mod", "xm", "med") }

    private val module = """
        {"module_id": 51456, "module_name": "the art of tunes", "module_type": "MOD", "file_size": 289606,
         "tracker": "Protracker and family", "duration": "04:56",
         "authors": [{"composer_id": 5442, "composer_handle": "Noteman"}],
         "download_link": "https://amp.dascene.net/module/51456"}
    """.trimIndent()

    @Test
    fun readsAModule() {
        val item = AmpParser.module(JSONObject(module), playable)!!
        assertEquals("amp", item.ref.sourceId)
        assertEquals("51456", item.ref.key)
        assertEquals("the art of tunes", item.title)
        assertEquals("Noteman", item.composer)
        assertEquals("MOD", item.format)
        assertEquals(289606L, item.sizeBytes)
        assertEquals(296, item.durationSeconds)
    }

    @Test
    fun joinsSeveralAuthorsAndLeavesOutUnplayableFormats() {
        val array = JSONArray(
            """
            [{"module_id": 1, "module_name": "dynamite", "module_type": "MOD",
              "authors": [{"composer_handle": "Mac"}, {"composer_handle": "Cyborg"}]},
             {"module_id": 2, "module_name": "sid tune", "module_type": "SID", "authors": []},
             {"module_id": 3, "module_name": "", "module_type": "XM"}]
            """,
        )
        val items = AmpParser.modules(array, playable)
        assertEquals(listOf("1", "3"), items.map { it.ref.key })
        assertEquals("Mac & Cyborg", items[0].composer)
        assertNull(items[1].composer)
        assertEquals("#3", items[1].title)
    }

    @Test
    fun readsTheSearchEnvelope() {
        val page = AmpParser.page(JSONObject("""{"current_page": 1, "total_page": 3, "modules": [$module]}"""), playable)
        assertEquals(1, page.modules.size)
        assertFalse(page.last)
        assertTrue(AmpParser.page(JSONObject("""{"current_page": 3, "total_page": 3, "modules": []}"""), playable).last)
    }

    @Test
    fun readsAComposer() {
        val folder = AmpParser.composer(
            JSONObject(
                """{"composer_id": 4310, "composer_handle": "Lizardking", "real_name": "x",
                   "country": [{"country_id": 52, "country_name": "Sweden"}], "module_count": 120}""",
            ),
        )!!
        assertEquals(FolderKind.COMPOSER, folder.kind)
        assertEquals("4310", folder.key)
        assertEquals("Lizardking", folder.title)
        assertEquals("120", folder.subtitle)
        assertEquals("Sweden", folder.detail)
    }

    @Test
    fun readsDurations() {
        assertEquals(92, AmpParser.duration("01:32"))
        assertEquals(3723, AmpParser.duration("1:02:03"))
        assertNull(AmpParser.duration(""))
        assertNull(AmpParser.duration("00:00"))
        assertNull(AmpParser.duration("n/a"))
    }

    @Test
    fun takesTheFileNameFromTheHeader() {
        assertEquals("blitboy.mod", AmpParser.fileName("""attachment; filename="blitboy.mod"""", "1.mod"))
        assertEquals("evil.mod", AmpParser.fileName("""attachment; filename="../../evil.mod"""", "1.mod"))
        assertEquals("a_b.xm", AmpParser.fileName("""attachment; filename="a:b.xm"""", "1.mod"))
        assertEquals("1.mod", AmpParser.fileName(null, "1.mod"))
        assertEquals("1.mod", AmpParser.fileName("""attachment; filename="..""""", "1.mod"))
    }
}
