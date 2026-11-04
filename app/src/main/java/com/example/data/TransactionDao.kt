package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :transactionId")
    suspend fun updateTransactionCategory(transactionId: Long, categoryId: Long)

    @Query("""
        SELECT t.id, t.amount, t.merchant, t.timestamp, t.categoryId, t.isManual, t.sourceApp, t.note,
               COALESCE(c.name, 'Uncategorized') AS categoryName,
               COALESCE(c.colorHex, '#94A3B8') AS categoryColor,
               COALESCE(c.iconName, 'ic_category_other') AS categoryIcon
        FROM transactions t
        LEFT JOIN categories c ON t.categoryId = c.id
        ORDER BY t.timestamp DESC
    """)
    fun getAllTransactionsWithCategory(): Flow<List<TransactionWithCategory>>

    @Query("""
        SELECT t.id, t.amount, t.merchant, t.timestamp, t.categoryId, t.isManual, t.sourceApp, t.note,
               COALESCE(c.name, 'Uncategorized') AS categoryName,
               COALESCE(c.colorHex, '#94A3B8') AS categoryColor,
               COALESCE(c.iconName, 'ic_category_other') AS categoryIcon
        FROM transactions t
        LEFT JOIN categories c ON t.categoryId = c.id
        WHERE t.isManual = :isManual
        ORDER BY t.timestamp DESC
    """)
    fun getTransactionsBySource(isManual: Boolean): Flow<List<TransactionWithCategory>>

    @Query("""
        SELECT t.id, t.amount, t.merchant, t.timestamp, t.categoryId, t.isManual, t.sourceApp, t.note,
               COALESCE(c.name, 'Uncategorized') AS categoryName,
               COALESCE(c.colorHex, '#94A3B8') AS categoryColor,
               COALESCE(c.iconName, 'ic_category_other') AS categoryIcon
        FROM transactions t
        LEFT JOIN categories c ON t.categoryId = c.id
        WHERE t.timestamp >= :startTimestamp AND t.timestamp <= :endTimestamp
        ORDER BY t.timestamp DESC
    """)
    fun getTransactionsByMonth(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionWithCategory>>

    @Query("""
        SELECT c.id AS categoryId,
               c.name AS categoryName,
               c.colorHex AS categoryColor,
               COALESCE(SUM(t.amount), 0.0) AS totalAmount,
               COUNT(t.id) AS transactionCount
        FROM categories c
        LEFT JOIN transactions t ON c.id = t.categoryId AND t.timestamp >= :startTimestamp AND t.timestamp <= :endTimestamp
        GROUP BY c.id
        ORDER BY totalAmount DESC
    """)
    fun getCategorySpendingSummaryByMonth(startTimestamp: Long, endTimestamp: Long): Flow<List<CategorySpendingSummary>>

    @Query("SELECT SUM(amount) FROM transactions WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp")
    fun getTotalSpendingByMonth(startTimestamp: Long, endTimestamp: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE timestamp >= :startOfMonth")
    fun getTotalSpendingThisMonth(startOfMonth: Long): Flow<Double?>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}
