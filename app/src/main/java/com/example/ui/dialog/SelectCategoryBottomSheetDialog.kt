package com.example.ui.dialog

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.FinzyApplication
import com.example.data.CategoryEntity
import com.example.databinding.DialogSelectCategoryBinding
import com.example.databinding.ItemCategoryOptionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class SelectCategoryBottomSheetDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSelectCategoryBinding? = null
    private val binding get() = _binding!!

    private var transactionId: Long = 0
    private var merchant: String = ""
    private var amount: Double = 0.0
    private var currentCategoryId: Long = 0
    private var currentCategoryName: String = ""

    private val rupiahFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            transactionId = it.getLong(ARG_ID)
            merchant = it.getString(ARG_MERCHANT, "")
            amount = it.getDouble(ARG_AMOUNT, 0.0)
            currentCategoryId = it.getLong(ARG_CURRENT_CAT_ID, 0)
            currentCategoryName = it.getString(ARG_CURRENT_CAT_NAME, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSelectCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDialogMerchant.text = merchant
        val formatted = rupiahFormat.format(amount).replace("Rp", "Rp ")
        binding.tvDialogAmount.text = formatted
        binding.tvDialogCurrentCat.text = "Kategori Saat Ini: $currentCategoryName"

        val adapter = CategoryOptionAdapter { selectedCategory ->
            updateCategory(selectedCategory)
        }

        binding.rvCategoryOptions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategoryOptions.adapter = adapter

        // Observe dynamic categories from Room
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                FinzyApplication.instance.repository.allCategories.collectLatest { categories ->
                    adapter.submitCategories(categories, currentCategoryId)
                }
            }
        }

        // Delete button
        binding.btnDeleteTransaction.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                FinzyApplication.instance.repository.deleteById(transactionId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Transaksi telah dihapus", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }

    private fun updateCategory(category: CategoryEntity) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            FinzyApplication.instance.repository.updateTransactionCategory(transactionId, category.id)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Kategori diubah menjadi '${category.name}'",
                    Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class CategoryOptionAdapter(
        private val onSelect: (CategoryEntity) -> Unit
    ) : RecyclerView.Adapter<CategoryOptionAdapter.OptionViewHolder>() {

        private var list: List<CategoryEntity> = emptyList()
        private var selectedId: Long = 0

        fun submitCategories(categories: List<CategoryEntity>, selectedCatId: Long) {
            this.list = categories
            this.selectedId = selectedCatId
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
            val binding = ItemCategoryOptionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return OptionViewHolder(binding)
        }

        override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
            val item = list[position]
            holder.bind(item, item.id == selectedId)
        }

        override fun getItemCount() = list.size

        inner class OptionViewHolder(
            private val binding: ItemCategoryOptionBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(category: CategoryEntity, isSelected: Boolean) {
                binding.tvCategoryName.text = category.name

                val colorInt = try {
                    Color.parseColor(category.colorHex)
                } catch (e: Exception) {
                    Color.parseColor("#94A3B8")
                }

                val circle = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorInt)
                }
                binding.viewCategoryColor.background = circle

                binding.ivSelectedCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

                binding.root.setOnClickListener {
                    onSelect(category)
                }
            }
        }
    }

    companion object {
        const val TAG = "SelectCategoryBottomSheet"
        private const val ARG_ID = "arg_id"
        private const val ARG_MERCHANT = "arg_merchant"
        private const val ARG_AMOUNT = "arg_amount"
        private const val ARG_CURRENT_CAT_ID = "arg_cat_id"
        private const val ARG_CURRENT_CAT_NAME = "arg_cat_name"

        fun newInstance(
            id: Long,
            merchant: String,
            amount: Double,
            currentCategoryId: Long,
            currentCategoryName: String
        ): SelectCategoryBottomSheetDialog {
            return SelectCategoryBottomSheetDialog().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ID, id)
                    putString(ARG_MERCHANT, merchant)
                    putDouble(ARG_AMOUNT, amount)
                    putLong(ARG_CURRENT_CAT_ID, currentCategoryId)
                    putString(ARG_CURRENT_CAT_NAME, currentCategoryName)
                }
            }
        }
    }
}
