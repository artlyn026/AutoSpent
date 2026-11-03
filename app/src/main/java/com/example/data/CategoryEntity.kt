package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing dynamic, customizable transaction categories.
 * Users can create, edit, or delete categories from the Analytics screen.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorHex: String = "#64748B",
    val iconName: String = "ic_category_other",
    val isSystem: Boolean = false // System categories like 'Uncategorized' cannot be deleted
) {
    companion object {
        const val UNCATEGORIZED_ID: Long = 1L
        const val UNCATEGORIZED_NAME = "Uncategorized"
        const val UNCATEGORIZED_COLOR = "#94A3B8"
    }
}
