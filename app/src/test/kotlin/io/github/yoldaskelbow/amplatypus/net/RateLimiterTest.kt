// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimiterTest {
    @Test
    fun neverStartsMoreThanFiveRequestsInTenSeconds() = runBlocking {
        var clock = 0L
        val starts = mutableListOf<Long>()
        val limiter = RateLimiter(5, 10_000, now = { clock }, sleep = { clock += it })
        repeat(12) {
            limiter.acquire()
            starts += clock
            clock += 100 // each request takes a moment
        }
        for (i in 5 until starts.size) {
            val window = starts[i] - starts[i - 5]
            assert(window >= 10_000) { "requests ${i - 5} and $i only $window ms apart" }
        }
        assertEquals(0L, starts[0])
        assertEquals(10_000L, starts[5])
    }
}
