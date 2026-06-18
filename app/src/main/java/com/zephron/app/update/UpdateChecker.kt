package com.zephron.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

private const val GITHUB_RELEASES_URL =
    "https://api.github.com/repos/lenzwagner/Saffron-Android/releases/latest"

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

object UpdateChecker {

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val json = URL(GITHUB_RELEASES_URL).readText()
            val root = JSONObject(json)
            val tag = root.getString("tag_name") // e.g. "v43" or "43"
            val remoteCode = tag.trimStart('v').toIntOrNull() ?: return@withContext null
            if (remoteCode <= currentVersionCode) return@withContext null
            val versionName = root.optString("name", tag)
            val assets = root.getJSONArray("assets")
            val apkUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url") ?: return@withContext null
            UpdateInfo(remoteCode, versionName, apkUrl)
        } catch (_: Exception) {
            null
        }
    }

    fun downloadAndInstall(context: Context, apkUrl: String) {
        val fileName = "saffron-update.apk"
        val dest = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (dest.exists()) dest.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Saffron Update")
            .setDescription("Wird heruntergeladen…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(dest))

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        // Register a receiver to trigger install when download completes
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    installApk(ctx, dest)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(
                receiver,
                android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
