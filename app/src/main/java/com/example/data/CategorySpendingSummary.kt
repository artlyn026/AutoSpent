package com.example.data

data class CategorySpendingSummary(
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String,
    val totalAmount: Double,
    val transactionCount: Int
)
