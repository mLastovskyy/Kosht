package by.mlastovsky.kosht

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import by.mlastovsky.kosht.data.ItemDraft
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.model.RecurringFrequency
import by.mlastovsky.kosht.model.TransactionType
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoDataSeed {

    @Test
    fun fillsTheAppWithTheRecordsTheManualShows() = runBlocking {
        val asked = InstrumentationRegistry.getArguments().getString("seedDemoData")
        assumeTrue("pass -Pandroid.testInstrumentationRunnerArguments.seedDemoData=yes", asked == "yes")

        val app = ApplicationProvider.getApplicationContext<Context>() as KoshtApp
        val transactions = app.container.transactionRepository
        val accounts = app.container.accountRepository
        val wallet = app.container.walletRepository
        val settings = app.container.settingsRepository

        settings.setMultiAccount(true)
        val existing = accounts.observeAccounts().first()
        val card = existing.firstOrNull()?.id ?: accounts.addAccount("Карта", "card", 0xFF1E88E5)
        val cash = existing.getOrNull(1)?.id ?: accounts.addAccount("Наличные", "cash", 0xFF43A047)

        val expenseCategories = transactions.observeCategories(TransactionType.EXPENSE).first()
        val incomeCategories = transactions.observeCategories(TransactionType.INCOME).first()
        fun expense(key: String) = expenseCategories.first { it.key == key }.id
        fun income(key: String) = incomeCategories.first { it.key == key }.id

        val today = LocalDate.now()
        suspend fun record(
            daysAgo: Long,
            categoryId: Long,
            amountMinor: Long,
            note: String,
            type: TransactionType = TransactionType.EXPENSE,
            accountId: Long = card,
            scanned: Boolean = false,
            items: List<ItemDraft> = emptyList()
        ) {
            val moment = today.minusDays(daysAgo)
                .atTime(if (daysAgo.toInt() % 3 == 0) 12 else 19, 40 - daysAgo.toInt() % 20)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val id = transactions.addTransaction(
                TransactionEntity(
                    amountMinor = amountMinor,
                    type = type,
                    categoryId = categoryId,
                    note = note,
                    timestamp = moment,
                    createdAt = moment,
                    accountId = accountId,
                    bynMinor = amountMinor,
                    scanned = scanned
                )
            )
            if (items.isNotEmpty()) transactions.saveItems(id, items)
        }

        record(28, income("salary"), 245000, "Зарплата", TransactionType.INCOME)
        record(14, income("salary"), 122500, "Аванс", TransactionType.INCOME)
        record(9, income("freelance"), 48000, "Подработка", TransactionType.INCOME)

        record(
            0, expense("groceries"), 2074, "Санта",
            scanned = true,
            items = listOf(
                ItemDraft("Крабовые палочки Санта Бремор", 399),
                ItemDraft("Десерт ТОП мандарин-сливки", 153),
                ItemDraft("Молоко Савушкин 1,5%", 240),
                ItemDraft("Лапша Роллтон", 179),
                ItemDraft("Бедро цыплёнка Ганна", 965, 0.555),
                ItemDraft("Батончик Milky Way", 138)
            )
        )
        record(0, expense("cafe"), 1250, "Кофе и круассан")
        record(1, expense("transport"), 480, "Метро")
        record(
            1, expense("groceries"), 3620, "Копеечка",
            items = listOf(
                ItemDraft("Хлеб Нарочанский", 189),
                ItemDraft("Сыр Тильзитер", 730),
                ItemDraft("Яйцо С1", 429, 2.0),
                ItemDraft("Кофе Jacobs", 1849),
                ItemDraft("Пакет", 17)
            )
        )
        record(2, expense("subscriptions"), 599, "Spotify", accountId = cash)
        record(3, expense("housing"), 9389, "Коммуналка")
        record(4, expense("groceries"), 2480, "Евроопт")
        record(5, expense("health"), 1590, "Аптека", accountId = cash)
        record(6, expense("entertainment"), 3400, "Кино")
        record(7, expense("groceries"), 4115, "Соседи")
        record(8, expense("transport"), 2600, "Такси")
        record(10, expense("clothes"), 12900, "Кроссовки")
        record(11, expense("cafe"), 2340, "Обед с коллегами")
        record(12, expense("groceries"), 3105, "Виталюр")
        record(15, expense("education"), 8500, "Курс по Kotlin")
        record(18, expense("groceries"), 2870, "Гиппо")
        record(21, expense("travel"), 15600, "Билеты в Вильнюс")
        record(24, expense("gifts"), 4500, "Подарок маме", accountId = cash)

        if (wallet.observeGoals().first().isEmpty()) {
            val goal = wallet.addGoal("Отпуск", 300000, "BYN")
            wallet.addSaving(50000, "BYN", "Отложил с зарплаты", goal)
            wallet.addSaving(25000, "BYN", "Ещё немного", goal)
        }
        if (wallet.observeDebts().first().isEmpty()) {
            wallet.addDebt("Андрей", DebtDirection.I_OWE, 20000, "BYN", "За билеты")
            wallet.addDebt("Ольга", DebtDirection.OWED_TO_ME, 7500, "BYN", "Обед")
        }
        if (wallet.observeRecurring().first().isEmpty()) {
            wallet.addRecurring(
                "Интернет", 3500, "BYN", expense("subscriptions"),
                today.minusDays(2), RecurringFrequency.MONTHLY, accountId = card
            )
            wallet.addRecurring(
                "Аренда квартиры", 65000, "BYN", expense("housing"),
                today.plusDays(6), RecurringFrequency.MONTHLY, accountId = card
            )
        }
        accounts.setAccountBalance(accounts.observeAccounts().first().first { it.id == cash }, 18000)
    }
}
