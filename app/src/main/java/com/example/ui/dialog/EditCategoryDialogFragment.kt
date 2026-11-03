package com.example.ui.dialog

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.FinzyApplication
import com.example.R
import com.example.data.CategoryEntity
import com.example.databinding.DialogEditCategoryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditCategoryDialogFragment : DialogFragment() {

    private var _binding: DialogEditCategoryBinding? = null
    private val binding get() = _binding!!

    private var categoryId: Long = 0
    private var initialName: String = ""
    private var selectedColorHex: String = "#F59E0B"
    private var isSystem: Boolean = false

    private val colorPalette = listOf(
        "#F59E0B", // Amber
        "#10B981", // Emerald
        "#3B82F6", // Blue
        "#8B5CF6", // Purple
        "#EC4899", // Pink
        "#06B6D4", // Cyan
        "#EF4444", // Coral Red
        "#6366F1", // Indigo
        "#64748B"  // Slate
    )

    private val colorViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            categoryId = it.getLong(ARG_CAT_ID, 0)
            initialName = it.getString(ARG_CAT_NAME, "")
            selectedColorHex = it.getString(ARG_CAT_COLOR, "#F59E0B")
            isSystem = it.getBoolean(ARG_IS_SYSTEM, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditCategoryBinding.inflate(inflater, container, false)
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

        binding.tvCategoryDialogTitle.text = if (categoryId == 0L) {
            "Tambah Kategori Kustom"
        } else {
            "Edit Kategori"
        }

        if (initialName.isNotEmpty()) {
            binding.etCategoryName.setText(initialName)
        }

        buildColorPalette()

        binding.btnCancelCategory.setOnClickListener {
            dismiss()
        }

        binding.btnSaveCategory.setOnClickListener {
            saveCategory()
        }
    }

    private fun buildColorPalette() {
        binding.layoutColorPalette.removeAllViews()
        colorViews.clear()

        val sizePx = (32 * resources.displayMetrics.density).toInt()
        val marginPx = (6 * resources.displayMetrics.density).toInt()

        for (hex in colorPalette) {
            val colorInt = Color.parseColor(hex)
            val colorContainer = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }
                gravity = Gravity.CENTER

                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorInt)
                    if (hex.equals(selectedColorHex, ignoreCase = true)) {
                        setStroke((3 * resources.displayMetrics.density).toInt(), Color.BLACK)
                    }
                }
                background = drawable

                setOnClickListener {
                    selectedColorHex = hex
                    refreshPaletteSelection()
                }
            }
            binding.layoutColorPalette.addView(colorContainer)
            colorViews.add(colorContainer)
        }
    }

    private fun refreshPaletteSelection() {
        for (i in colorPalette.indices) {
            val hex = colorPalette[i]
            val container = colorViews.getOrNull(i) ?: continue
            val colorInt = Color.parseColor(hex)
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorInt)
                if (hex.equals(selectedColorHex, ignoreCase = true)) {
                    setStroke((3 * resources.displayMetrics.density).toInt(), Color.BLACK)
                }
            }
            container.background = drawable
        }
    }

    private fun saveCategory() {
        val name = binding.etCategoryName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            binding.tilCategoryName.error = "Nama kategori harus diisi"
            return
        }

        val category = CategoryEntity(
            id = categoryId,
            name = name,
            colorHex = selectedColorHex,
            iconName = "ic_category_other",
            isSystem = isSystem
        )

        CoroutineScope(Dispatchers.IO).launch {
            val repo = FinzyApplication.instance.repository
            if (categoryId == 0L) {
                repo.insertCategory(category)
            } else {
                repo.updateCategory(category)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Kategori '$name' berhasil disimpan!",
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

    companion object {
        const val TAG = "EditCategoryDialog"
        private const val ARG_CAT_ID = "arg_cat_id"
        private const val ARG_CAT_NAME = "arg_cat_name"
        private const val ARG_CAT_COLOR = "arg_cat_color"
        private const val ARG_IS_SYSTEM = "arg_is_system"

        fun newInstance(
            id: Long = 0L,
            name: String = "",
            colorHex: String = "#F59E0B",
            isSystem: Boolean = false
        ): EditCategoryDialogFragment {
            return EditCategoryDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_CAT_ID, id)
                    putString(ARG_CAT_NAME, name)
                    putString(ARG_CAT_COLOR, colorHex)
                    putBoolean(ARG_IS_SYSTEM, isSystem)
                }
            }
        }
    }
}
