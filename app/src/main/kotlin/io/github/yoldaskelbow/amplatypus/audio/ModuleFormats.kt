// SPDX-License-Identifier: GPL-3.0-or-later
//
// The format list is taken from MPTReplayer.m of 4champ,
// Copyright (c) Aleksi Sitomaniemi, used under the MIT license.
package io.github.yoldaskelbow.amplatypus.audio

object ModuleFormats {
    /** File extensions (lower case) that the bundled replayers can play. */
    val playableExtensions: Set<String> = setOf(
        "667", "669", "amf", "ams", "dbm", "digi", "dmf", "dsm", "dsym", "dtm", "far", "fmt", "fst", "gdm",
        "gtk", "gt2", "ice", "it", "imf", "j2b", "m15", "mdl", "med", "mmcmp", "mms", "mo3", "mod", "mptm",
        "mt2", "mtm", "nst", "oct", "okt", "oxm", "plm", "ppm", "psm", "pt36", "ptm", "s3m", "sfx", "sfx2",
        "st26", "stk", "stm", "stp", "stx", "symmod", "ult", "umx", "wow", "xm", "xmf",
        "mmd0", "mmd1", "mmd2", "mmd3",
    )

    /** Bump when [playableExtensions] changes, so stored indexes are rebuilt. */
    const val VERSION = 1

    fun isPlayable(extension: String): Boolean = extension.lowercase() in playableExtensions
}
