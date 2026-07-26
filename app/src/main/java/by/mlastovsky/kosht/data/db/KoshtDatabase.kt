package by.mlastovsky.kosht.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import by.mlastovsky.kosht.data.CategorySeed

@Database(
    entities = [
        TransactionEntity::class,
        TransactionItemEntity::class,
        CategoryEntity::class,
        RateEntity::class,
        DebtEntity::class,
        SavingEntity::class,
        RecurringEntity::class,
        SavingGoalEntity::class,
        ChallengeEntity::class,
        AccountEntity::class,
        AwardEntity::class,
        SyncTombstoneEntity::class,
        SyncCursorEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class KoshtDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun transactionItemDao(): TransactionItemDao

    abstract fun categoryDao(): CategoryDao

    abstract fun rateDao(): RateDao

    abstract fun debtDao(): DebtDao

    abstract fun savingDao(): SavingDao

    abstract fun recurringDao(): RecurringDao

    abstract fun goalDao(): GoalDao

    abstract fun challengeDao(): ChallengeDao

    abstract fun accountDao(): AccountDao

    abstract fun awardDao(): AwardDao

    abstract fun syncDao(): SyncDao

    companion object {

        fun build(context: Context): KoshtDatabase =
            Room.databaseBuilder(context, KoshtDatabase::class.java, "kosht.db")
                .addCallback(SeedCallback)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                    MIGRATION_14_15
                )
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS rates (" +
                        "code TEXT NOT NULL PRIMARY KEY, " +
                        "scale INTEGER NOT NULL, " +
                        "rate REAL NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS debts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "personName TEXT NOT NULL, " +
                        "direction TEXT NOT NULL, " +
                        "amountMinor INTEGER NOT NULL, " +
                        "currencyCode TEXT NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "closedAt INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "amountMinor INTEGER NOT NULL, " +
                        "currencyCode TEXT NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recurring (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "amountMinor INTEGER NOT NULL, " +
                        "categoryId INTEGER NOT NULL, " +
                        "dayOfMonth INTEGER NOT NULL, " +
                        "lastConfirmed TEXT, " +
                        "enabled INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(categoryId) REFERENCES categories(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recurring_categoryId " +
                        "ON recurring (categoryId)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN photoPath TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN bynMinor INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recurring ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'BYN'"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE savings ADD COLUMN goalId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS saving_goals (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "targetMinor INTEGER NOT NULL, " +
                        "currencyCode TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "achievedAt INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS challenges (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "amountMinor INTEGER NOT NULL, " +
                        "categoryId INTEGER, " +
                        "startEpochDay INTEGER NOT NULL, " +
                        "endEpochDay INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recurring charges move from day-of-month to a concrete next
                // due date + frequency. Existing rows become due today.
                db.execSQL(
                    "CREATE TABLE recurring_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "amountMinor INTEGER NOT NULL, " +
                        "currencyCode TEXT NOT NULL, " +
                        "categoryId INTEGER NOT NULL, " +
                        "nextDueEpochDay INTEGER NOT NULL, " +
                        "frequency TEXT NOT NULL, " +
                        "enabled INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(categoryId) REFERENCES categories(id) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "INSERT INTO recurring_new " +
                        "(id, title, amountMinor, currencyCode, categoryId, " +
                        "nextDueEpochDay, frequency, enabled, createdAt) " +
                        "SELECT id, title, amountMinor, currencyCode, categoryId, " +
                        "CAST(strftime('%s','now') / 86400 AS INTEGER), 'MONTHLY', " +
                        "enabled, createdAt FROM recurring"
                )
                db.execSQL("DROP TABLE recurring")
                db.execSQL("ALTER TABLE recurring_new RENAME TO recurring")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recurring_categoryId " +
                        "ON recurring (categoryId)"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS accounts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "key TEXT, name TEXT NOT NULL, iconKey TEXT NOT NULL, " +
                        "colorArgb INTEGER NOT NULL, position INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN accountId INTEGER")
                seedAccounts(db)
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE accounts ADD COLUMN adjustmentMinor INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS awards (" +
                        "`key` TEXT NOT NULL PRIMARY KEY, " +
                        "unlockedAt INTEGER NOT NULL)"
                )
            }
        }

        /**
         * Sync columns for every table that travels between devices, plus the
         * bookkeeping tables. Existing rows are handed an identity here; the
         * triggers installed in [installSyncTriggers] take it from there.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_tombstones` (" +
                        "`entity` TEXT NOT NULL, `uid` TEXT NOT NULL, " +
                        "`deletedAt` INTEGER NOT NULL, PRIMARY KEY(`entity`, `uid`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_cursor` (" +
                        "`id` INTEGER NOT NULL, `pulledThrough` INTEGER NOT NULL, " +
                        "`pushedThrough` INTEGER NOT NULL, `lastSyncAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                // The tables that existed when sync arrived, spelled out rather
                // than read off the enum: a table added by a later migration is
                // not here yet, and altering it would fail this one.
                syncTablesAtV11.forEach { entity ->
                    val table = entity.table
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        "ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0"
                    )
                    // updatedAt stays 0: data that predates the account has
                    // never been "edited", so a real edit from any device wins.
                    db.execSQL("UPDATE `$table` SET `uid` = ${backfillUid(entity)}")
                }
            }
        }

        /** Where a scanned QR led, and the copy of the page it led to. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptUrl TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptDocPath TEXT")
            }
        }

        /**
         * The object name a receipt photo was uploaded under, once the user
         * has asked for photos to be synced. Null means "this photo has never
         * left the phone", which is what every existing row is.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN photoKey TEXT")
            }
        }

        /**
         * Where a record came from and where money went: the scanner mark,
         * transfers between the user's own accounts with what they cost, and
         * planned payments that can now be income and name their account.
         * Every existing row is an ordinary typed expense, which is exactly
         * what these defaults say.
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN scanned INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN transferToAccountId INTEGER")
                db.execSQL(
                    "ALTER TABLE transactions " +
                        "ADD COLUMN transferFeeMinor INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE recurring ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'"
                )
                db.execSQL("ALTER TABLE recurring ADD COLUMN accountId INTEGER")
            }
        }

        /**
         * What a record was spent on, line by line — the optional product list
         * behind an amount, and what the product statistics read. CASCADE, so a
         * deleted record takes its lines with it; the undo offer carries them
         * back (see DeletionEvents).
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS transaction_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "transactionId INTEGER NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "amountMinor INTEGER NOT NULL, " +
                        "quantity REAL, " +
                        "position INTEGER NOT NULL, " +
                        "uid TEXT NOT NULL DEFAULT '', " +
                        "updatedAt INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(transactionId) REFERENCES transactions(id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transaction_items_transactionId " +
                        "ON transaction_items (transactionId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transaction_items_name " +
                        "ON transaction_items (name)"
                )
            }
        }

        private val syncTablesAtV11 = listOf(
            SyncEntity.ACCOUNTS,
            SyncEntity.CATEGORIES,
            SyncEntity.SAVING_GOALS,
            SyncEntity.TRANSACTIONS,
            SyncEntity.RECURRING,
            SyncEntity.SAVINGS,
            SyncEntity.CHALLENGES,
            SyncEntity.DEBTS,
            SyncEntity.AWARDS
        )

        /**
         * Built-in rows must land on the same identity on every device, or
         * signing in on a second phone would duplicate every stock category.
         */
        private fun backfillUid(entity: SyncEntity): String = when (entity) {
            SyncEntity.AWARDS -> "`key`"
            SyncEntity.CATEGORIES, SyncEntity.ACCOUNTS ->
                "CASE WHEN `key` IS NOT NULL AND `key` <> '' " +
                    "THEN '$SEED_UID_PREFIX' || `key` ELSE $NEW_UID END"

            else -> NEW_UID
        }

        /**
         * Keeping this in triggers rather than in every repository means a
         * write anywhere in the app — including plain UPDATE queries and
         * cascades — stays sync-correct without anyone remembering to stamp it.
         */
        private fun installSyncTriggers(db: SupportSQLiteDatabase) {
            SyncEntity.tables.forEach { entity ->
                val table = entity.table
                val uid = when (entity) {
                    SyncEntity.AWARDS -> "NEW.`key`"
                    SyncEntity.CATEGORIES, SyncEntity.ACCOUNTS ->
                        "CASE WHEN NEW.`key` IS NOT NULL AND NEW.`key` <> '' " +
                            "THEN '$SEED_UID_PREFIX' || NEW.`key` ELSE $NEW_UID END"

                    else -> NEW_UID
                }
                // Rows the sync engine writes already carry uid and updatedAt,
                // which is precisely what keeps these guards from firing.
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `${table}_sync_insert` " +
                        "AFTER INSERT ON `$table` FOR EACH ROW WHEN NEW.`uid` = '' BEGIN " +
                        "UPDATE `$table` SET `uid` = $uid, `updatedAt` = $NOW_MILLIS " +
                        "WHERE rowid = NEW.rowid; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `${table}_sync_update` " +
                        "AFTER UPDATE ON `$table` FOR EACH ROW " +
                        "WHEN NEW.`updatedAt` = OLD.`updatedAt` BEGIN " +
                        "UPDATE `$table` SET `updatedAt` = $NOW_MILLIS " +
                        "WHERE rowid = NEW.rowid; END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `${table}_sync_delete` " +
                        "AFTER DELETE ON `$table` FOR EACH ROW WHEN OLD.`uid` <> '' BEGIN " +
                        "INSERT OR REPLACE INTO `sync_tombstones` " +
                        "(`entity`, `uid`, `deletedAt`) " +
                        "VALUES ('$table', OLD.`uid`, $NOW_MILLIS); END"
                )
            }
        }

        /** 128 random bits; plenty to never collide across a user's devices. */
        private const val NEW_UID = "lower(hex(randomblob(16)))"

        private const val SEED_UID_PREFIX = "seed:"

        private const val NOW_MILLIS =
            "(CAST(strftime('%s','now') AS INTEGER) * 1000 + " +
                "CAST(strftime('%f','now') * 1000 AS INTEGER) % 1000)"

        /**
         * A single primary account keeps the UI exactly as before; account
         * pickers appear only after the user adds more. [withSyncUid] is off
         * for the pre-sync migration that predates the uid column.
         */
        private fun seedAccounts(db: SupportSQLiteDatabase, withSyncUid: Boolean = false) {
            if (withSyncUid) {
                db.execSQL(
                    "INSERT INTO accounts (`key`, name, iconKey, colorArgb, position, uid) " +
                        "VALUES ('card', '', 'card', ${0xFF1E88E5}, 0, '${SEED_UID_PREFIX}card')"
                )
            } else {
                db.execSQL(
                    "INSERT INTO accounts (key, name, iconKey, colorArgb, position) " +
                        "VALUES ('card', '', 'card', ${0xFF1E88E5}, 0)"
                )
            }
        }

        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                CategorySeed.all.forEachIndexed { index, seed ->
                    // Seeded rows keep updatedAt at 0 on purpose: a second
                    // device must not resurrect stock rows deleted elsewhere.
                    db.execSQL(
                        "INSERT INTO categories " +
                            "(`key`, name, iconKey, colorArgb, type, position, uid) " +
                            "VALUES (?, '', ?, ?, ?, ?, ?)",
                        arrayOf<Any?>(
                            seed.key,
                            seed.iconKey,
                            seed.colorArgb,
                            seed.type.name,
                            index,
                            SEED_UID_PREFIX + seed.key
                        )
                    )
                }
                seedAccounts(db, withSyncUid = true)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                // Idempotent, so the triggers survive any future migration
                // that rebuilds a table and silently drops them with it.
                installSyncTriggers(db)
                db.execSQL(
                    "INSERT OR IGNORE INTO `sync_cursor` " +
                        "(`id`, `pulledThrough`, `pushedThrough`, `lastSyncAt`) " +
                        "VALUES (0, 0, -1, 0)"
                )
            }
        }
    }
}
