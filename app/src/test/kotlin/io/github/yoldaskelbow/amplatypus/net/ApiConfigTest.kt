// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.net

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiConfigTest {
    @Test
    fun userAgentNamesAppVersionAndRepo() {
        val config = ApiConfig("0.1.0", "https://example.org/repo")
        assertEquals("AMPlatypus/0.1.0 (+https://example.org/repo)", config.userAgent)
    }
}
