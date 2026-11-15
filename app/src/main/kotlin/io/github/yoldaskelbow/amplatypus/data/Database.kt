// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One playable file of the Modland index. */
@Entity(
    tableName = "index_entry",
    indices = [Index("format", "composer"), Index("composer")],
)
data class IndexEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Path below the module root, e.g. `Protracker/4-Mat/1978.mod`. */
    val path: String,
    val format: String,
    val composer: String,
    val title: String,
    val ext: String,
    val sizeBytes: Long,
)

/** Full-text index over title and composer; `rowid` equals [IndexEntry.id]. */
@Fts4
@Entity(tableName = "index_fts")
data class IndexFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val composer: String,
)

/** A module that has been downloaded (or imported) and is kept on the device. */
@Entity(
    tableName = "library",
    indices = [Index(value = ["sourceId", "sourceKey"], unique = true)],
)
data class LibraryModule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val sourceKey: String,
    val title: String,
    val composer: String?,
    val format: String,
    val sizeBytes: Long,
    val localPath: String,
    val addedAt: Long,
    val favorite: Boolean = false,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    /**
     * False for modules the radio fetched and the user has not chosen to keep. They show up in the
     * radio history only and are removed again once they drop out of it.
     */
    @ColumnInfo(defaultValue = "1") val kept: Boolean = true,
)

@Entity(tableName = "playlist")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** Membership of a library module in a playlist; [position] gives the order. */
@Entity(
    tableName = "playlist_entry",
    primaryKeys = ["playlistId", "moduleId"],
    indices = [Index("moduleId")],
)
data class PlaylistEntry(
    val playlistId: Long,
    val moduleId: Long,
    val position: Int,
)

data class PlaylistSummary(val id: Long, val name: String, val count: Int)

data class NameCount(val name: String, val count: Int)

@Dao
interface IndexDao {
    @Query("SELECT COUNT(*) FROM index_entry")
    suspend fun count(): Int

    @Query(
        "SELECT index_entry.* FROM index_entry JOIN index_fts ON index_fts.rowid = index_entry.id " +
            "WHERE index_fts MATCH :match ORDER BY index_entry.title COLLATE NOCASE LIMIT :limit OFFSET :offset",
    )
    suspend fun searchModules(match: String, limit: Int, offset: Int): List<IndexEntry>

    @Query(
        "SELECT composer AS name, COUNT(*) AS count FROM index_entry WHERE composer LIKE '%' || :text || '%' " +
            "GROUP BY composer ORDER BY composer COLLATE NOCASE LIMIT :limit OFFSET :offset",
    )
    suspend fun searchComposers(text: String, limit: Int, offset: Int): List<NameCount>

    @Query("SELECT format AS name, COUNT(*) AS count FROM index_entry GROUP BY format ORDER BY format COLLATE NOCASE LIMIT :limit OFFSET :offset")
    suspend fun formats(limit: Int, offset: Int): List<NameCount>

    @Query(
        "SELECT composer AS name, COUNT(*) AS count FROM index_entry WHERE format = :format " +
            "GROUP BY composer ORDER BY composer COLLATE NOCASE LIMIT :limit OFFSET :offset",
    )
    suspend fun composersOfFormat(format: String, limit: Int, offset: Int): List<NameCount>

    @Query("SELECT * FROM index_entry WHERE format = :format AND composer = :composer ORDER BY title COLLATE NOCASE LIMIT :limit OFFSET :offset")
    suspend fun modulesOf(format: String, composer: String, limit: Int, offset: Int): List<IndexEntry>

    @Query("SELECT * FROM index_entry WHERE composer = :composer ORDER BY title COLLATE NOCASE LIMIT :limit OFFSET :offset")
    suspend fun modulesOfComposer(composer: String, limit: Int, offset: Int): List<IndexEntry>

    @Query("SELECT * FROM index_entry WHERE path = :path LIMIT 1")
    suspend fun byPath(path: String): IndexEntry?

    @Query("SELECT * FROM index_entry WHERE id >= (ABS(RANDOM()) % (SELECT MAX(id) FROM index_entry)) LIMIT 1")
    suspend fun random(): IndexEntry?
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library WHERE kept = 1 ORDER BY title COLLATE NOCASE")
    fun all(): Flow<List<LibraryModule>>

    @Query("SELECT * FROM library WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun history(limit: Int): Flow<List<LibraryModule>>

    @Query("SELECT * FROM library WHERE localPath = :localPath LIMIT 1")
    fun byLocalPath(localPath: String): Flow<LibraryModule?>

    @Query("SELECT * FROM library WHERE favorite = 1 AND kept = 1 AND id != :exceptId ORDER BY RANDOM() LIMIT 1")
    suspend fun randomFavorite(exceptId: Long): LibraryModule?

    @Query("SELECT COUNT(*) FROM library WHERE favorite = 1 AND kept = 1")
    suspend fun favoriteCount(): Int

    @Query("UPDATE library SET favorite = :favorite, kept = CASE WHEN :favorite THEN 1 ELSE kept END WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE library SET kept = 1 WHERE id = :id")
    suspend fun keep(id: Long)

    /** Radio modules that were not kept and have dropped out of the last [historySize] played. */
    @Query(
        "SELECT * FROM library WHERE kept = 0 AND id NOT IN " +
            "(SELECT id FROM library ORDER BY COALESCE(lastPlayedAt, addedAt) DESC LIMIT :historySize)",
    )
    suspend fun expiredRadioModules(historySize: Int): List<LibraryModule>

    @Query("SELECT sourceKey FROM library WHERE kept = 1 AND sourceId = :sourceId")
    fun keysOf(sourceId: String): Flow<List<String>>

    @Query("SELECT * FROM library WHERE sourceId = :sourceId AND sourceKey = :sourceKey LIMIT 1")
    suspend fun find(sourceId: String, sourceKey: String): LibraryModule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(module: LibraryModule): Long

    @Query("DELETE FROM library WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE library SET lastPlayedAt = :now, playCount = playCount + 1 WHERE localPath = :localPath")
    suspend fun markPlayed(localPath: String, now: Long)
}

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id AS id, p.name AS name, " +
            "(SELECT COUNT(*) FROM playlist_entry e JOIN library l ON l.id = e.moduleId WHERE e.playlistId = p.id) AS count " +
            "FROM playlist p ORDER BY p.name COLLATE NOCASE",
    )
    fun playlists(): Flow<List<PlaylistSummary>>

    @Query("SELECT l.* FROM library l JOIN playlist_entry e ON e.moduleId = l.id WHERE e.playlistId = :playlistId ORDER BY e.position")
    fun modules(playlistId: Long): Flow<List<LibraryModule>>

    @Insert
    suspend fun insert(playlist: Playlist): Long

    /** Appends the module; does nothing if it is in the playlist already. */
    @Query(
        "INSERT OR IGNORE INTO playlist_entry(playlistId, moduleId, position) VALUES(:playlistId, :moduleId, " +
            "COALESCE((SELECT MAX(position) FROM playlist_entry WHERE playlistId = :playlistId), -1) + 1)",
    )
    suspend fun add(playlistId: Long, moduleId: Long)

    @Query("DELETE FROM playlist_entry WHERE playlistId = :playlistId AND moduleId = :moduleId")
    suspend fun remove(playlistId: Long, moduleId: Long)

    @Query("DELETE FROM playlist_entry WHERE moduleId = :moduleId")
    suspend fun removeEverywhere(moduleId: Long)

    @Query("DELETE FROM playlist_entry WHERE playlistId = :playlistId")
    suspend fun clear(playlistId: Long)

    @Query("DELETE FROM playlist WHERE id = :playlistId")
    suspend fun delete(playlistId: Long)
}

@Database(
    entities = [IndexEntry::class, IndexFts::class, LibraryModule::class, Playlist::class, PlaylistEntry::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun indexDao(): IndexDao
    abstract fun libraryDao(): LibraryDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        /** Version 2 adds the `kept` flag for modules fetched by the radio. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library ADD COLUMN kept INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** Version 3 adds playlists. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_entry` (`playlistId` INTEGER NOT NULL, `moduleId` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `moduleId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_entry_moduleId` ON `playlist_entry` (`moduleId`)")
            }
        }
    }
}
