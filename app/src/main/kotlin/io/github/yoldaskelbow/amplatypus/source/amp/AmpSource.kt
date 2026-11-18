// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.source.amp

import io.github.yoldaskelbow.amplatypus.audio.ModuleFormats
import io.github.yoldaskelbow.amplatypus.net.Endpoints
import io.github.yoldaskelbow.amplatypus.net.RateLimiter
import io.github.yoldaskelbow.amplatypus.source.Capability
import io.github.yoldaskelbow.amplatypus.source.FolderKind
import io.github.yoldaskelbow.amplatypus.source.ModuleRef
import io.github.yoldaskelbow.amplatypus.source.MusicSource
import io.github.yoldaskelbow.amplatypus.source.SearchKind
import io.github.yoldaskelbow.amplatypus.source.SourceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException

/**
 * Amiga Music Preservation (https://amp.dascene.net), through its public API.
 *
 * Used with the permission of the AMP staff (see docs/PERMISSIONS.md). Their conditions are kept here:
 * an identifying User-Agent (set by the app's HTTP client), at most 5 requests per 10 seconds,
 * answers cached on the device, nothing fetched in bulk, and get_similar_modules asked for 5 results only.
 */
class AmpSource(
    private val http: OkHttpClient,
    private val playable: (String) -> Boolean = ModuleFormats::isPlayable,
) : MusicSource {
    override val id = ID
    override val capabilities = setOf(Capability.SEARCH, Capability.BROWSE, Capability.RANDOM, Capability.LATEST, Capability.SIMILAR)

    private val limiter = RateLimiter(Endpoints.AMP_REQUESTS, Endpoints.AMP_WINDOW_MS)
    private val cache = object : LinkedHashMap<String, Pair<Long, Any>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Any>>) = size > CACHE_ENTRIES
    }

    override suspend fun search(query: String, kind: SearchKind, page: Int): List<SourceItem> {
        val text = query.trim()
        return when (kind) {
            SearchKind.COMPOSER -> {
                // search_by_handle has no pages; it returns up to 50 composers at once.
                if (page > 0 || text.length < 2) return emptyList()
                AmpParser.composers(getArray("search_by_handle", "search" to text.take(31), "match" to "false"))
            }
            else -> {
                if (text.isEmpty()) return emptyList()
                // The site's search is an exact name match unless the term has a '*'; the app searches within names.
                val term = if ('*' in text) text else "*$text*"
                val json = try {
                    getObject("search_modules_paged", "search" to term.take(63), "page" to (page + 1).toString())
                } catch (e: HttpError) {
                    if (e.code == 400) return emptyList() // a page past the last one
                    throw e
                }
                val result = AmpParser.page(json, playable)
                lastPageWasLast = result.last
                result.modules
            }
        }
    }

    /**
     * Whether the last module search page was the final one. Unplayable formats are left out of a page,
     * so a short page does not mean the end on its own.
     */
    @Volatile var lastPageWasLast = true
        private set

    /** Top level: the latest additions. A composer: all their modules (playable ones). */
    override suspend fun browse(parent: SourceItem.Folder?, page: Int): List<SourceItem> {
        if (page > 0) return emptyList()
        return when (parent?.kind) {
            null -> latest()
            FolderKind.COMPOSER -> AmpParser.modules(getArray("get_modules_by_author", "index" to parent.key), playable)
                .sortedBy { it.title.lowercase() }
            else -> emptyList()
        }
    }

    suspend fun latest(): List<SourceItem.Module> =
        AmpParser.modules(getArray("get_latest_additions", "category" to "modules", "limit" to "50"), playable)

    override suspend fun details(ref: ModuleRef): SourceItem.Module? =
        AmpParser.module(getObject("get_module_details", "index" to ref.key), playable)

    /** A random public module. AMP picks one of every format, so unplayable picks are skipped. */
    override suspend fun random(): SourceItem.Module? {
        repeat(RANDOM_TRIES) {
            AmpParser.module(getObject("get_module_details", fresh = true), playable)?.let { return it }
        }
        return null
    }

    /** The modules that sound most like [ref], best first; empty if AMP has no sonic analysis for it. */
    suspend fun similar(ref: ModuleRef): List<SourceItem.Module> {
        val answer = get("get_similar_modules", fresh = false, "index" to ref.key, "limit" to Endpoints.AMP_SIMILAR_LIMIT.toString())
        return if (answer is JSONArray) AmpParser.modules(answer, playable) else emptyList()
    }

    override suspend fun download(ref: ModuleRef, targetDir: File, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            // Every module gets a folder of its own, named by its AMP number, so equal file names never clash.
            val dir = File(targetDir, ref.key)
            dir.listFiles()?.firstOrNull { it.isFile && it.length() > 0 && !it.name.endsWith(".part") }?.let { return@withContext it }
            dir.mkdirs()
            limiter.acquire()
            val request = Request.Builder().url(Endpoints.AMP_MODULE + ref.key).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("AMP: HTTP ${response.code}")
                val name = AmpParser.fileName(response.header("Content-Disposition"), "${ref.key}.mod")
                val target = File(dir, name)
                val partial = File(dir, "$name.part")
                val total = response.body.contentLength()
                var done = 0L
                response.body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
                if (!partial.renameTo(target)) {
                    partial.delete()
                    throw IOException("Could not store $name")
                }
                target
            }
        }

    override fun companionFiles(ref: ModuleRef, file: File): List<File> =
        // The folder of the module, so deleting the module leaves nothing behind.
        listOfNotNull(file.parentFile?.takeIf { it.name == ref.key })

    private suspend fun getObject(call: String, vararg params: Pair<String, String>, fresh: Boolean = false): JSONObject =
        get(call, fresh, *params) as? JSONObject ?: throw IOException("AMP: unexpected answer to $call")

    private suspend fun getArray(call: String, vararg params: Pair<String, String>): JSONArray =
        get(call, false, *params) as? JSONArray ?: JSONArray()

    /** One API call, answered from the cache when possible. [fresh] skips the cache (random picks). */
    private suspend fun get(call: String, fresh: Boolean, vararg params: Pair<String, String>): Any {
        val url = Endpoints.AMP_API.toHttpUrl().newBuilder().addPathSegment(call).apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val key = url.toString()
        if (!fresh) {
            synchronized(cache) { cache[key] }?.let { (time, value) ->
                if (System.currentTimeMillis() - time < CACHE_MS) return value
            }
        }
        limiter.acquire()
        val value = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw HttpError(response.code)
                JSONTokener(response.body.string()).nextValue()
            }
        }
        if (!fresh) synchronized(cache) { cache[key] = System.currentTimeMillis() to value }
        return value
    }

    class HttpError(val code: Int) : IOException("AMP: HTTP $code")

    companion object {
        const val ID = "amp"
        private const val CACHE_ENTRIES = 100
        private const val CACHE_MS = 30 * 60 * 1000L
        private const val RANDOM_TRIES = 4

        /** The module's download address on AMP, for sharing. */
        fun shareUrl(key: String) = Endpoints.AMP_MODULE + key
    }
}
