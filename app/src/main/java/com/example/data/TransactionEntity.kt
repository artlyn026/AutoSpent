package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a financial transaction.
 * Foreign key connects to CategoryEntity, defaulting to CategoryEntity.UNCATEGORIZED_ID (1).
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_DEFAULT
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val timestamp: Long = System.currentTimeMillis(),
    val categoryId: Long = CategoryEntity.UNCATEGORIZED_ID,
    val isManual: Boolean = false,
    val sourceApp: String = "Manual",
    val note: String? = null
)
