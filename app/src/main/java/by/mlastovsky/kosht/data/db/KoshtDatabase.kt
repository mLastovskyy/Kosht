package by.mlastovsky.kosht.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import by.mlastovsky.kosht.data.CategorySeed

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KoshtDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    companion object {

        fun build(context: Context): KoshtDatabase =
            Room.databaseBuilder(context, KoshtDatabase::class.java, "kosht.db")
                .addCallback(SeedCallback)
                .build()

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
