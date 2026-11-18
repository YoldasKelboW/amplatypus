// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.data

import io.github.yoldaskelbow.amplatypus.source.ModuleRef
import io.github.yoldaskelbow.amplatypus.source.MusicSource
import io.github.yoldaskelbow.amplatypus.source.SourceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import io.github.yoldaskelbow.amplatypus.audio.ModuleFormats
import java.io.File
import java.io.InputStream

/** The modules kept on the device. Downloads happen only when the user asks for a module. */
class Library(
    private val dao: LibraryDao,
    private val playlistDao: PlaylistDao,
    private val sources: Map<String, MusicSource>,
    private val modulesDir: File,
) {
    val modules: Flow<List<LibraryModule>> = dao.all()

    /** The modules played last, newest first; includes radio modules that were not kept. */
    val history: Flow<List<LibraryModule>> = dao.history(HISTORY_SIZE)

    fun byLocalPath(path: String): Flow<LibraryModule?> = dao.byLocalPath(path)

    suspend fun setFavorite(module: LibraryModule, favorite: Boolean) = dao.setFavorite(module.id, favorite)

    suspend fun keep(module: LibraryModule) = dao.keep(module.id)

    suspend fun favoriteCount(): Int = dao.favoriteCount()

    suspend fun randomFavorite(exceptId: Long): LibraryModule? =
        dao.randomFavorite(exceptId) ?: dao.randomFavorite(-1)

    /** Removes radio modules that were not kept and are no longer part of the history. */
    suspend fun removeExpiredRadioModules() {
        dao.expiredRadioModules(HISTORY_SIZE).forEach { delete(it) }
    }

    fun keysOf(sourceId: String): Flow<List<String>> = dao.keysOf(sourceId)

    /**
     * Returns the stored copy of [module], downloading it first if it is not on the device yet.
     * With [keep] false (the radio) a new module is stored but not listed in the library.
     */
    suspend fun obtain(module: SourceItem.Module, keep: Boolean = true, onProgress: (Float) -> Unit = {}): LibraryModule {
        dao.find(module.ref.sourceId, module.ref.key)?.let { existing ->
            if (File(existing.localPath).isFile) {
                if (keep && !existing.kept) dao.keep(existing.id)
                return existing.copy(kept = existing.kept || keep)
            }
        }
        val source = sources.getValue(module.ref.sourceId)
        val file = source.download(module.ref, File(modulesDir, module.ref.sourceId), onProgress)
        val row = LibraryModule(
            sourceId = module.ref.sourceId,
            sourceKey = module.ref.key,
            title = module.title,
            composer = module.composer,
            format = module.format,
            sizeBytes = file.length(),
            localPath = file.absolutePath,
            addedAt = System.currentTimeMillis(),
            kept = keep,
        )
        return row.copy(id = dao.insert(row))
    }

    suspend fun delete(module: LibraryModule) {
        withContext(Dispatchers.IO) {
            val file = File(module.localPath)
            // The file first: a companion can be the folder that held it.
            file.delete()
            sources[module.sourceId]?.companionFiles(ModuleRef(module.sourceId, module.sourceKey), file)?.forEach { it.delete() }
        }
        playlistDao.removeEverywhere(module.id)
        dao.delete(module.id)
    }

    // ---- Playlists ----

    val playlists: Flow<List<PlaylistSummary>> = playlistDao.playlists()

    fun playlistModules(playlistId: Long): Flow<List<LibraryModule>> = playlistDao.modules(playlistId)

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insert(Playlist(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun addToPlaylist(playlistId: Long, module: LibraryModule) {
        // A module in a playlist must not be cleaned up with the radio history.
        if (!module.kept) dao.keep(module.id)
        playlistDao.add(playlistId, module.id)
    }

    suspend fun removeFromPlaylist(playlistId: Long, module: LibraryModule) = playlistDao.remove(playlistId, module.id)

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.clear(playlistId)
        playlistDao.delete(playlistId)
    }

    // ---- Local files ----

    /**
     * Copies a file the user picked into the app and adds it to the library. Returns null if the
     * file is not in a format the app can play.
     */
    suspend fun importFile(displayName: String, open: () -> InputStream?): LibraryModule? = withContext(Dispatchers.IO) {
        val (title, extension) = splitFileName(displayName) ?: return@withContext null
        if (!ModuleFormats.isPlayable(extension)) return@withContext null
        val dir = File(modulesDir, LOCAL_SOURCE).apply { mkdirs() }
        val safeName = displayName.replace(Regex("[/\\\u0000]"), "_")
        var target = File(dir, safeName)
        var counter = 1
        while (target.exists()) target = File(dir, "${counter++}_$safeName")
        val input = open() ?: return@withContext null
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        val row = LibraryModule(
            sourceId = LOCAL_SOURCE,
            sourceKey = target.name,
            title = title,
            composer = null,
            format = extension.uppercase(),
            sizeBytes = target.length(),
            localPath = target.absolutePath,
            addedAt = System.currentTimeMillis(),
        )
        row.copy(id = dao.insert(row))
    }

    suspend fun markPlayed(localPath: String) = dao.markPlayed(localPath, System.currentTimeMillis())

    companion object {
        /** Same length as 4champ's radio session history. */
        const val HISTORY_SIZE = 30

        /** Source id of modules imported from the device. */
        const val LOCAL_SOURCE = "local"

        /**
         * Splits a module file name into title and extension. Besides `title.mod` it understands the
         * Amiga convention of putting the format first, as in `mod.title`.
         */
        fun splitFileName(name: String): Pair<String, String>? {
            val dot = name.lastIndexOf('.')
            if (dot > 0 && dot < name.length - 1 && ModuleFormats.isPlayable(name.substring(dot + 1))) {
                return name.substring(0, dot) to name.substring(dot + 1).lowercase()
            }
            val first = name.indexOf('.')
            if (first > 0 && first < name.length - 1 && ModuleFormats.isPlayable(name.substring(0, first))) {
                return name.substring(first + 1) to name.substring(0, first).lowercase()
            }
            if (dot > 0 && dot < name.length - 1) return name.substring(0, dot) to name.substring(dot + 1).lowercase()
            return null
        }
    }
}
