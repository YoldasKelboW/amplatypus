// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.net

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Builds the one HTTP client of the app: identifying User-Agent, and retries with growing waits. */
object Http {
    fun client(userAgent: String): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .addInterceptor(BackoffInterceptor())
        .build()
}

/** Retries idempotent requests on network errors and 5xx/429 answers, waiting 1 s, 2 s, 4 s. */
class BackoffInterceptor(
    private val maxRetries: Int = 3,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        var attempt = 0
        while (true) {
            var failure: IOException? = null
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                failure = e
                null
            }
            val retryable = response == null || response.code >= 500 || response.code == 429
            if (!retryable || attempt >= maxRetries) {
                return response ?: throw failure!!
            }
            response?.close()
            sleep(1000L shl attempt)
            attempt++
        }
    }
}
