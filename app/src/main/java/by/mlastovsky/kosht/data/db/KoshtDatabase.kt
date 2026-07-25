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
        RateEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KoshtDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun rateDao(): RateDao

    companion object {

        fun build(context: Context): KoshtDatabase =
            Room.databaseBuilder(context, KoshtDatabase::class.java, "kosht.db")
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_1_2)
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
