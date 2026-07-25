package by.mlastovsky.kosht.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import by.mlastovsky.kosht.data.AppSettings
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Keeps WorkManager jobs in sync with the notification settings. Periodic
 * work is not exact-time, which is fine for reminders.
 */
object NotificationScheduler {

    private const val WORK_DAILY = "daily_reminder"
    private const val WORK_RECURRING = "recurring_due"
    private const val WORK_WEEKLY = "weekly_summary"

    fun sync(context: Context, settings: AppSettings) {
        val manager = WorkManager.getInstance(context)

        syncPeriodic<DailyReminderWorker>(
            manager, WORK_DAILY, settings.notifyDailyReminder,
            repeatDays = 1,
            initialDelay = delayUntil(LocalTime.of(20, 0))
        )
        syncPeriodic<RecurringDueWorker>(
            manager, WORK_RECURRING, settings.notifyRecurringDue,
            repeatDays = 1,
            initialDelay = delayUntil(LocalTime.of(10, 0))
        )
        syncPeriodic<WeeklySummaryWorker>(
            manager, WORK_WEEKLY, settings.notifyWeeklySummary,
            repeatDays = 7,
            initialDelay = delayUntilNext(DayOfWeek.MONDAY, LocalTime.of(10, 0))
        )
    }

    private inline fun <reified W : androidx.work.ListenableWorker> syncPeriodic(
        manager: WorkManager,
        name: String,
        enabled: Boolean,
        repeatDays: Long,
        initialDelay: Duration
    ) {
        if (!enabled) {
            manager.cancelUniqueWork(name)
            return
        }
        val request: PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<W>(repeatDays, TimeUnit.DAYS)
                .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
        // KEEP: re-enqueueing on every app start must not reset the schedule.
        manager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun delayUntil(time: LocalTime): Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    private fun delayUntilNext(day: DayOfWeek, time: LocalTime): Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate()
            .with(TemporalAdjusters.nextOrSame(day))
            .atTime(time)
        if (!next.isAfter(now)) {
            next = now.toLocalDate().with(TemporalAdjusters.next(day)).atTime(time)
        }
        return Duration.between(now, next)
    }
}
