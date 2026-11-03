package com.example.ui.fragment

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.FinzyApplication
import com.example.R
import com.example.data.CategoryEntity
import com.example.data.CategorySpendingSummary
import com.example.databinding.FragmentAnalyticsBinding
import com.example.databinding.ItemCategoryManageBinding
import com.example.ui.dialog.EditCategoryDialogFragment
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val rupiahFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }

    private lateinit var manageAdapter: CategoryManageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCategoryManagement()
        observeData()
    }

    private fun setupCategoryManagement() {
        manageAdapter = CategoryManageAdapter(
            onEdit = { category ->
                val dialog = EditCategoryDialogFragment.newInstance(
                    id = category.id,
                    name = category.name,
                    colorHex = category.colorHex,
                    isSystem = category.isSystem
                )
                dialog.show(childFragmentManager, EditCategoryDialogFragment.TAG)
            },
            onDelete = { category ->
                showDeleteCategoryDialog(category)
            }
        )

        binding.rvCategoriesManage.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategoriesManage.adapter = manageAdapter

        binding.btnAddCategory.setOnClickListener {
            val dialog = EditCategoryDialogFragment.newInstance()
            dialog.show(childFragmentManager, EditCategoryDialogFragment.TAG)
        }
    }

    private fun showDeleteCategoryDialog(category: CategoryEntity) {
        if (category.isSystem) {
            Toast.makeText(requireContext(), "Kategori sistem tidak dapat dihapus.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Kategori")
            .setMessage("Apakah Anda yakin ingin menghapus kategori '${category.name}'? Transaksi terkait akan otomatis dialihkan ke 'Uncategorized'.")
            .setPositiveButton("Hapus") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    FinzyApplication.instance.repository.deleteCategoryById(category.id)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Kategori '${category.name}' telah dihapus",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun observeData() {
        val repo = FinzyApplication.instance.repository

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repo.allCategories,
                    repo.categorySpendingSummary
                ) { categories, summaries ->
                    Pair(categories, summaries)
                }.collect { (categories, summaries) ->
                    // 1. Update Category Management List
                    manageAdapter.submitList(categories)

                    // 2. Update Dynamic Spending Breakdown Bars
                    renderSpendingBreakdown(summaries)
                }
            }
        }
    }

    private fun renderSpendingBreakdown(summaries: List<CategorySpendingSummary>) {
        val container = binding.layoutDynamicCategoryBars
        container.removeAllViews()

        val activeSummaries = summaries.filter { it.totalAmount > 0.0 }
        val grandTotal = activeSummaries.sumOf { it.totalAmount }

        if (grandTotal <= 0.0 || activeSummaries.isEmpty()) {
            binding.tvEmptySpendingState.visibility = View.VISIBLE
            binding.tvHealthScore.text = "100 / 100"
            binding.tvHealthScoreDesc.text = "Belum ada transaksi pengeluaran bulan ini."
            return
        }

        binding.tvEmptySpendingState.visibility = View.GONE

        // Health Score calculation: Penalty if high Uncategorized percentage
        val uncategorizedSpend = summaries
            .filter { it.categoryName.equals(CategoryEntity.UNCATEGORIZED_NAME, ignoreCase = true) }
            .sumOf { it.totalAmount }

        val uncategorizedRatio = uncategorizedSpend / grandTotal
        val healthScore = ((1.0 - (uncategorizedRatio * 0.5)) * 100).toInt().coerceIn(40, 100)

        binding.tvHealthScore.text = "$healthScore / 100"
        if (uncategorizedRatio > 0.4) {
            binding.tvHealthScoreDesc.text = "Perhatian: Anda memiliki banyak transaksi 'Belum Dikategorikan'. Ketuk transaksi di Riwayat untuk menetapkan kategori!"
        } else {
            binding.tvHealthScoreDesc.text = "Bagus! Sebagian besar pengeluaran Anda telah diklasifikasikan dengan rapi."
        }

        // Dynamically add category progress bars for each category with spending
        val density = resources.displayMetrics.density

        for (summary in activeSummaries) {
            val colorInt = try {
                Color.parseColor(summary.categoryColor)
            } catch (e: Exception) {
                Color.parseColor("#94A3B8")
            }

            val categoryRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (12 * density).toInt()
                }
            }

            // Row header with Category Name and Amount
            val textRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val tvName = TextView(requireContext()).apply {
                text = "${summary.categoryName} (${summary.transactionCount})"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val formattedAmount = rupiahFormat.format(summary.totalAmount).replace("Rp", "Rp ")
            val tvAmount = TextView(requireContext()).apply {
                text = formattedAmount
                setTextColor(colorInt)
                textSize = 13f
                paint.isFakeBoldText = true
            }

            textRow.addView(tvName)
            textRow.addView(tvAmount)

            // Progress bar
            val progressPercent = ((summary.totalAmount / grandTotal) * 100).toInt().coerceIn(1, 100)
            val indicator = LinearProgressIndicator(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (6 * density).toInt()
                ).apply {
                    topMargin = (6 * density).toInt()
                }
                trackCornerRadius = (3 * density).toInt()
                trackColor = Color.parseColor("#F1F5F9")
                setIndicatorColor(colorInt)
                progress = progressPercent
            }

            categoryRow.addView(textRow)
            categoryRow.addView(indicator)
            container.addView(categoryRow)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- RecyclerView Adapter for Category Management ---
    private class CategoryManageAdapter(
        private val onEdit: (CategoryEntity) -> Unit,
        private val onDelete: (CategoryEntity) -> Unit
    ) : RecyclerView.Adapter<CategoryManageAdapter.ManageViewHolder>() {

        private var list: List<CategoryEntity> = emptyList()

        fun submitList(categories: List<CategoryEntity>) {
            this.list = categories
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManageViewHolder {
            val binding = ItemCategoryManageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ManageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ManageViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount() = list.size

        inner class ManageViewHolder(
            private val binding: ItemCategoryManageBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(category: CategoryEntity) {
                binding.tvCatName.text = category.name

                val colorInt = try {
                    Color.parseColor(category.colorHex)
                } catch (e: Exception) {
                    Color.parseColor("#94A3B8")
                }

                val circle = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorInt)
                }
                binding.viewCatColor.background = circle

                if (category.isSystem) {
                    binding.tvCatSystemTag.visibility = View.VISIBLE
                    binding.btnDeleteCat.visibility = View.GONE
                } else {
                    binding.tvCatSystemTag.visibility = View.GONE
                    binding.btnDeleteCat.visibility = View.VISIBLE
                }

                binding.btnEditCat.setOnClickListener {
                    onEdit(category)
                }

                binding.btnDeleteCat.setOnClickListener {
                    onDelete(category)
                }
            }
        }
    }

    companion object {
        fun newInstance() = AnalyticsFragment()
    }
}
