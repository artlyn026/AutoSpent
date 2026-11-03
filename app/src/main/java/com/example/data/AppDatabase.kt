package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finzy_autospent_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Initialize default categories without any dummy transactions
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    seedDefaultCategories(database.categoryDao())
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedDefaultCategories(categoryDao: CategoryDao) {
            val defaultCategories = listOf(
                CategoryEntity(
                    id = CategoryEntity.UNCATEGORIZED_ID,
                    name = CategoryEntity.UNCATEGORIZED_NAME,
                    colorHex = CategoryEntity.UNCATEGORIZED_COLOR,
                    iconName = "ic_category_other",
                    isSystem = true
                ),
                CategoryEntity(
                    id = 2,
                    name = "Food & Beverage",
                    colorHex = "#F59E0B",
                    iconName = "ic_category_food",
                    isSystem = false
                ),
                CategoryEntity(
                    id = 3,
                    name = "Groceries",
                    colorHex = "#10B981",
                    iconName = "ic_category_groceries",
                    isSystem = false
                ),
                CategoryEntity(
                    id = 4,
                    name = "Transport",
                    colorHex = "#3B82F6",
                    iconName = "ic_category_transport",
                    isSystem = false
                ),
                CategoryEntity(
                    id = 5,
                    name = "Entertainment",
                    colorHex = "#8B5CF6",
                    iconName = "ic_category_entertainment",
                    isSystem = false
                ),
                CategoryEntity(
                    id = 6,
                    name = "Bills & Utilities",
                    colorHex = "#EC4899",
                    iconName = "ic_category_bills",
                    isSystem = false
                ),
                CategoryEntity(
                    id = 7,
                    name = "Shopping",
                    colorHex = "#6366F1",
                    iconName = "ic_category_shopping",
                    isSystem = false
                )
            )

            for (category in defaultCategories) {
                categoryDao.insertCategory(category)
            }
        }
    }
}
