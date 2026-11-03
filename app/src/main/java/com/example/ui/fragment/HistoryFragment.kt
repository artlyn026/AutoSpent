package com.example.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.FinzyApplication
import com.example.R
import com.example.data.TransactionWithCategory
import com.example.databinding.FragmentHistoryBinding
import com.example.ui.adapter.TransactionAdapter
import com.example.ui.dialog.SelectCategoryBottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TransactionAdapter
    private var allTransactionsList: List<TransactionWithCategory> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilterChips()
        observeTransactions()
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter(
            onItemClick = { item ->
                // Open category selection bottom sheet to edit category in Room
                val dialog = SelectCategoryBottomSheetDialog.newInstance(
                    id = item.id,
                    merchant = item.merchant,
                    amount = item.amount,
                    currentCategoryId = item.categoryId,
                    currentCategoryName = item.categoryName
                )
                dialog.show(childFragmentManager, SelectCategoryBottomSheetDialog.TAG)
            }
        )

        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            filterTransactions(checkedIds.firstOrNull())
        }
    }

    private fun observeTransactions() {
        val repo = FinzyApplication.instance.repository

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Real-time Flow observation of transactions joined with categories
                repo.allTransactionsWithCategory.collectLatest { transactions ->
                    allTransactionsList = transactions
                    val checkedChip = binding.chipGroupFilter.checkedChipId
                    filterTransactions(checkedChip)
                }
            }
        }
    }

    private fun filterTransactions(chipId: Int?) {
        val filtered = when (chipId) {
            R.id.chip_filter_auto -> allTransactionsList.filter { !it.isManual }
            R.id.chip_filter_manual -> allTransactionsList.filter { it.isManual }
            else -> allTransactionsList
        }

        adapter.submitList(filtered)
        binding.tvTransactionCount.text = "${filtered.size} transaksi tercatat"

        if (filtered.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvTransactions.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvTransactions.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HistoryFragment()
    }
}
