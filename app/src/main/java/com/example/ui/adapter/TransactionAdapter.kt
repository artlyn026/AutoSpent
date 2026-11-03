package com.example.ui.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.CategoryEntity
import com.example.data.TransactionWithCategory
import com.example.databinding.ItemTransactionBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private val onItemClick: ((TransactionWithCategory) -> Unit)? = null
) : ListAdapter<TransactionWithCategory, TransactionAdapter.TransactionViewHolder>(DiffCallback) {

    private val rupiahFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }
    private val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TransactionViewHolder(
        private val binding: ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TransactionWithCategory) {
            binding.tvMerchant.text = item.merchant
            binding.tvTimestamp.text = dateFormat.format(Date(item.timestamp))

            val formattedAmount = rupiahFormat.format(item.amount).replace("Rp", "Rp ")
            binding.tvAmount.text = "- $formattedAmount"

            // Parse dynamic category color
            val catColor = try {
                Color.parseColor(item.categoryColor)
            } catch (e: Exception) {
                Color.parseColor("#94A3B8")
            }

            // Category tag and icon background
            val isUncategorized = item.categoryId == CategoryEntity.UNCATEGORIZED_ID ||
                    item.categoryName.equals(CategoryEntity.UNCATEGORIZED_NAME, ignoreCase = true)

            if (isUncategorized) {
                binding.tvCategory.text = "⚠️ Uncategorized (Ketuk untuk ubah)"
                binding.tvCategory.setTextColor(Color.parseColor("#DC2626"))
            } else {
                binding.tvCategory.text = item.categoryName
                binding.tvCategory.setTextColor(catColor)
            }

            // Set circular container background color for icon
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(catColor)
            }
            binding.iconContainer.background = shape
            binding.ivCategoryIcon.setImageResource(R.drawable.ic_wallet)

            // Source App Tag
            binding.tvSourceTag.text = item.sourceApp
            if (item.isManual) {
                binding.tvDetectionMode.text = "Manual"
                binding.tvDetectionMode.setTextColor(Color.parseColor("#64748B"))
                binding.tvSourceTag.setBackgroundColor(Color.parseColor("#F1F5F9"))
                binding.tvSourceTag.setTextColor(Color.parseColor("#475569"))
            } else {
                binding.tvDetectionMode.text = "Auto E-Wallet"
                binding.tvDetectionMode.setTextColor(Color.parseColor("#10B981"))
                binding.tvSourceTag.setBackgroundColor(Color.parseColor("#E0E7FF"))
                binding.tvSourceTag.setTextColor(Color.parseColor("#4F46E5"))
            }

            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<TransactionWithCategory>() {
            override fun areItemsTheSame(
                oldItem: TransactionWithCategory,
                newItem: TransactionWithCategory
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: TransactionWithCategory,
                newItem: TransactionWithCategory
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
