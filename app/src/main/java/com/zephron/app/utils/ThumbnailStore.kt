package com.zephron.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves recipe thumbnails as JPEG files in the app's permanent files directory
 * (filesDir/thumbnails/). Unlike Coil's HTTP disk cache, these files are never
 * evicted by the OS and survive app restarts without any network round-trip.
 *
 * Usage:
 *   val localPath = ThumbnailStore.save(context, bytes, docId)
 *   // store localPath ("file:///...") in Room thumbnailUrl field
 *   // Coil loads file:// URIs natively, so no UI changes needed
 */
object ThumbnailStore {

    private const val TAG = "ThumbnailStore"
    private const val DIR = "thumbnails"

    /** Max longest edge in pixels before down-sampling. */
    private const val MAX_PX = 480

    /** JPEG quality (0–100). 82 gives a good size/quality balance. */
    private const val JPEG_QUALITY = 82

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Decode [bytes], down-sample if larger than [MAX_PX], save as
     * filesDir/thumbnails/{docId}.jpg and return a "file://…" URI string.
     * Returns null on any error.
     */
    suspend fun save(context: Context, bytes: ByteArray, docId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = thumbnailDir(context)
                val file = File(dir, "${sanitise(docId)}.jpg")

                val compressed = compress(bytes)
                file.writeBytes(compressed)

                "file://${file.absolutePath}".also {
                    Log.d(TAG, "Saved thumbnail: $it (${compressed.size / 1024} KB)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save thumbnail for $docId", e)
                null
            }
        }

    /**
     * Download [remoteUrl], compress and save locally.
     * Returns the "file://…" path, or [remoteUrl] as fallback on failure.
     */
    suspend fun download(context: Context, remoteUrl: String, docId: String): String =
        withContext(Dispatchers.IO) {
            try {
                val bytes = java.net.URL(remoteUrl).openStream().use { it.readBytes() }
                save(context, bytes, docId) ?: remoteUrl
            } catch (e: Exception) {
                Log.w(TAG, "Failed to download thumbnail from $remoteUrl", e)
                remoteUrl
            }
        }

    /**
     * Returns the local "file://…" path if already cached, null otherwise.
     */
    fun localPath(context: Context, docId: String): String? {
        val file = File(thumbnailDir(context), "${sanitise(docId)}.jpg")
        return if (file.exists()) "file://${file.absolutePath}" else null
    }

    /**
     * Migrate existing recipes that still point to remote URLs.
     * Downloads and saves each one locally, then updates Room.
     * Call once from Application startup in a background coroutine.
     */
    suspend fun migrateExisting(
        context: Context,
        dao: com.zephron.app.data.RecipeDao
    ) = withContext(Dispatchers.IO) {
        val recipes = dao.getAllRecipesOnce()
        val toMigrate = recipes.filter { r ->
            r.thumbnailUrl.isNotBlank() && !r.thumbnailUrl.startsWith("file://")
        }
        if (toMigrate.isEmpty()) return@withContext
        Log.d(TAG, "Migrating ${toMigrate.size} remote thumbnails to local storage…")

        toMigrate.forEach { recipe ->
            try {
                val isFirebase = recipe.thumbnailUrl.contains("firebasestorage.googleapis.com")
                val docId = docIdFor(recipe)
                if (localPath(context, docId) != null) return@forEach  // already cached

                val localUri = download(context, recipe.thumbnailUrl, docId)
                if (!isFirebase && localUri.startsWith("file://")) {
                    // Non-Firebase URLs: update Room so Coil uses the local file directly.
                    // Firebase URLs: keep Room unchanged — LocalThumbnailFetcher intercepts.
                    dao.update(recipe.copy(thumbnailUrl = localUri))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Migration failed for ${recipe.title}", e)
            }
        }
        Log.d(TAG, "Migration complete.")
    }

    /**
     * Derives the local-cache docId for a recipe.
     * - Firebase Storage URLs: extract the filename from the storage object path
     *   (same logic as LocalThumbnailFetcher so the key always matches).
     * - All other URLs: base64 of the recipe's source URL.
     */
    private fun docIdFor(recipe: com.zephron.app.data.Recipe): String {
        val thumb = recipe.thumbnailUrl
        if (thumb.contains("firebasestorage.googleapis.com")) {
            try {
                val uri = android.net.Uri.parse(thumb)
                val obj = uri.getQueryParameter("o") ?: uri.pathSegments.lastOrNull() ?: ""
                val filename = obj.substringAfterLast("/").substringAfterLast("%2F")
                val docId = filename.removeSuffix(".jpg")
                if (docId.isNotBlank()) return docId
            } catch (_: Exception) {}
        }
        return java.util.Base64.getUrlEncoder().encodeToString(recipe.url.trim().toByteArray())
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun thumbnailDir(context: Context): File =
        File(context.filesDir, DIR).also { it.mkdirs() }

    private fun sanitise(docId: String): String =
        docId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)

    private fun compress(bytes: ByteArray): ByteArray {
        // Decode just the bounds first (no pixel allocation)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

        val srcW = opts.outWidth
        val srcH = opts.outHeight

        // Calculate inSampleSize so the decoded bitmap fits within MAX_PX
        var sample = 1
        if (srcW > MAX_PX || srcH > MAX_PX) {
            val halfW = srcW / 2
            val halfH = srcH / 2
            while (halfW / sample >= MAX_PX && halfH / sample >= MAX_PX) sample *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565 // saves memory vs ARGB_8888
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: return bytes // give up, return original

        // Fine-scale to MAX_PX if still over
        val scaled = if (bmp.width > MAX_PX || bmp.height > MAX_PX) {
            val ratio = MAX_PX.toFloat() / maxOf(bmp.width, bmp.height)
            val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
            val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
            val s = Bitmap.createScaledBitmap(bmp, w, h, true)
            bmp.recycle()
            s
        } else bmp

        return java.io.ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            scaled.recycle()
            out.toByteArray()
        }
    }
}
