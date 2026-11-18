// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus.source.amp

import io.github.yoldaskelbow.amplatypus.source.FolderKind
import io.github.yoldaskelbow.amplatypus.source.ModuleRef
import io.github.yoldaskelbow.amplatypus.source.SourceItem
import org.json.JSONArray
import org.json.JSONObject

/** Turns the JSON of the AMP API (https://amp.dascene.net/api) into the app's source items. */
object AmpParser {
    /**
     * One module object, or `null` if it is unusable or no replayer in this build plays its format.
     * AMP's `module_type` is the file extension of the module ("MOD", "XM", "FC", ...).
     */
    fun module(json: JSONObject, playable: (String) -> Boolean): SourceItem.Module? {
        val id = json.optLong("module_id", -1)
        val type = json.optString("module_type").trim()
        if (id < 0 || type.isEmpty() || !playable(type.lowercase())) return null
        val authors = json.optJSONArray("authors")
        val composer = buildList {
            for (i in 0 until (authors?.length() ?: 0)) {
                authors!!.optJSONObject(i)?.optString("composer_handle")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }.joinToString(" & ").ifEmpty { null }
        return SourceItem.Module(
            ref = ModuleRef(AmpSource.ID, id.toString()),
            title = json.optString("module_name").ifBlank { "#$id" },
            composer = composer,
            format = type.uppercase(),
            sizeBytes = json.optLong("file_size", -1).takeIf { it > 0 },
            durationSeconds = duration(json.optString("duration")),
        )
    }

    fun modules(array: JSONArray?, playable: (String) -> Boolean): List<SourceItem.Module> =
        (0 until (array?.length() ?: 0)).mapNotNull { array!!.optJSONObject(it)?.let { m -> module(m, playable) } }

    /** search_modules_paged answers with an envelope; the page is the last one when it says so. */
    fun page(json: JSONObject, playable: (String) -> Boolean): Page = Page(
        modules = modules(json.optJSONArray("modules"), playable),
        last = json.optInt("current_page", 1) >= json.optInt("total_page", 1),
    )

    data class Page(val modules: List<SourceItem.Module>, val last: Boolean)

    /** One author object, as search_by_handle and get_author_details return it. */
    fun composer(json: JSONObject): SourceItem.Folder? {
        val id = json.optLong("composer_id", -1)
        val handle = json.optString("composer_handle").trim()
        if (id < 0 || handle.isEmpty()) return null
        val country = json.optJSONArray("country")?.optJSONObject(0)?.optString("country_name")?.takeIf { it.isNotBlank() }
        return SourceItem.Folder(
            sourceId = AmpSource.ID,
            key = id.toString(),
            title = handle,
            kind = FolderKind.COMPOSER,
            subtitle = json.optInt("module_count", -1).takeIf { it >= 0 }?.toString(),
            detail = country,
        )
    }

    fun composers(array: JSONArray?): List<SourceItem.Folder> =
        (0 until (array?.length() ?: 0)).mapNotNull { array!!.optJSONObject(it)?.let(::composer) }

    /** "04:56" or "1:02:03" in seconds; `null` if AMP does not know the length. */
    fun duration(text: String?): Int? {
        val parts = text?.trim()?.split(':')?.map { it.toIntOrNull() ?: return null } ?: return null
        if (parts.isEmpty() || parts.size > 3) return null
        return parts.fold(0) { total, part -> total * 60 + part }.takeIf { it > 0 }
    }

    /** The file name from a Content-Disposition header, made safe to store. */
    fun fileName(contentDisposition: String?, fallback: String): String {
        val raw = contentDisposition
            ?.let { Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            ?: fallback
        return raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("""[\u0000-\u001f:*?"<>|]"""), "_")
            .trim().trimStart('.')
            .ifEmpty { fallback }
    }
}
