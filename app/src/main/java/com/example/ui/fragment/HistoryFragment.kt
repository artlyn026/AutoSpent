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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TransactionAdapter
    private var allTransactionsList: List<TransactionWithCategory> = emptyList()

    // State for selected month (default: current month)
    private val selectedCalendar: Calendar = Calendar.getInstance()
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
    private val selectedMonthRange = MutableStateFlow(calculateMonthRange(selectedCalendar))

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
        setupMonthNavigation()
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

    private fun setupMonthNavigation() {
        updateMonthDisplay()

        binding.btnPrevMonth.setOnClickListener {
            selectedCalendar.add(Calendar.MONTH, -1)
            onMonthChanged()
        }

        binding.btnNextMonth.setOnClickListener {
            selectedCalendar.add(Calendar.MONTH, 1)
            onMonthChanged()
        }

        // Tap label to reset to current month
        binding.layoutMonthLabel.setOnClickListener {
            selectedCalendar.timeInMillis = System.currentTimeMillis()
            onMonthChanged()
        }
    }

    private fun onMonthChanged() {
        updateMonthDisplay()
        selectedMonthRange.value = calculateMonthRange(selectedCalendar)
    }

    private fun updateMonthDisplay() {
        binding.tvCurrentMonth.text = monthFormat.format(selectedCalendar.time)
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
                // Reactive query scoped strictly to the selected month's timestamp range
                selectedMonthRange.flatMapLatest { (start, end) ->
                    repo.getTransactionsByMonth(start, end)
                }.collectLatest { transactions ->
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
        val monthLabel = monthFormat.format(selectedCalendar.time)
        binding.tvTransactionCount.text = "${filtered.size} transaksi tercatat ($monthLabel)"

        if (filtered.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvTransactions.visibility = View.GONE
            binding.tvEmptyHistoryMessage.text = "Tidak ada transaksi tercatat pada $monthLabel."
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

        fun calculateMonthRange(calendar: Calendar): Pair<Long, Long> {
            val start = (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val end = (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, start.getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return Pair(start.timeInMillis, end.timeInMillis)
        }
    }
}
