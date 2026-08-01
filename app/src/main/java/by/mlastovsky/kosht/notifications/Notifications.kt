package by.mlastovsky.kosht.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import by.mlastovsky.kosht.MainActivity
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.awards.AwardVisuals

object Notifications {

    const val CHANNEL_REMINDERS = "reminders_v3"
    const val CHANNEL_RECURRING = "recurring_v3"
    const val CHANNEL_SUMMARY = "summary_v3"
    const val CHANNEL_AWARDS = "awards_v3"

    private val legacyChannels = listOf(
        "reminders", "recurring", "recurring_v2", "summary", "awards"
    )

    const val ID_DAILY = 1
    const val ID_RECURRING = 2
    const val ID_WEEKLY = 3
    const val ID_AWARD = 4

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        legacyChannels.forEach(manager::deleteNotificationChannel)
        listOf(
            CHANNEL_REMINDERS to R.string.channel_reminders,
            CHANNEL_RECURRING to R.string.channel_recurring,
            CHANNEL_SUMMARY to R.string.channel_summary,
            CHANNEL_AWARDS to R.string.channel_awards
        ).forEach { (id, nameRes) ->
            manager.createNotificationChannel(audibleChannel(context, id, nameRes))
        }
    }

    private fun audibleChannel(context: Context, id: String, nameRes: Int) =
        NotificationChannel(
            id,
            context.getString(nameRes),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            enableLights(true)
        }

    fun showAward(context: Context, key: String) {
        show(
            context = context,
            id = ID_AWARD,
            channel = CHANNEL_AWARDS,
            title = context.getString(R.string.award_unlocked_title),
            text = context.getString(
                AwardVisuals.titleRes(key)
            ) + ", " + context.getString(
                AwardVisuals.descRes(key)
            )
        )
    }

    fun show(context: Context, id: Int, channel: String, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_kosht)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)

            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
