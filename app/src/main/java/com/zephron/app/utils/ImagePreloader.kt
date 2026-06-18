package com.zephron.app.utils

import android.content.Context
import coil.Coil
import coil.request.ImageRequest

object ImagePreloader {

    fun preload(context: Context, urls: List<String>, limit: Int = urls.size) {
        val loader = Coil.imageLoader(context)
        urls.take(limit).forEach { url ->
            if (url.isBlank()) return@forEach
            val req = ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(url)
                .build()
            loader.enqueue(req)
        }
    }
}
