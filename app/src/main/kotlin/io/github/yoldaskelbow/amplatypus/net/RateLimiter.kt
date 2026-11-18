// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lets at most [permits] requests start within any [windowMs]. A caller that would exceed the limit
 * waits until the oldest request has left the window. Used for servers that ask for a request budget.
 */
class RateLimiter(
    private val permits: Int,
    private val windowMs: Long,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private val starts = ArrayDeque<Long>()

    suspend fun acquire() = mutex.withLock {
        while (true) {
            val time = now()
            while (starts.isNotEmpty() && time - starts.first() >= windowMs) starts.removeFirst()
            if (starts.size < permits) {
                starts.addLast(time)
                return@withLock
            }
            sleep(windowMs - (time - starts.first()))
        }
    }
}
