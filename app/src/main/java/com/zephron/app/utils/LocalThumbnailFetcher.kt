package com.zephron.app.utils

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.buffer
import okio.source
import java.io.File

/**
 * Coil Fetcher that short-circuits any https:// image request by serving the
 * locally-saved copy from ThumbnailStore if it exists.
 *
 * This makes recipe thumbnails load instantly on every app start — even if
 * the Coil HTTP disk cache was evicted — because the local JPEG is always
 * available without a network round-trip.
 */
class LocalThumbnailFetcher(
    private val data: String,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // Only intercept Firebase Storage URLs.
        if (!data.contains("firebasestorage.googleapis.com")) return null

        // The local file is named after the recipe's url-derived docId (Base64),
        // not the thumbnail URL. We extract the docId from the Storage path:
        // users/{uid}/recipes/{docId}.jpg
        val uri = try { android.net.Uri.parse(data) } catch (_: Exception) { return null }
        val pathSegments = uri.pathSegments
        // Storage path is encoded in the "o" query param or in the path itself.
        // Firebase Storage download URLs look like:
        //   .../o/users%2F{uid}%2Frecipes%2F{docId}.jpg?...
        val storageObject = uri.getQueryParameter("o") ?: pathSegments.lastOrNull() ?: return null
        val filename = storageObject.substringAfterLast("/").substringAfterLast("%2F")
        val docId = filename.removeSuffix(".jpg")
        if (docId.isBlank()) return null

        val sanitised = docId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        val file = File(context.filesDir, "thumbnails/$sanitised.jpg")
        if (!file.exists()) return null

        return SourceResult(
            source = ImageSource(file.source().buffer(), context),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher =
            LocalThumbnailFetcher(data, context)
    }
}
