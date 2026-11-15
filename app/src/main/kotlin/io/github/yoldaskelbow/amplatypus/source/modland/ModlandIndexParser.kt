// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.source.modland

/**
 * Parses lines of Modland's `allmods.txt`. Each line is `<size in bytes><TAB><path>`, and the path is
 * `Format/Composer/[sub folders/]file.ext`, for example `146528<TAB>Protracker/4-Mat/1978.mod`.
 */
object ModlandIndexParser {
    data class Line(
        val path: String,
        val format: String,
        val composer: String,
        val title: String,
        val ext: String,
        val sizeBytes: Long,
    )

    /** Returns null for lines that do not follow the layout (blank, no size, fewer than three path parts). */
    fun parse(line: String): Line? {
        val tab = line.indexOf('\t')
        if (tab <= 0) return null
        val size = line.substring(0, tab).trim().toLongOrNull() ?: return null
        val path = line.substring(tab + 1).trim()
        val parts = path.split('/')
        if (parts.size < 3 || parts.any { it.isEmpty() }) return null
        val file = parts.last()
        val dot = file.lastIndexOf('.')
        if (dot <= 0 || dot == file.length - 1) return null
        return Line(
            path = path,
            format = parts[0],
            composer = parts[1],
            title = file.substring(0, dot),
            ext = file.substring(dot + 1).lowercase(),
            sizeBytes = size,
        )
    }

    /**
     * Turns what the user typed into an FTS4 prefix query: `purple mot` becomes `purple* mot*`.
     * Returns null if nothing searchable is left.
     */
    fun ftsQuery(text: String): String? {
        val tokens = text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    /** Percent-encodes a path for use in a URL, keeping the slashes. */
    fun encodePath(path: String): String = path.split('/').joinToString("/") { segment ->
        buildString {
            for (byte in segment.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt().toChar()
                if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-._~") {
                    append(c)
                } else {
                    append('%').append("%02X".format(byte.toInt() and 0xFF))
                }
            }
        }
    }
}
