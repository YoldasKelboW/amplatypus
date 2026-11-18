// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.net

/**
 * Single place that defines how the app identifies itself to servers. The User-Agent names app,
 * version and repository so an operator can tell where requests come from.
 */
data class ApiConfig(
    val appVersion: String,
    val repoUrl: String,
) {
    val userAgent: String
        get() = "$APP_NAME/$appVersion (Android; +$repoUrl)"

    companion object {
        const val APP_NAME = "AMPlatypus"
    }
}
