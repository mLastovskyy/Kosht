package by.mlastovsky.kosht.di

import android.content.Context
import by.mlastovsky.kosht.BuildConfig
import by.mlastovsky.kosht.data.AccountRepository
import by.mlastovsky.kosht.data.CurrencyChanger
import by.mlastovsky.kosht.data.Housekeeping
import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.PremiumRepository
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.UpdateChecker
import by.mlastovsky.kosht.data.UpdateInstaller
import by.mlastovsky.kosht.data.WalletRepository
import by.mlastovsky.kosht.data.awards.AwardTracker
import by.mlastovsky.kosht.data.db.KoshtDatabase
import by.mlastovsky.kosht.data.lock.AppLock
import by.mlastovsky.kosht.data.lock.AppLockRepository
import by.mlastovsky.kosht.data.receipt.ReceiptScanner
import by.mlastovsky.kosht.data.sync.PhotoSync
import by.mlastovsky.kosht.data.sync.SupabaseApi
import by.mlastovsky.kosht.data.sync.SyncAccountRepository
import by.mlastovsky.kosht.data.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope

class AppContainer(
    context: Context,

    private val appScope: CoroutineScope
) {

    private val appContext = context.applicationContext

    private val database: KoshtDatabase = KoshtDatabase.build(context)

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(
            database.transactionDao(),
            database.categoryDao(),
            database.recurringDao(),
            database.transactionItemDao(),
            database.debtDao(),
            database.syncDao(),
            photoStore
        )
    }

    val ratesRepository: RatesRepository by lazy {
        RatesRepository(database.rateDao())
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(
            database.accountDao(),
            database.transactionDao(),
            database.recurringDao(),
            photoStore
        )
    }

    val walletRepository: WalletRepository by lazy {
        WalletRepository(
            database.debtDao(),
            database.savingDao(),
            database.recurringDao(),
            database.transactionDao(),
            database.categoryDao(),
            database.goalDao(),
            database.challengeDao(),
            database.awardDao()
        )
    }

    val receiptScanner: ReceiptScanner by lazy { ReceiptScanner(appContext) }

    val awardTracker: AwardTracker by lazy {
        AwardTracker(
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

    val appLockRepository: AppLockRepository by lazy {
        AppLockRepository(appContext)
    }

    val appLock: AppLock =
        AppLock(appLockRepository, appScope)

    val housekeeping: Housekeeping by lazy {
        Housekeeping(database, photoStore, settingsRepository)
    }

    val updateChecker: UpdateChecker by lazy {
        UpdateChecker()
    }

    val updateInstaller: UpdateInstaller by lazy {
        UpdateInstaller(appContext)
    }

    val settingsRepository: SettingsRepository = SettingsRepository(context)

    val premiumRepository: PremiumRepository by lazy { PremiumRepository(appContext) }

    private val supabaseApi: SupabaseApi by lazy {
        SupabaseApi(
            baseUrl = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY
        )
    }

    val syncAccountRepository: SyncAccountRepository by lazy {
        SyncAccountRepository(appContext, supabaseApi)
    }

    private val photoSync: PhotoSync by lazy {
        PhotoSync(
            api = supabaseApi,
            settings = settingsRepository,
            transactions = database.transactionDao(),
            photos = photoStore
        )
    }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            database,
            supabaseApi,
            syncAccountRepository,
            settingsRepository,
            photoSync
        )
    }
}
