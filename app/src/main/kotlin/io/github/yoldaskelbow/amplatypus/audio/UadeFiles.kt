// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.audio

import android.content.Context
import io.github.yoldaskelbow.amplatypus.BuildConfig
import java.io.File

/**
 * UADE reads its players, sound core and configuration from real files, so they are copied out of
 * the APK's assets once per app version. The emulator itself (uadecore) is installed by Android
 * into the native library folder as libuadecore.so.
 */
object UadeFiles {
    private const val ASSET_ROOT = "uade"
    private const val MARKER = ".complete"
    private var ready = false

    /** Makes sure the files are in place and tells the native side where. Cheap after the first call. */
    @Synchronized
    fun ensureReady(context: Context) {
        if (ready) return
        try {
            val target = File(context.filesDir, "uade-${BuildConfig.VERSION_CODE}-${BuildConfig.FLAVOR}")
            if (!File(target, MARKER).isFile) {
                target.deleteRecursively()
                copy(context, ASSET_ROOT, target)
                File(target, MARKER).writeText("")
            }
            // Left over from older versions.
            context.filesDir.listFiles { file -> file.name.startsWith("uade-") && file != target }
                ?.forEach { it.deleteRecursively() }
            val core = File(context.applicationInfo.nativeLibraryDir, "libuadecore.so")
            if (core.isFile) OpenMpt.nativeSetupUade(target.path, core.path)
        } catch (_: Exception) {
            // Without these files the UADE formats do not play; everything else is unaffected.
        }
        ready = true
    }

    private fun copy(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> target.outputStream().use { input.copyTo(it) } }
        } else {
            target.mkdirs()
            for (child in children) copy(context, "$assetPath/$child", File(target, child))
        }
    }
}
