// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConfigTest {
    @Test
    fun userAgentNamesAppVersionAndRepo() {
        val config = ApiConfig("0.1.0", "https://example.org/repo")
        assertEquals("AMPlatypus/0.1.0 (Android; +https://example.org/repo)", config.userAgent)
    }

    /** The AMP server recognises the app by this pattern, so every version name must keep matching it. */
    @Test
    fun userAgentMatchesTheAmpServerPattern() {
        val pattern = Regex("""^AMPlatypus/(?:\S+\.){2}\d+(?:-\S+)?\s*?\s\(Android;""")
        listOf("0.4.0-pre2", "0.4.0-pre2-full", "1.0.0", "1.0.0-full", "1.12.3").forEach { version ->
            val agent = ApiConfig(version, "https://github.com/YoldasKelboW/amplatypus").userAgent
            assertTrue(agent, pattern.containsMatchIn(agent))
        }
    }
}
