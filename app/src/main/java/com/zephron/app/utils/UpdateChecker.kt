package com.zephron.app.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class UpdateInfo(val latestVersion: String, val downloadUrl: String)

object UpdateChecker {
    private const val RELEASES_API =
        "https://api.github.com/repos/lenzwagner/Saffron_Android/releases/latest"

    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(URL(RELEASES_API).readText())
            val tag = json.getString("tag_name").trimStart('v')
            if (isNewer(tag, currentVersion)) {
                val assets = json.getJSONArray("assets")
                val apkUrl = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                    ?.getString("browser_download_url")
                    ?: json.getString("html_url")
                UpdateInfo(tag, apkUrl)
            } else null
        } catch (_: Exception) { null }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.split(".").mapNotNull { it.toIntOrNull() }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }
}
