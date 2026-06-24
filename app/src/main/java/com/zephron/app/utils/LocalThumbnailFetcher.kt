package com.zephron.app.utils

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.File
import java.net.URL

/**
 * Coil Fetcher for Firebase Storage URLs.
 *
 * Cache hit  → serve JPEG from filesDir/thumbnails/ instantly (no network).
 * Cache miss → download from Firebase, save permanently to filesDir/thumbnails/,
 *              then serve from disk. Survives Coil cache eviction forever.
 */
class LocalThumbnailFetcher(
    private val data: String,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (!data.contains("firebasestorage.googleapis.com")) return null

        val docId = extractDocId(data) ?: return null
        val sanitised = docId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val file = File(context.filesDir, "thumbnails/$sanitised.jpg")

        if (!file.exists()) {
            // Cache miss — download and persist permanently.
            val downloaded = withContext(Dispatchers.IO) {
                try {
                    val bytes = URL(data).openStream().use { it.readBytes() }
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                    true
                } catch (_: Exception) { false }
            }
            if (!downloaded) return null
        }

        return SourceResult(
            source = ImageSource(file.source().buffer(), context),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK
        )
    }

    private fun extractDocId(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            val obj = uri.getQueryParameter("o") ?: uri.pathSegments.lastOrNull() ?: return null
            val filename = obj.substringAfterLast("/").substringAfterLast("%2F")
            filename.removeSuffix(".jpg").takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    class Factory(private val context: Context) : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher =
            LocalThumbnailFetcher(data, context)
    }
}
