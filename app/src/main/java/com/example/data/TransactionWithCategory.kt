package com.example.data

/**
 * Data class representing a transaction combined with its Category details.
 */
data class TransactionWithCategory(
    val id: Long,
    val amount: Double,
    val merchant: String,
    val timestamp: Long,
    val categoryId: Long,
    val isManual: Boolean,
    val sourceApp: String,
    val note: String?,
    val categoryName: String,
    val categoryColor: String,
    val categoryIcon: String
)
