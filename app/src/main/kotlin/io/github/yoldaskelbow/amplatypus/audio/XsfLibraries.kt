// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.audio

/**
 * xSF files (GSF, 2SF, USF, SSF, DSF, and the PSF family they come from) keep most of a game's music in
 * library files that the songs name in their tags: `_lib=game.gsflib`, and further ones as `_lib2`,
 * `_lib3`... A library can name libraries of its own. They are looked for next to the song.
 *
 * Layout: "PSF", a version byte, the sizes of the reserved and the program section (32-bit, little
 * endian), a checksum, the two sections, then optionally "[TAG]" and lines of `name=value`.
 */
object XsfLibraries {
    private const val HEADER = 16
    private val TAG_MARK = "[TAG]".toByteArray(Charsets.US_ASCII)
    private val LIB_KEY = Regex("_lib(\\d*)")

    /** The library file names [file] names, `_lib` first, then `_lib2`, `_lib3`... in order. */
    fun namesIn(file: ByteArray): List<String> {
        if (file.size < HEADER || file[0] != 'P'.code.toByte() || file[1] != 'S'.code.toByte() || file[2] != 'F'.code.toByte()) {
            return emptyList()
        }
        val reserved = u32(file, 4)
        val program = u32(file, 8)
        val tagStart = HEADER.toLong() + reserved + program
        if (tagStart < HEADER || tagStart + TAG_MARK.size > file.size) return emptyList()
        val at = tagStart.toInt()
        for (i in TAG_MARK.indices) if (file[at + i] != TAG_MARK[i]) return emptyList()
        val text = String(file, at + TAG_MARK.size, file.size - at - TAG_MARK.size, Charsets.UTF_8)
        return text.split('\n')
            .mapNotNull { line ->
                val eq = line.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val match = LIB_KEY.matchEntire(line.substring(0, eq).trim().lowercase()) ?: return@mapNotNull null
                val number = match.groupValues[1].toIntOrNull() ?: 1
                val name = line.substring(eq + 1).trim()
                if (isSafe(name)) number to name else null
            }
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
    }

    /** Only plain names (and sub folders) beside the song: nothing that leaves its folder. */
    private fun isSafe(name: String): Boolean =
        name.isNotEmpty() && !name.startsWith('/') && !name.startsWith('\\') &&
            name.split('/', '\\').none { it == ".." || it.isEmpty() }

    private fun u32(bytes: ByteArray, at: Int): Long =
        (bytes[at].toLong() and 0xFF) or
            ((bytes[at + 1].toLong() and 0xFF) shl 8) or
            ((bytes[at + 2].toLong() and 0xFF) shl 16) or
            ((bytes[at + 3].toLong() and 0xFF) shl 24)
}
