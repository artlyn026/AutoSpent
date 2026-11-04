package com.example.ui.fragment

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.FinzyApplication
import com.example.data.CategorySpendingSummary
import com.example.data.TransactionWithCategory
import com.example.databinding.FragmentDashboardBinding
import com.example.ui.custom.DonutChartView
import com.example.ui.dialog.AddTransactionDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val rupiahFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }

    // State for selected month navigation (default: current month)
    private val selectedCalendar: Calendar = Calendar.getInstance()
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
    private val selectedMonthRange = MutableStateFlow(HistoryFragment.calculateMonthRange(selectedCalendar))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMonthNavigation()
        setupListeners()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        checkNotificationAccess()
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
        selectedMonthRange.value = HistoryFragment.calculateMonthRange(selectedCalendar)
    }

    private fun updateMonthDisplay() {
        val monthName = monthFormat.format(selectedCalendar.time)
        binding.tvCurrentMonth.text = monthName
        binding.tvTotalExpenseLabel.text = "Total Pengeluaran ($monthName)"
    }

    private fun setupListeners() {
        // Button to open Notification Access system settings
        binding.btnEnableListener.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Buka Pengaturan > Akses Notifikasi", Toast.LENGTH_SHORT).show()
            }
        }

        // Button to open Add Manual Transaction dialog
        binding.btnAddManualTransaction.setOnClickListener {
            AddTransactionDialogFragment.newInstance()
                .show(childFragmentManager, AddTransactionDialogFragment.TAG)
        }

        // Button to simulate an e-wallet notification (GoPay / BCA / ShopeePay / DANA)
        binding.btnSimulateNotification.setOnClickListener {
            simulateEWalletNotification()
        }
    }

    private fun checkNotificationAccess() {
        val context = context ?: return
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        val isGranted = enabledPackages.contains(context.packageName)

        binding.cardPermissionWarning.visibility = if (isGranted) View.GONE else View.VISIBLE
    }

    private fun observeData() {
        val repo = FinzyApplication.instance.repository
        val prefs = requireActivity().getSharedPreferences("finzy_prefs", Context.MODE_PRIVATE)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Reactive query scoped strictly to the selected month
                selectedMonthRange.flatMapLatest { (start, end) ->
                    combine(
                        repo.getTotalSpendingByMonth(start, end),
                        repo.getTransactionsByMonth(start, end),
                        repo.getCategorySpendingSummaryByMonth(start, end)
                    ) { totalSpending, allItems, categorySummaries ->
                        Triple(totalSpending ?: 0.0, allItems, categorySummaries)
                    }
                }.collect { (total, allItems, categorySummaries) ->
                    val budgetLimit = prefs.getFloat("monthly_budget", 2500000f).toDouble()

                    // Update Total Spending
                    val formattedTotal = rupiahFormat.format(total).replace("Rp", "Rp ")
                    binding.tvTotalExpense.text = formattedTotal

                    // Update Remaining Budget
                    val remaining = (budgetLimit - total).coerceAtLeast(0.0)
                    val formattedRemaining = rupiahFormat.format(remaining).replace("Rp", "Rp ")
                    binding.tvRemainingBudget.text = formattedRemaining

                    // Update Budget Progress & Limit
                    val formattedLimit = rupiahFormat.format(budgetLimit).replace("Rp", "Rp ")
                    binding.tvBudgetLimitHint.text = "Batas Bulanan: $formattedLimit"

                    val percent = if (budgetLimit > 0) {
                        ((total / budgetLimit) * 100).toInt().coerceIn(0, 100)
                    } else 0
                    binding.progressBudget.progress = percent
                    binding.tvBudgetPercent.text = "$percent% Terpakai"

                    if (percent >= 80) {
                        binding.progressBudget.setIndicatorColor(Color.parseColor("#EF4444"))
                    } else if (percent >= 50) {
                        binding.progressBudget.setIndicatorColor(Color.parseColor("#F59E0B"))
                    } else {
                        binding.progressBudget.setIndicatorColor(Color.parseColor("#10B981"))
                    }

                    // Dynamically update Donut Chart with real custom categories and custom colors for this month
                    updateDonutChart(allItems, categorySummaries, total)
                }
            }
        }
    }

    private fun updateDonutChart(
        items: List<TransactionWithCategory>,
        summaries: List<CategorySpendingSummary>,
        total: Double
    ) {
        val activeSummaries = summaries.filter { it.totalAmount > 0.0 }
        val monthLabel = monthFormat.format(selectedCalendar.time)

        if (items.isEmpty() || total <= 0.0 || activeSummaries.isEmpty()) {
            val emptySlices = listOf(
                DonutChartView.DonutSlice("Kosong", 100f, Color.parseColor("#E2E8F0"))
            )
            binding.donutChartView.setChartData(emptySlices, "0 Tx", monthLabel)
            return
        }

        val slices = activeSummaries.map { summary ->
            val colorInt = try {
                Color.parseColor(summary.categoryColor)
            } catch (e: Exception) {
                Color.parseColor("#94A3B8")
            }
            DonutChartView.DonutSlice(
                summary.categoryName,
                summary.totalAmount.toFloat(),
                colorInt
            )
        }

        binding.donutChartView.setChartData(slices, "${items.size} Tx", monthLabel)
    }

    /**
     * Simulates realistic e-wallet notifications to verify regex extraction and automatic Room saving.
     * New transactions default to 'Uncategorized' as required by Requirement 3.
     */
    private fun simulateEWalletNotification() {
        val scenarios = listOf(
            Triple("GoPay", "Kamu sudah bayar Rp 35.000 ke Kopi Janji Jiwa pakai GoPay.", "com.gojek.app"),
            Triple("OVO", "Pembayaran ke Fore Coffee sebesar Rp 18.000 menggunakan OVO Cash berhasil.", "ovo.id"),
            Triple("ShopeePay", "Transfer ke DANA sebesar Rp 50.000 berhasil.", "com.shopee.id"),
            Triple("DANA", "Pembayaran Rp 22.000 di Chatime berhasil!", "id.dana"),
            Triple("m-BCA", "Transaksi QRIS sebesar Rp 65.000 di Solaria berhasil.", "com.bca"),
            Triple("BRImo", "Pembayaran QRIS Rp 25.000 di Indomaret berhasil.", "id.co.bri.brimo"),
            Triple("SeaBank", "Kamu berhasil transfer Rp 75.000 ke ShopeePay (08123456789).", "com.bke.seabank")
        )

        val selected = scenarios[Random.nextInt(scenarios.size)]
        val (walletName, notificationText, pkgName) = selected

        val parsed = com.example.service.NotificationParser.parseByPackage(pkgName, walletName, notificationText)

        if (parsed != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                FinzyApplication.instance.repository.insertTransaction(parsed)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "🔔 Notifikasi $walletName terdeteksi!\nSumber: ${parsed.sourceApp} | Tujuan: ${parsed.merchant} | Rp ${String.format("%,.0f", parsed.amount)}\nOtomatis disimpan ke database.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = DashboardFragment()
    }
}
