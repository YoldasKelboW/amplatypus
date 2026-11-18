// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.source.modland

/**
 * Some Amiga formats keep a song in two files: the notes and the samples. Modland lists both, side by
 * side in the same folder. The app shows only the song file and downloads its companion with it, into
 * the same folder, where UADE's replay routine looks for it.
 */
object ModlandCompanions {
    private class Rule(
        /** Modland format folders the rule is for. */
        val formats: Set<String>,
        /** Is this file name the song? */
        val isSong: (String) -> Boolean,
        /** The companion's file name for a song file name. */
        val companionOf: (String) -> String,
        /** Is this file name a companion (not shown in the index)? */
        val isCompanion: (String) -> Boolean,
        /** A song plays without its companion when this is false; a missing companion is then no error. */
        val required: Boolean,
    )

    private val rules = listOf(
        // TFMX: "mdat.name" holds the notes, "smpl.name" the samples. Most TFMX ST songs have no smpl file.
        Rule(
            formats = setOf("TFMX", "TFMX ST"),
            isSong = { it.startsWith("mdat.", ignoreCase = true) },
            companionOf = { "smpl." + it.substring("mdat.".length) },
            isCompanion = { it.startsWith("smpl.", ignoreCase = true) },
            required = false,
        ),
        // Richard Joseph: "name.sng" and "name.ins".
        Rule(
            formats = setOf("Richard Joseph"),
            isSong = { it.endsWith(".sng", ignoreCase = true) },
            companionOf = { it.dropLast(".sng".length) + ".ins" },
            isCompanion = { it.endsWith(".ins", ignoreCase = true) },
            required = true,
        ),
        // Stereo Sidplayer (C64): "name.mus" plays on the first SID, "name.str" on the second.
        Rule(
            formats = setOf("Stereo Sidplayer"),
            isSong = { it.endsWith(".mus", ignoreCase = true) },
            companionOf = { it.dropLast(".mus".length) + ".str" },
            isCompanion = { it.endsWith(".str", ignoreCase = true) },
            required = false,
        ),
        // Audio Sculpture: "name.adsc" (or "name.mod") and "name.adsc.as".
        Rule(
            formats = setOf("Audio Sculpture"),
            isSong = { !it.endsWith(".as", ignoreCase = true) },
            companionOf = { "$it.as" },
            isCompanion = { it.endsWith(".as", ignoreCase = true) },
            required = true,
        ),
    )

    data class Companion(val path: String, val required: Boolean)

    private fun fileName(path: String) = path.substringAfterLast('/')

    /**
     * Formats whose songs need a whole folder of instrument files, with the extension of the song file.
     * IFF-SMUS (Sonix) keeps each song next to an "Instruments" folder of .instr and .ss files.
     */
    private val folderFormats = mapOf("IFF-SMUS" to "smus")

    /** True for files that belong to a song and are not listed on their own. */
    fun isCompanion(format: String, path: String): Boolean =
        rules.any { format in it.formats && it.isCompanion(fileName(path)) } || isFolderFile(format, path)

    /** An instrument file of a folder format: kept aside to be fetched with its song, not listed. */
    fun isFolderFile(format: String, path: String): Boolean {
        val songExtension = folderFormats[format] ?: return false
        return !fileName(path).endsWith(".$songExtension", ignoreCase = true)
    }

    /**
     * The instrument files of the folder-format song at [path], picked from [folderFiles] (all
     * instrument files Modland lists for such formats): everything below the song's own folder.
     */
    fun folderCompanionsOf(path: String, folderFiles: List<String>): List<Companion> {
        val format = path.substringBefore('/')
        val songExtension = folderFormats[format] ?: return emptyList()
        if (!fileName(path).endsWith(".$songExtension", ignoreCase = true)) return emptyList()
        val dir = path.substringBeforeLast('/') + "/"
        return folderFiles.filter { it.startsWith(dir, ignoreCase = true) }.map { Companion(it, required = false) }
    }

    /** The files to download along with the song at [path] (a Modland path, format folder first). */
    fun companionsOf(path: String): List<Companion> {
        val format = path.substringBefore('/')
        val name = fileName(path)
        return rules.filter { format in it.formats && it.isSong(name) }
            .map { Companion(path.substringBeforeLast('/') + "/" + it.companionOf(name), it.required) }
    }
}
