package com.zephron.app.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import kotlin.random.Random

object NotificationScheduler {

    const val CHANNEL_ID = "recipe_of_day"
    const val NOTIFICATION_ID = 2001
    private const val REQUEST_CODE = 2001

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context)

        // Random time between 12:00 and 17:59
        val triggerAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12 + Random.nextInt(6))
            set(Calendar.MINUTE, Random.nextInt(60))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Already past today → push to tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() ->
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            else ->
                // Inexact but still wakes device — may fire a few minutes late, good enough
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun isAlreadyScheduled(context: Context): Boolean =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, RecipeNotificationReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rezept des Tages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Tägliche Rezept-Empfehlung" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildPendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, RecipeNotificationReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
