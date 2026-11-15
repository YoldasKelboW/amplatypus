// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.net

/** Every remote address the app talks to is defined here and nowhere else. */
object Endpoints {
    /** Complete Modland file list: one zip with a single text file, updated daily. */
    const val MODLAND_INDEX_URL = "https://modland.com/allmods.zip"

    /** Hosts serving the module files over HTTPS, tried in this order. */
    val MODLAND_HOSTS = listOf("modland.com", "ftp.modland.com", "modland.ziphoid.com")

    const val MODLAND_MODULE_PATH = "/pub/modules/"
}
