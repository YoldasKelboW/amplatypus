// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.source.modland

import io.github.yoldaskelbow.amplatypus.audio.FormatFilter
import io.github.yoldaskelbow.amplatypus.audio.ModuleFormats
import io.github.yoldaskelbow.amplatypus.audio.XsfLibraries
import io.github.yoldaskelbow.amplatypus.data.IndexDao
import io.github.yoldaskelbow.amplatypus.data.IndexEntry
import io.github.yoldaskelbow.amplatypus.net.Endpoints
import io.github.yoldaskelbow.amplatypus.source.Capability
import io.github.yoldaskelbow.amplatypus.source.FolderKind
import io.github.yoldaskelbow.amplatypus.source.ModuleRef
import io.github.yoldaskelbow.amplatypus.source.MusicSource
import io.github.yoldaskelbow.amplatypus.source.SearchKind
import io.github.yoldaskelbow.amplatypus.source.SourceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Modland, searched and browsed through the local index; only downloads touch the network. */
class ModlandSource(
    private val dao: IndexDao,
    private val http: OkHttpClient,
    /** Instrument files of the folder formats, see [ModlandIndex.folderFiles]. */
    private val folderFiles: () -> List<String> = { emptyList() },
    /** The format groups the user wants to see. */
    private val filter: () -> FormatFilter = { FormatFilter() },
) : MusicSource {
    private val trackerExts = ModuleFormats.namedExtensions.toList()
    override val id = ID
    override val capabilities = setOf(Capability.SEARCH, Capability.BROWSE, Capability.RANDOM)

    override suspend fun search(query: String, kind: SearchKind, page: Int): List<SourceItem> {
        val offset = page * PAGE_SIZE
        return when (kind) {
            SearchKind.COMPOSER ->
                dao.searchComposers(query.trim(), PAGE_SIZE, offset).map { composerFolder(null, it.name, it.count) }
            else -> {
                val match = ModlandIndexParser.ftsQuery(query) ?: return emptyList()
                filter().let { f -> dao.searchModules(match, PAGE_SIZE, offset, f.shownExtensions(), f.amiga, trackerExts) }.map { it.toItem() }
            }
        }
    }

    override suspend fun browse(parent: SourceItem.Folder?, page: Int): List<SourceItem> {
        val offset = page * PAGE_SIZE
        return when (parent?.kind) {
            null -> filter().let { f -> dao.formats(PAGE_SIZE, offset, f.shownExtensions(), f.amiga, trackerExts) }.map {
                SourceItem.Folder(ID, it.name, it.name, FolderKind.FORMAT, it.count.toString())
            }
            FolderKind.FORMAT -> dao.composersOfFormat(parent.key, PAGE_SIZE, offset).map {
                composerFolder(parent.key, it.name, it.count)
            }
            FolderKind.COMPOSER -> {
                val (format, composer) = splitComposerKey(parent.key)
                val rows = if (format == null) {
                    filter().let { f -> dao.modulesOfComposer(composer, PAGE_SIZE, offset, f.shownExtensions(), f.amiga, trackerExts) }
                } else {
                    filter().let { f -> dao.modulesOf(format, composer, PAGE_SIZE, offset, f.shownExtensions(), f.amiga, trackerExts) }
                }
                rows.map { it.toItem() }
            }
            FolderKind.GROUP -> emptyList()
        }
    }

    override suspend fun details(ref: ModuleRef): SourceItem.Module? = dao.byPath(ref.key)?.toItem()

    override suspend fun random(): SourceItem.Module? {
        val f = filter()
        // The random start can fall behind the last shown module; then try again from another place.
        repeat(RANDOM_TRIES) { dao.random(f.shownExtensions(), f.amiga, trackerExts)?.let { return it.toItem() } }
        return null
    }

    override suspend fun download(ref: ModuleRef, targetDir: File, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val target = File(targetDir, ref.key)
            // The companions go first, so a song file on disk always has its samples next to it.
            for (companion in companionsOf(ref.key)) {
                try {
                    fetch(companion.path, File(targetDir, companion.path)) {}
                } catch (e: IOException) {
                    if (companion.required) throw e
                }
            }
            fetch(ref.key, target, onProgress)
            fetchXsfLibraries(ref.key, target, targetDir, onProgress)
            target
        }

    /**
     * xSF songs name the library files that hold most of their game's music; those are fetched next to the
     * song, and the ones they name in turn. Modland keeps its file names in lower case, the tags often not.
     * Libraries stay when a song is deleted: other songs of the game use them too.
     */
    private fun fetchXsfLibraries(songPath: String, song: File, targetDir: File, onProgress: (Float) -> Unit) {
        if (songPath.substringAfterLast('.').lowercase() !in ModuleFormats.xsfExtensions) return
        val dir = songPath.substringBeforeLast('/')
        val queue = ArrayDeque(XsfLibraries.namesIn(song.readBytes()))
        val seen = mutableSetOf<String>()
        while (queue.isNotEmpty() && seen.size < MAX_XSF_LIBRARIES) {
            val name = queue.removeFirst()
            if (!seen.add(name.lowercase())) continue
            val library = try {
                fetch("$dir/$name", File(targetDir, "$dir/$name"), onProgress)
            } catch (e: MissingFile) {
                if (name == name.lowercase()) throw e
                fetch("$dir/${name.lowercase()}", File(targetDir, "$dir/${name.lowercase()}"), onProgress)
            }
            queue.addAll(XsfLibraries.namesIn(library.readBytes()))
        }
    }

    override fun companionFiles(ref: ModuleRef, file: File): List<File> {
        val root = file.path.removeSuffix(ref.key)
        return companionsOf(ref.key).map { File(root + it.path) }
    }

    private fun companionsOf(path: String) =
        ModlandCompanions.companionsOf(path) + ModlandCompanions.folderCompanionsOf(path, folderFiles())

    /** Downloads one Modland file to [target], trying the mirrors in turn. Does nothing if it is there. */
    private fun fetch(path: String, target: File, onProgress: (Float) -> Unit): File {
        if (target.isFile && target.length() > 0) return target
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        var lastError: IOException? = null
        for (host in Endpoints.MODLAND_HOSTS) {
            val url = "https://" + host + Endpoints.MODLAND_MODULE_PATH + ModlandIndexParser.encodePath(path)
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    // A file that is not there is not there on the mirrors either.
                    if (response.code == 404) throw MissingFile(path)
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
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
                }
                if (!partial.renameTo(target)) throw IOException("Could not store ${target.name}")
                return target
            } catch (e: MissingFile) {
                partial.delete()
                throw e
            } catch (e: IOException) {
                lastError = e
                partial.delete()
            }
        }
        throw lastError ?: IOException("No download host available")
    }

    private class MissingFile(path: String) : IOException("Not on Modland: $path")

    private fun composerFolder(format: String?, composer: String, count: Int) = SourceItem.Folder(
        sourceId = ID,
        key = (format ?: ANY_FORMAT) + "/" + composer,
        title = displayComposer(composer),
        kind = FolderKind.COMPOSER,
        subtitle = count.toString(),
    )

    private fun splitComposerKey(key: String): Pair<String?, String> {
        val slash = key.indexOf('/')
        val format = key.substring(0, slash)
        return (if (format == ANY_FORMAT) null else format) to key.substring(slash + 1)
    }

    private fun IndexEntry.toItem() = SourceItem.Module(
        ref = ModuleRef(ID, path),
        title = title,
        composer = displayComposer(composer),
        format = ext.uppercase(),
        sizeBytes = sizeBytes,
    )

    companion object {
        /** More library files than any real song has; a guard against tags that name each other. */
        private const val MAX_XSF_LIBRARIES = 16

        const val ID = "modland"
        const val PAGE_SIZE = 50
        private const val RANDOM_TRIES = 5

        /** Shown for modules without a known author; the same in every language, like the names around it. */
        const val UNKNOWN_COMPOSER = "Unknown"
        private const val ANY_FORMAT = "*"

        /** The public address of a module, for sharing. */
        fun shareUrl(path: String): String =
            "https://" + Endpoints.MODLAND_HOSTS.first() + Endpoints.MODLAND_MODULE_PATH + ModlandIndexParser.encodePath(path)

        /** Modland files modules without a known author under `- unknown`. */
        fun displayComposer(raw: String): String = if (raw.trim('-', '_', ' ').equals("unknown", ignoreCase = true)) UNKNOWN_COMPOSER else raw
    }
}
