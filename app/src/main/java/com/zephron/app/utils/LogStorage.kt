package com.zephron.app.utils

import android.content.Context
import android.util.Log
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LogStorage {
    private const val TAG = "LogStorage"
    private const val LOG_FILE_NAME = "app_logs.txt"

    fun logToFile(context: Context, message: String, throwable: Throwable? = null) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logMessage = "[$timestamp] $message\n" + (throwable?.let { Log.getStackTraceString(it) + "\n" } ?: "")
            
            // Log to internal files dir (private, but reliable)
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            logFile.appendText(logMessage)
            
            Log.d(TAG, "Logged to file: ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    fun shareLogs(context: Context) {
        val logFile = File(context.filesDir, LOG_FILE_NAME)
        if (!logFile.exists()) {
            logToFile(context, "Log file shared, but it was empty/new.")
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Logs senden"))
    }
}
