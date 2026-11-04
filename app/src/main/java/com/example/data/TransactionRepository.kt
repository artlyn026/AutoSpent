package com.example.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {

    // --- Transactions ---
    val allTransactionsWithCategory: Flow<List<TransactionWithCategory>> =
        transactionDao.getAllTransactionsWithCategory()

    fun getTransactionsBySource(isManual: Boolean): Flow<List<TransactionWithCategory>> {
        return transactionDao.getTransactionsBySource(isManual)
    }

    fun getTransactionsByMonth(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionWithCategory>> {
        return transactionDao.getTransactionsByMonth(startTimestamp, endTimestamp)
    }

    fun getCategorySpendingSummaryByMonth(startTimestamp: Long, endTimestamp: Long): Flow<List<CategorySpendingSummary>> {
        return transactionDao.getCategorySpendingSummaryByMonth(startTimestamp, endTimestamp)
    }

    fun getTotalSpendingByMonth(startTimestamp: Long, endTimestamp: Long): Flow<Double?> {
        return transactionDao.getTotalSpendingByMonth(startTimestamp, endTimestamp)
    }

    fun getTotalSpendingThisMonth(): Flow<Double?> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return transactionDao.getTotalSpendingThisMonth(calendar.timeInMillis)
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun updateTransactionCategory(transactionId: Long, categoryId: Long) {
        transactionDao.updateTransactionCategory(transactionId, categoryId)
    }

    suspend fun deleteById(id: Long) {
        transactionDao.deleteById(id)
    }

    suspend fun clearAll() {
        transactionDao.clearAll()
    }

    // --- Categories ---
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    val categorySpendingSummary: Flow<List<CategorySpendingSummary>> =
        categoryDao.getCategorySpendingSummary()

    suspend fun getCategoriesList(): List<CategoryEntity> {
        val list = categoryDao.getCategoriesList()
        if (list.isEmpty()) {
            AppDatabase.seedDefaultCategories(categoryDao)
            return categoryDao.getCategoriesList()
        }
        return list
    }

    suspend fun insertCategory(category: CategoryEntity): Long {
        return categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity) {
        categoryDao.updateCategory(category)
    }

    suspend fun deleteCategoryById(id: Long) {
        categoryDao.deleteCategoryById(id)
    }
}
