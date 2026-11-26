// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.yoldaskelbow.amplatypus.R
import io.github.yoldaskelbow.amplatypus.data.Library
import io.github.yoldaskelbow.amplatypus.data.LibraryModule
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * What Android Auto and other media browsers can browse: favourites, recently played, the whole library
 * and the playlists, all from the modules on the device. Archive search and the radio need the app.
 *
 * A module in a list carries the list in its id, so that tapping it plays the whole list from there.
 * The player itself only ever sees plain items whose id is the module's file path.
 */
class LibraryBrowser(private val context: Context, private val library: Library) {
    fun root(): MediaItem = folder(ROOT, context.getString(R.string.app_name))

    suspend fun children(parentId: String): List<MediaItem> = when (parentId) {
        ROOT -> listOf(
            folder(FAVORITES, context.getString(R.string.library_favorites)),
            folder(RECENT, context.getString(R.string.radio_history)),
            folder(LIBRARY, context.getString(R.string.nav_library)),
            folder(PLAYLISTS, context.getString(R.string.playlists)),
        )
        PLAYLISTS -> library.playlists.first().map { folder(PLAYLIST + it.id, it.name) }
        else -> modulesOf(parentId).map { it.toMediaItem(listId = parentId) }
    }

    /** A single item by its id, as browsers ask for it. */
    suspend fun item(mediaId: String): MediaItem? {
        if (mediaId == ROOT) return root()
        val (listId, path) = split(mediaId) ?: return children(ROOT).firstOrNull { it.mediaId == mediaId }
        return modulesOf(listId).firstOrNull { it.localPath == path }?.toMediaItem(listId)
    }

    /**
     * For a module tapped in a list: the list as player items and the tapped one's place in it. Null for
     * ids that are no module of a list (the app's own items are plain file paths).
     */
    suspend fun queueFor(mediaId: String): Pair<List<MediaItem>, Int>? {
        val (listId, path) = split(mediaId) ?: return null
        val modules = modulesOf(listId)
        val index = modules.indexOfFirst { it.localPath == path }
        if (index < 0) return null
        return modules.map { it.toMediaItem(listId = null) } to index
    }

    /**
     * Modules whose title, composer or format contain every word of [query], for voice commands and the
     * browser's search. Without words ("play Amplatypus"): the favourites shuffled, or the whole library.
     */
    suspend fun search(query: String): List<LibraryModule> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val all = library.modules.first().filter { File(it.localPath).isFile }
        if (words.isEmpty()) return all.filter { it.favorite }.ifEmpty { all }.shuffled()
        return all.filter { module ->
            val text = "${module.title} ${module.composer.orEmpty()} ${module.format}".lowercase()
            words.all { it in text }
        }
    }

    /** Search results as player items. */
    suspend fun searchQueue(query: String): List<MediaItem> = search(query).map { it.toMediaItem(listId = null) }

    /** Turns a browser's item into one the player can play: its id is the file path. */
    suspend fun playable(item: MediaItem): MediaItem {
        val (_, path) = split(item.mediaId) ?: return item
        return library.byLocalPath(path).first()?.toMediaItem(listId = null) ?: item.buildUpon().setMediaId(path).build()
    }

    private suspend fun modulesOf(listId: String): List<LibraryModule> {
        val modules = when {
            listId == FAVORITES -> library.modules.first().filter { it.favorite }
            listId == RECENT -> library.history.first()
            listId == LIBRARY -> library.modules.first()
            listId.startsWith(SEARCH) -> search(listId.removePrefix(SEARCH))
            listId.startsWith(PLAYLIST) -> listId.removePrefix(PLAYLIST).toLongOrNull()
                ?.let { library.playlistModules(it).first() }
                .orEmpty()
            else -> emptyList()
        }
        // Only what can play without the network.
        return modules.filter { File(it.localPath).isFile }
    }

    private fun folder(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(if (id == PLAYLISTS || id == ROOT) MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS else MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .build(),
        )
        .build()

    private fun LibraryModule.toMediaItem(listId: String?) = MediaItem.Builder()
        .setMediaId(if (listId == null) localPath else listId + SEPARATOR + localPath)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(composer)
                .setAlbumTitle(format)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

    private fun split(mediaId: String): Pair<String, String>? {
        val at = mediaId.indexOf(SEPARATOR)
        return if (at <= 0) null else mediaId.substring(0, at) to mediaId.substring(at + 1)
    }

    companion object {
        const val ROOT = "root"
        const val FAVORITES = "favorites"
        const val RECENT = "recent"
        const val LIBRARY = "library"
        const val PLAYLISTS = "playlists"
        const val PLAYLIST = "playlist:"
        const val SEARCH = "search:"

        /** Between a list's id and a module's path; a character no file path contains. */
        const val SEPARATOR = '\u001f'
    }
}
