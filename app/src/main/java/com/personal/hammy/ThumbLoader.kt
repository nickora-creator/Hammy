package com.personal.hammy

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Lightweight thumbnail loader for TV cards (public HTTPS image URLs only). */
object ThumbLoader {
    private val executor = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())
    private val cache = ConcurrentHashMap<String, Bitmap>()

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun load(view: ImageView, url: String?) {
        view.setImageDrawable(null)
        view.tag = url
        if (url.isNullOrBlank()) return

        cache[url]?.let {
            view.setImageBitmap(it)
            return
        }

        val ref = WeakReference(view)
        executor.execute {
            val bmp = download(url) ?: return@execute
            cache.putIfAbsent(url, bmp)
            main.post {
                val v = ref.get() ?: return@post
                if (v.tag == url) {
                    v.setImageBitmap(cache[url] ?: bmp)
                }
            }
        }
    }

    private fun download(url: String): Bitmap? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "image/webp,image/*,*/*")
                setRequestProperty("Referer", "https://xhamster.com/")
            }
            try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.use { stream ->
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
