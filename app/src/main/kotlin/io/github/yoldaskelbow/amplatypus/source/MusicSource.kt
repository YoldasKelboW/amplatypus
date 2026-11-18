// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.source

import java.io.File

/**
 * A place modules come from: Modland, local files, or another archive later.
 * UI, library, playlists and radio only ever talk to this interface, so sources
 * can be added, disabled or replaced without touching the rest of the app.
 *
 * Draft from Phase 1; signatures may still change when the first source is built.
 */
interface MusicSource {
    /** Stable identifier stored with every module, e.g. "modland", "amp", "local". */
    val id: String

    /** What this source can do, so the UI only offers what works. */
    val capabilities: Set<Capability>

    /** Searches the source. [page] is 0-based; a short or empty page means the end. */
    suspend fun search(query: String, kind: SearchKind, page: Int = 0): List<SourceItem>

    /** Lists the children of a node; `null` lists the top level (e.g. formats or groups). */
    suspend fun browse(parent: SourceItem.Folder? = null, page: Int = 0): List<SourceItem>

    /** Full details of one module, or `null` if the source no longer has it. */
    suspend fun details(ref: ModuleRef): SourceItem.Module?

    /** A random module for the radio, or `null` if the source cannot provide one right now. */
    suspend fun random(): SourceItem.Module?

    /**
     * Downloads one module into [targetDir], unpacking it if the source serves it compressed,
     * and returns the playable file. [onProgress] receives 0.0–1.0, or -1 if the size is unknown.
     */
    suspend fun download(ref: ModuleRef, targetDir: File, onProgress: (Float) -> Unit = {}): File

    /** Files stored next to [file] that belong to it (samples of two-file formats). Deleted with it. */
    fun companionFiles(ref: ModuleRef, file: File): List<File> = emptyList()
}

enum class Capability { SEARCH, BROWSE, RANDOM, LATEST, SIMILAR, TEXT_SEARCH }

enum class SearchKind { MODULE, COMPOSER, GROUP, TEXT }

/** Identifies a module within one source. [key] is the source's own id or path. */
data class ModuleRef(val sourceId: String, val key: String)

sealed interface SourceItem {
    val title: String

    /** A node that can be opened: a format, a composer, a group. */
    data class Folder(
        val sourceId: String,
        val key: String,
        override val title: String,
        val kind: FolderKind,
        val subtitle: String? = null,
        /** A short extra line, e.g. a composer's country. */
        val detail: String? = null,
    ) : SourceItem

    data class Module(
        val ref: ModuleRef,
        override val title: String,
        val composer: String?,
        val format: String,
        val sizeBytes: Long? = null,
        val durationSeconds: Int? = null,
    ) : SourceItem
}

enum class FolderKind { FORMAT, COMPOSER, GROUP }
