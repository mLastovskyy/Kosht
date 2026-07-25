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
        CategoryEntity::class,
        RateEntity::class,
        DebtEntity::class,
        SavingEntity::class,
        RecurringEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class KoshtDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun rateDao(): RateDao

    abstract fun debtDao(): DebtDao

    abstract fun savingDao(): SavingDao

    abstract fun recurringDao(): RecurringDao

    companion object {

        fun build(context: Context): KoshtDatabase =
            Room.databaseBuilder(context, KoshtDatabase::class.java, "kosht.db")
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                CategorySeed.all.forEachIndexed { index, seed ->
                    db.execSQL(
                        "INSERT INTO categories (key, name, iconKey, colorArgb, type, position) " +
                            "VALUES (?, '', ?, ?, ?, ?)",
                        arrayOf<Any?>(seed.key, seed.iconKey, seed.colorArgb, seed.type.name, index)
                    )
                }
            }
        }
    }
}
