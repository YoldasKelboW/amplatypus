// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.net

/** Every remote address the app talks to is defined here and nowhere else. */
object Endpoints {
    /** Complete Modland file list: one zip with a single text file, updated daily. */
    const val MODLAND_INDEX_URL = "https://modland.com/allmods.zip"

    /** Hosts serving the module files over HTTPS, tried in this order. */
    val MODLAND_HOSTS = listOf("modland.com", "ftp.modland.com", "modland.ziphoid.com")

    const val MODLAND_MODULE_PATH = "/pub/modules/"

    /** Amiga Music Preservation: the API, the module downloads and the public pages. */
    const val AMP_API = "https://amp.dascene.net/api/"
    const val AMP_MODULE = "https://amp.dascene.net/module/"
    const val AMP_SITE = "https://amp.dascene.net"

    /** The AMP staff asked for no more than 5 requests per 10 seconds. */
    const val AMP_REQUESTS = 5
    const val AMP_WINDOW_MS = 10_000L

    /** get_similar_modules is costly for the AMP server; the staff allowed it with at most 5 results. */
    const val AMP_SIMILAR_LIMIT = 5
}
