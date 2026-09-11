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
 * Loads an official listing/category page over HTTPS and extracts publicly embedded
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

    fun fetch(listingUrl: String): Result<List<VideoItem>> = runCatching {
        val html = download(listingUrl)
        val json = extractInitialsJson(html)
            ?: error("Could not find listing data on page")
        val items = collectVideoThumbs(JSONObject(json))
        if (items.isEmpty()) error("No videos found on this page")
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
                out.putIfAbsent(
                    pageUrl,
                    VideoItem(title = title, thumbUrl = thumb, pageUrl = pageUrl)
                )
            }
        }
        return out.values.toList()
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
