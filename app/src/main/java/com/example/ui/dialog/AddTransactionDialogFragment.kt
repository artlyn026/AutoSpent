package com.example.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.FinzyApplication
import com.example.R
import com.example.data.CategoryEntity
import com.example.data.TransactionEntity
import com.example.databinding.LayoutAddTransactionBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddTransactionDialogFragment : DialogFragment() {

    private var _binding: LayoutAddTransactionBinding? = null
    private val binding get() = _binding!!

    private var availableCategories: List<CategoryEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutAddTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCategories()

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            saveTransaction()
        }
    }

    private fun loadCategories() {
        CoroutineScope(Dispatchers.IO).launch {
            val list = FinzyApplication.instance.repository.getCategoriesList()
            availableCategories = list
            val categoryNames = list.map { it.name }

            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    val spinnerAdapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        categoryNames
                    )
                    binding.spinnerCategory.adapter = spinnerAdapter

                    // If Food & Beverage exists, default select index 1 instead of Uncategorized
                    val defaultIndex = list.indexOfFirst { !it.isSystem }.coerceAtLeast(0)
                    if (defaultIndex < list.size) {
                        binding.spinnerCategory.setSelection(defaultIndex)
                    }
                }
            }
        }
    }

    private fun saveTransaction() {
        val amountStr = binding.etAmount.text?.toString()?.trim() ?: ""
        val merchant = binding.etMerchant.text?.toString()?.trim() ?: ""
        val selectedIndex = binding.spinnerCategory.selectedItemPosition
        val selectedCategory = availableCategories.getOrNull(selectedIndex)
        val categoryId = selectedCategory?.id ?: CategoryEntity.UNCATEGORIZED_ID
        val note = binding.etNote.text?.toString()?.trim()

        if (amountStr.isEmpty()) {
            binding.tilAmount.error = getString(R.string.amount_required)
            return
        } else {
            binding.tilAmount.error = null
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            binding.tilAmount.error = "Nominal tidak valid"
            return
        }

        if (merchant.isEmpty()) {
            binding.tilMerchant.error = getString(R.string.merchant_required)
            return
        } else {
            binding.tilMerchant.error = null
        }

        val transaction = TransactionEntity(
            amount = amount,
            merchant = merchant,
            timestamp = System.currentTimeMillis(),
            categoryId = categoryId,
            isManual = true,
            sourceApp = "Manual",
            note = if (!note.isNullOrBlank()) note else "Catatan manual"
        )

        CoroutineScope(Dispatchers.IO).launch {
            FinzyApplication.instance.repository.insertTransaction(transaction)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Transaksi berhasil dicatat!", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddTransactionDialog"
        fun newInstance() = AddTransactionDialogFragment()
    }
}
