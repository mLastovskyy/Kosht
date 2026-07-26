package by.mlastovsky.kosht.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import by.mlastovsky.kosht.MainActivity
import by.mlastovsky.kosht.R

object Notifications {

    const val CHANNEL_REMINDERS = "reminders"

    // v2: the original channel was created with IMPORTANCE_HIGH and its
    // importance can no longer be lowered in code — a new id is the only
    // way to drop the heads-up popup for existing installs.
    const val CHANNEL_RECURRING = "recurring_v2"
    const val CHANNEL_SUMMARY = "summary"
    const val CHANNEL_AWARDS = "awards"

    private const val CHANNEL_RECURRING_LEGACY = "recurring"

    const val ID_DAILY = 1
    const val ID_RECURRING = 2
    const val ID_WEEKLY = 3
    const val ID_AWARD = 4

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(CHANNEL_RECURRING_LEGACY)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECURRING,
                context.getString(R.string.channel_recurring),
                // DEFAULT, not HIGH: no heads-up popup over whatever the
                // user is doing — a status-bar entry is enough.
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SUMMARY,
                context.getString(R.string.channel_summary),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AWARDS,
                context.getString(R.string.channel_awards),
                // Silent: earning an award is good news, not urgent news, and
                // the app already says so on screen when it is open.
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    /**
     * "Award earned: Iron month". The award's own wording is reused, so the
     * shade says exactly what the achievements screen says.
     */
    fun showAward(context: Context, key: String) {
        show(
            context = context,
            id = ID_AWARD,
            channel = CHANNEL_AWARDS,
            title = context.getString(R.string.award_unlocked_title),
            text = context.getString(
                by.mlastovsky.kosht.ui.awards.AwardVisuals.titleRes(key)
            ) + " · " + context.getString(
                by.mlastovsky.kosht.ui.awards.AwardVisuals.descRes(key)
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
            // Quiet by design: no full-screen intent, no vibration pattern,
            // and never louder than the channel allows.
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
