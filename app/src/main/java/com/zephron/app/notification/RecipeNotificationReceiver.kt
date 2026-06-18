package com.zephron.app.notification

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zephron.app.MainActivity
import com.zephron.app.R
import com.zephron.app.data.Recipe
import com.zephron.app.data.RecipeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecipeNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val recipe = RecipeDatabase.getDatabase(context)
                    .recipeDao()
                    .getRandomRecipe()
                if (recipe != null) showNotification(context, recipe)
                NotificationScheduler.scheduleNext(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val PUNCHY_TITLES = listOf(
            "Heute kochst du das. 🔥",
            "Dein Abendessen hat angerufen. 📞",
            "Hungrig? Wir hätten da was. 😏",
            "Das willst du heute essen. 👇",
            "Kein Stress. Nur ein geiles Rezept. 🍳",
            "Heute Abend gibt's was Ordentliches. 💪",
            "Koch das. Du wirst es nicht bereuen. 🤌",
            "Dein Kühlschrank wartet auf dich. 🧑‍🍳",
            "Zeit, den Herd anzuschmeißen. 🔥",
            "Das ist zu gut zum Ignorieren. 👀"
        )

        fun showNotification(context: Context, recipe: Recipe) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
            ) return

            NotificationScheduler.createChannel(context)

            val title = PUNCHY_TITLES.random()
            val body = if (recipe.cookingTimeMinutes > 0)
                "${recipe.title}  ·  ${recipe.cookingTimeMinutes} Min."
            else
                recipe.title

            val tapIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context)
                .notify(NotificationScheduler.NOTIFICATION_ID, notification)
        }
    }
}
