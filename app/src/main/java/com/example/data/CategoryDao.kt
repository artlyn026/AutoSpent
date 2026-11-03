package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY isSystem DESC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isSystem DESC, name ASC")
    suspend fun getCategoriesList(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND isSystem = 0")
    suspend fun deleteCategoryById(id: Long)

    @Query("""
        SELECT c.id AS categoryId,
               c.name AS categoryName,
               c.colorHex AS categoryColor,
               COALESCE(SUM(t.amount), 0.0) AS totalAmount,
               COUNT(t.id) AS transactionCount
        FROM categories c
        LEFT JOIN transactions t ON c.id = t.categoryId
        GROUP BY c.id
        ORDER BY totalAmount DESC
    """)
    fun getCategorySpendingSummary(): Flow<List<CategorySpendingSummary>>
}
