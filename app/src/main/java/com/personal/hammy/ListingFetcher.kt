package com.personal.hammy

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Loads an official listing/category/search page over HTTPS and extracts publicly embedded
 * card metadata (title, thumbnail, video page URL) from window.initials.
 * No CDN scrape, no unofficial API — same HTML a desktop browser would receive.
 */
object ListingFetcher {
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val initialsPattern = Pattern.compile(
        "window\\.initials\\s*=\\s*\\{",
        Pattern.CASE_INSENSITIVE
    )

    private val durationKeyHints = listOf(
        "duration", "durationMS", "durationMs", "duration_ms",
        "videoDuration", "length", "time", "durationSec", "durationSeconds"
    )

    /**
     * @param requireAllSlugs when non-empty, prefer the server search URL results, then
     *   post-filter client-side to videos whose tags/categories include every slug
     *   (when tag metadata is present on the thumbs).
     */
    fun fetch(
        listingUrl: String,
        requireAllSlugs: Collection<String> = emptyList()
    ): Result<List<VideoItem>> = runCatching {
        val html = download(listingUrl)
        val json = extractInitialsJson(html)
            ?: error("Could not find listing data on page")
        var items = collectVideoThumbs(JSONObject(json))
        if (requireAllSlugs.isNotEmpty()) {
            items = maybePostFilterAll(items, requireAllSlugs)
        }
        items
    }

    private fun download(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 25000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                ?: error("HTTP $code")
            val body = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) error("HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun extractInitialsJson(html: String): String? {
        val matcher = initialsPattern.matcher(html)
        if (!matcher.find()) return null
        val start = matcher.end() - 1 // points at '{'
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until html.length) {
            val ch = html[i]
            if (inString) {
                when {
                    escape -> escape = false
                    ch == '\\' -> escape = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun collectVideoThumbs(root: JSONObject): List<VideoItem> {
        val out = LinkedHashMap<String, VideoItem>()
        walk(root) { obj ->
            val arr = obj.optJSONArray("videoThumbProps") ?: return@walk
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val pageUrl = item.optString("pageURL").ifBlank { null } ?: continue
                if (!pageUrl.contains("/videos/")) continue
                val title = item.optString("title").ifBlank { "Video" }
                val thumb = sequenceOf("thumbURL", "previewThumbURL", "imageURL")
                    .map { item.optString(it) }
                    .firstOrNull { it.isNotBlank() }
                    ?: ""
                val durationLabel = extractDurationLabel(item)
                val tags = extractTags(item)
                out.putIfAbsent(
                    pageUrl,
                    VideoItem(
                        title = title,
                        thumbUrl = thumb,
                        pageUrl = pageUrl,
                        durationLabel = durationLabel,
                        tags = tags
                    )
                )
            }
        }
        return out.values.toList()
    }

    private fun extractTags(item: JSONObject): Set<String> {
        val out = linkedSetOf<String>()
        for (key in listOf("categories", "tags", "categoryList", "tagList", "cats")) {
            val arr = item.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                when (val el = arr.opt(i)) {
                    is String -> if (el.isNotBlank()) out.add(el.trim())
                    is JSONObject -> {
                        sequenceOf("slug", "name", "title", "text", "label")
                            .map { el.optString(it) }
                            .firstOrNull { it.isNotBlank() }
                            ?.let { out.add(it.trim()) }
                    }
                }
            }
        }
        item.optString("category").takeIf { it.isNotBlank() }?.let { out.add(it.trim()) }
        item.optJSONObject("category")?.let { cat ->
            sequenceOf("slug", "name", "title")
                .map { cat.optString(it) }
                .firstOrNull { it.isNotBlank() }
                ?.let { out.add(it.trim()) }
        }
        return out
    }

    /**
     * When thumbs include categories/tags, keep only those that match every required slug.
     * If no tag metadata is present, keep server results as-is (search URL is the primary filter).
     */
    private fun maybePostFilterAll(
        items: List<VideoItem>,
        requireAllSlugs: Collection<String>
    ): List<VideoItem> {
        val required = requireAllSlugs.map { normalizeTag(it) }.filter { it.isNotEmpty() }
        if (required.isEmpty()) return items
        val taggedCount = items.count { it.tags.isNotEmpty() }
        if (taggedCount == 0) return items

        return items.filter { item ->
            if (item.tags.isEmpty()) return@filter false
            val normalized = item.tags.map { normalizeTag(it) }.toSet()
            required.all { req ->
                normalized.any { tag -> tagsMatch(tag, req) }
            }
        }
    }

    private fun normalizeTag(raw: String): String =
        raw.trim().lowercase().replace('_', '-')

    private fun tagsMatch(tag: String, required: String): Boolean {
        if (tag == required) return true
        val tagDashed = tag.replace(' ', '-')
        val reqDashed = required.replace(' ', '-')
        if (tagDashed == reqDashed) return true
        val tagSpaced = tag.replace('-', ' ')
        val reqSpaced = required.replace('-', ' ')
        return tagSpaced == reqSpaced
    }

    private fun extractDurationLabel(item: JSONObject): String {
        // Prefer already-formatted strings like "12:34" or "1:05:02"
        for (key in durationKeyHints) {
            if (!item.has(key)) continue
            val raw = item.opt(key) ?: continue
            when (raw) {
                is String -> {
                    val s = raw.trim()
                    if (s.isEmpty() || s == "null") continue
                    if (s.contains(':')) return s
                    parseNumericDuration(s, key)?.let { return it }
                }
                is Number -> {
                    formatSeconds(normalizeToSeconds(raw.toDouble(), key))?.let { return it }
                }
            }
        }
        // Nested objects / alternate structures
        for (key in listOf("duration", "video", "meta", "info")) {
            val nested = item.optJSONObject(key) ?: continue
            extractDurationLabel(nested).takeIf { it.isNotEmpty() }?.let { return it }
        }
        // Scan any remaining keys that look like duration
        val keys = item.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.contains("duration", ignoreCase = true) &&
                !key.equals("length", ignoreCase = true)
            ) continue
            if (key in durationKeyHints) continue
            val raw = item.opt(key) ?: continue
            when (raw) {
                is String -> {
                    val s = raw.trim()
                    if (s.contains(':')) return s
                    parseNumericDuration(s, key)?.let { return it }
                }
                is Number -> {
                    formatSeconds(normalizeToSeconds(raw.toDouble(), key))?.let { return it }
                }
            }
        }
        return ""
    }

    private fun parseNumericDuration(raw: String, key: String): String? {
        val n = raw.toDoubleOrNull() ?: return null
        return formatSeconds(normalizeToSeconds(n, key))
    }

    private fun normalizeToSeconds(value: Double, key: String): Double {
        val k = key.lowercase()
        return when {
            k.contains("ms") -> value / 1000.0
            // Large values without unit hint are almost always milliseconds
            value >= 10_000 -> value / 1000.0
            else -> value
        }
    }

    private fun formatSeconds(totalSeconds: Double): String? {
        if (totalSeconds.isNaN() || totalSeconds <= 0) return null
        val secs = totalSeconds.toLong()
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }

    private fun walk(node: Any?, onObject: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                onObject(node)
                val keys = node.keys()
                while (keys.hasNext()) {
                    walk(node.opt(keys.next()), onObject)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    walk(node.opt(i), onObject)
                }
            }
        }
    }
}
