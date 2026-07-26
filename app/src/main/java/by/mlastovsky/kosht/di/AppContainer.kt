package by.mlastovsky.kosht.di

import android.content.Context
import by.mlastovsky.kosht.data.AccountRepository
import by.mlastovsky.kosht.data.CurrencyChanger
import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.WalletRepository
import by.mlastovsky.kosht.data.db.KoshtDatabase
import by.mlastovsky.kosht.data.receipt.ReceiptScanner

/**
 * Simple manual dependency container scoped to the application lifecycle.
 */
class AppContainer(
    context: Context,
    /** Lives as long as the process; what app-wide watchers run in. */
    private val appScope: kotlinx.coroutines.CoroutineScope
) {

    private val appContext = context.applicationContext

    private val database: KoshtDatabase = KoshtDatabase.build(context)

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(
            database.transactionDao(),
            database.categoryDao(),
            database.recurringDao(),
            database.transactionItemDao()
        )
    }

    val ratesRepository: RatesRepository by lazy {
        RatesRepository(database.rateDao())
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(
            database.accountDao(),
            database.transactionDao(),
            database.recurringDao()
        )
    }

    val walletRepository: WalletRepository by lazy {
        WalletRepository(
            database.debtDao(),
            database.savingDao(),
            database.recurringDao(),
            database.transactionDao(),
            database.goalDao(),
            database.challengeDao(),
            database.awardDao()
        )
    }

    val receiptScanner: ReceiptScanner by lazy { ReceiptScanner(appContext) }

    /**
     * Created eagerly: an award has to be earned the moment it is deserved,
     * which cannot wait for a screen to ask for it.
     */
    val awardTracker: by.mlastovsky.kosht.data.awards.AwardTracker by lazy {
        by.mlastovsky.kosht.data.awards.AwardTracker(
            transactions = transactionRepository,
            wallet = walletRepository,
            rates = ratesRepository,
            settings = settingsRepository,
            scope = appScope
        )
    }

    val currencyChanger: CurrencyChanger by lazy {
        CurrencyChanger(
            database.transactionDao(),
            database.transactionItemDao(),
            database.challengeDao(),
            database.accountDao(),
            database.savingDao(),
            database.goalDao(),
            database.debtDao(),
            database.recurringDao(),
            settingsRepository,
            ratesRepository
        )
    }

    val photoStore: PhotoStore by lazy { PhotoStore(appContext) }

    /** Sweeps out attachments and tombstones nothing points at any more. */
    val housekeeping: by.mlastovsky.kosht.data.Housekeeping by lazy {
        by.mlastovsky.kosht.data.Housekeeping(database, photoStore, settingsRepository)
    }

    val updateChecker: by.mlastovsky.kosht.data.UpdateChecker by lazy {
        by.mlastovsky.kosht.data.UpdateChecker()
    }

    val updateInstaller: by.mlastovsky.kosht.data.UpdateInstaller by lazy {
        by.mlastovsky.kosht.data.UpdateInstaller(appContext)
    }

    val settingsRepository: SettingsRepository = SettingsRepository(context)

    private val supabaseApi: by.mlastovsky.kosht.data.sync.SupabaseApi by lazy {
        by.mlastovsky.kosht.data.sync.SupabaseApi(
            baseUrl = by.mlastovsky.kosht.BuildConfig.SUPABASE_URL,
            anonKey = by.mlastovsky.kosht.BuildConfig.SUPABASE_ANON_KEY
        )
    }

    val syncAccountRepository: by.mlastovsky.kosht.data.sync.SyncAccountRepository by lazy {
        by.mlastovsky.kosht.data.sync.SyncAccountRepository(appContext, supabaseApi)
    }

    private val photoSync: by.mlastovsky.kosht.data.sync.PhotoSync by lazy {
        by.mlastovsky.kosht.data.sync.PhotoSync(
            api = supabaseApi,
            settings = settingsRepository,
            transactions = database.transactionDao(),
            photos = photoStore
        )
    }

    val syncEngine: by.mlastovsky.kosht.data.sync.SyncEngine by lazy {
        by.mlastovsky.kosht.data.sync.SyncEngine(
            database,
            supabaseApi,
            syncAccountRepository,
            settingsRepository,
            photoSync
        )
    }
}
