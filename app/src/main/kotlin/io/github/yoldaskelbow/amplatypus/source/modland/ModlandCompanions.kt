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

    /** True for files that belong to a song and are not listed on their own. */
    fun isCompanion(format: String, path: String): Boolean =
        rules.any { format in it.formats && it.isCompanion(fileName(path)) }

    /** The files to download along with the song at [path] (a Modland path, format folder first). */
    fun companionsOf(path: String): List<Companion> {
        val format = path.substringBefore('/')
        val name = fileName(path)
        return rules.filter { format in it.formats && it.isSong(name) }
            .map { Companion(path.substringBeforeLast('/') + "/" + it.companionOf(name), it.required) }
    }
}
