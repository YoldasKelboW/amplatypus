// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.source.modland

import android.content.Context
import androidx.room.withTransaction
import io.github.yoldaskelbow.amplatypus.audio.ModuleFormats
import io.github.yoldaskelbow.amplatypus.data.AppDatabase
import io.github.yoldaskelbow.amplatypus.net.Endpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Keeps the local copy of the Modland index. The index is downloaded at most once a week, filtered to
 * the formats the app can play, and stored in the database. Directories are never listed live.
 */
class ModlandIndex(
    private val context: Context,
    private val db: AppDatabase,
    private val http: OkHttpClient,
) {
    sealed interface State {
        data object Idle : State
        data object Downloading : State
        data class Importing(val entries: Int) : State
        data class Ready(val entries: Int) : State
        data class Failed(val message: String) : State
    }

    private val prefs = context.getSharedPreferences("modland_index", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** Downloads the index if there is none, if it is older than a week, or if [force] is set. */
    suspend fun refreshIfNeeded(force: Boolean = false) {
        if (!mutex.tryLock()) return
        try {
            val count = db.indexDao().count()
            val age = System.currentTimeMillis() - prefs.getLong(KEY_UPDATED, 0)
            val current = prefs.getInt(KEY_FORMATS, 0) == ModuleFormats.VERSION
            if (!force && count > 0 && current && age < MAX_AGE_MS) {
                _state.value = State.Ready(count)
                return
            }
            // Never refresh more often than once a day, even when asked to.
            if (force && count > 0 && current && age < MIN_AGE_MS) {
                _state.value = State.Ready(count)
                return
            }
            try {
                val imported = withContext(Dispatchers.IO) { downloadAndImport() }
                prefs.edit()
                    .putLong(KEY_UPDATED, System.currentTimeMillis())
                    .putInt(KEY_FORMATS, ModuleFormats.VERSION)
                    .apply()
                _state.value = State.Ready(imported)
            } catch (e: Exception) {
                // Keep an older index usable if the update failed.
                _state.value = if (count > 0 && current) State.Ready(count) else State.Failed(e.message ?: e.javaClass.simpleName)
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun downloadAndImport(): Int {
        _state.value = State.Downloading
        val request = Request.Builder().url(Endpoints.MODLAND_INDEX_URL).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            ZipInputStream(response.body.byteStream()).use { zip ->
                zip.nextEntry ?: throw IOException("Index archive is empty")
                val reader = zip.bufferedReader(Charsets.ISO_8859_1)
                var imported = 0
                val folderFiles = ArrayList<String>()
                db.withTransaction {
                    val sql = db.openHelper.writableDatabase
                    sql.execSQL("DELETE FROM index_fts")
                    sql.execSQL("DELETE FROM index_entry")
                    val insertEntry = sql.compileStatement(
                        "INSERT INTO index_entry(id, path, format, composer, title, ext, sizeBytes) VALUES(?,?,?,?,?,?,?)",
                    )
                    val insertFts = sql.compileStatement("INSERT INTO index_fts(rowid, title, composer) VALUES(?,?,?)")
                    reader.lineSequence().forEach { raw ->
                        val line = ModlandIndexParser.parse(raw) ?: return@forEach
                        if (!ModuleFormats.isPlayableOnModland(line.format, line.ext, line.path)) return@forEach
                        // Sample files of two-file formats are fetched with their song, not listed.
                        if (ModlandCompanions.isFolderFile(line.format, line.path)) {
                            folderFiles += line.path
                            return@forEach
                        }
                        if (ModlandCompanions.isCompanion(line.format, line.path)) return@forEach
                        imported++
                        val id = imported.toLong()
                        insertEntry.bindLong(1, id)
                        insertEntry.bindString(2, line.path)
                        insertEntry.bindString(3, line.format)
                        insertEntry.bindString(4, line.composer)
                        insertEntry.bindString(5, line.title)
                        insertEntry.bindString(6, line.ext)
                        insertEntry.bindLong(7, line.sizeBytes)
                        insertEntry.executeInsert()
                        insertFts.bindLong(1, id)
                        insertFts.bindString(2, line.title)
                        insertFts.bindString(3, line.composer)
                        insertFts.executeInsert()
                        if (imported % 5000 == 0) _state.value = State.Importing(imported)
                    }
                }
                if (imported == 0) throw IOException("Index contained no playable modules")
                saveFolderFiles(folderFiles)
                return imported
            }
        }
    }

    @Volatile private var folderFilesCache: List<String>? = null

    /**
     * Instrument files of the folder formats (IFF-SMUS), which are not in the database: they are only
     * needed when their song is downloaded, so they are kept in a plain list next to it.
     */
    fun folderFiles(): List<String> = folderFilesCache ?: run {
        val file = java.io.File(context.filesDir, FOLDER_FILES)
        val list = if (file.isFile) file.readLines(Charsets.UTF_8).filter { it.isNotEmpty() } else emptyList()
        folderFilesCache = list
        list
    }

    private fun saveFolderFiles(paths: List<String>) {
        val file = java.io.File(context.filesDir, FOLDER_FILES)
        val partial = java.io.File(context.filesDir, "$FOLDER_FILES.part")
        partial.writeText(paths.joinToString("\n"), Charsets.UTF_8)
        partial.renameTo(file)
        folderFilesCache = paths
    }

    private companion object {
        const val FOLDER_FILES = "modland_folder_files.txt"
        const val KEY_UPDATED = "updated_at"
        const val KEY_FORMATS = "formats_version"
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val MIN_AGE_MS = 24L * 60 * 60 * 1000
    }
}
