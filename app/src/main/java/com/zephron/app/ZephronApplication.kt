package com.zephron.app

import android.app.Application
import android.net.Uri
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.key.Keyer
import coil.memory.MemoryCache
import coil.request.Options
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Custom Application that configures Coil with a large disk cache so recipe
 * thumbnails survive app restarts and load instantly without hitting the network again.
 */
class ZephronApplication : Application(), ImageLoaderFactory {

    /**
     * Firebase Storage download URLs contain a rotating `token` query parameter.
     * Strip all query params so the same image always maps to the same cache key,
     * regardless of token rotation.
     */
    private val stableUrlKeyer = object : Keyer<String> {
        override fun key(data: String, options: Options): String {
            return try {
                val uri = Uri.parse(data)
                if (uri.host?.contains("firebasestorage") == true || uri.queryParameterNames.isNotEmpty()) {
                    // Keep only scheme + host + path, drop all query params
                    Uri.Builder()
                        .scheme(uri.scheme)
                        .encodedAuthority(uri.encodedAuthority)
                        .encodedPath(uri.encodedPath)
                        .build()
                        .toString()
                } else {
                    data
                }
            } catch (_: Exception) {
                data
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)   // 25 % of available RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)   // 200 MB
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            }
            .components {
                add(stableUrlKeyer)
            }
            .crossfade(true)
            .build()
    }
}
