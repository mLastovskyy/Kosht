package by.mlastovsky.kosht.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import by.mlastovsky.kosht.KoshtApp
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.Money
import java.time.LocalDate
import kotlinx.coroutines.flow.first

private val Context.app get() = applicationContext as KoshtApp

class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = context.app.container.settingsRepository.settings.first()
        if (!settings.notifyDailyReminder) return Result.success()

        val today = LocalDate.now()
        val from = Dates.toEpochMillis(today)
        val to = Dates.toEpochMillis(today.plusDays(1))
        val todayCount = context.app.container.transactionRepository
            .observeBetween(from, to).first().size

        if (todayCount == 0) {
            Notifications.show(
                context,
                Notifications.ID_DAILY,
                Notifications.CHANNEL_REMINDERS,
                context.getString(R.string.notif_daily_title),
                context.getString(R.string.notif_daily_text)
            )
        }
        return Result.success()
    }
}

class RecurringDueWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = context.app.container.settingsRepository.settings.first()
        if (!settings.notifyRecurringDue) return Result.success()

        val due = context.app.container.walletRepository.observeRecurring().first()
            .filter { it.recurring.isDue() }
        if (due.isNotEmpty()) {
            val titles = due.joinToString(", ") { it.recurring.title }
            val total = due.sumOf { it.recurring.amountMinor }
            Notifications.show(
                context,
                Notifications.ID_RECURRING,
                Notifications.CHANNEL_RECURRING,
                context.getString(R.string.notif_recurring_title),
                context.getString(
                    R.string.notif_recurring_text,
                    titles,
                    Money.format(total, settings.currencyCode)
                )
            )
        }
        return Result.success()
    }
}

class WeeklySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val container = context.app.container
        val settings = container.settingsRepository.settings.first()
        if (!settings.notifyWeeklySummary) return Result.success()

        val today = LocalDate.now()
        val from = Dates.toEpochMillis(today.minusDays(6))
        val to = Dates.toEpochMillis(today.plusDays(1))
        val repo = container.transactionRepository
        val total = repo.observeTotal(TransactionType.EXPENSE, from, to).first()
        if (total <= 0) return Result.success()

        val topCategory = repo.observeCategoryTotals(TransactionType.EXPENSE, from, to)
            .first().firstOrNull()
            ?.let { repo.getCategory(it.categoryId) }
        val topName = topCategory?.let { category ->
            category.key?.let { CategoryVisuals.nameRes(it) }?.let { context.getString(it) }
                ?: category.name
        }

        val text = buildString {
            append(
                context.getString(
                    R.string.notif_weekly_total,
                    Money.format(total, settings.currencyCode)
                )
            )
            if (!topName.isNullOrBlank()) {
                append(" ")
                append(context.getString(R.string.notif_weekly_top, topName))
            }
        }
        Notifications.show(
            context,
            Notifications.ID_WEEKLY,
            Notifications.CHANNEL_SUMMARY,
            context.getString(R.string.notif_weekly_title),
            text
        )
        return Result.success()
    }
}
