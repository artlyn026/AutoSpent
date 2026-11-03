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
import com.example.R
import com.example.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPreferences()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        checkListenerStatus()
    }

    private fun setupPreferences() {
        val prefs = requireActivity().getSharedPreferences("finzy_prefs", Context.MODE_PRIVATE)
        val isPremium = prefs.getBoolean("is_premium_mode", false)
        val budget = prefs.getFloat("monthly_budget", 2500000f)

        binding.switchPremiumMode.isChecked = isPremium
        updateModeUI(isPremium)

        binding.etBudgetLimit.setText(String.format(java.util.Locale.US, "%.0f", budget))
    }

    private fun setupListeners() {
        val prefs = requireActivity().getSharedPreferences("finzy_prefs", Context.MODE_PRIVATE)

        // Switch to toggle between Free Mode and Premium Mode
        binding.switchPremiumMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_premium_mode", isChecked).apply()
            updateModeUI(isChecked)
            val modeName = if (isChecked) "Finzy Pro (Premium)" else "Free Mode"
            Toast.makeText(requireContext(), "Beralih ke $modeName", Toast.LENGTH_SHORT).show()
        }

        // Button to open Android Notification Listener settings
        binding.btnOpenSystemSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Buka Pengaturan HP > Akses Notifikasi", Toast.LENGTH_SHORT).show()
            }
        }

        // Button to update monthly budget limit
        binding.btnSaveBudgetLimit.setOnClickListener {
            val inputStr = binding.etBudgetLimit.text?.toString()?.trim() ?: ""
            val newBudget = inputStr.toFloatOrNull()
            if (newBudget != null && newBudget > 0) {
                prefs.edit().putFloat("monthly_budget", newBudget).apply()
                Toast.makeText(requireContext(), "Target budget bulanan diperbarui!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Masukkan nominal budget yang valid", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateModeUI(isPremium: Boolean) {
        if (isPremium) {
            binding.tvModeTitle.text = getString(R.string.toggle_premium_mode)
            binding.tvModeStatus.text = "Semua fitur deteksi AI & notifikasi tanpa batas aktif"
            binding.tvUserStatusBadge.text = "Finzy Pro • Premium Student Member"
            binding.tvUserStatusBadge.setBackgroundColor(Color.parseColor("#FEF3C7"))
            binding.tvUserStatusBadge.setTextColor(Color.parseColor("#D97706"))
        } else {
            binding.tvModeTitle.text = getString(R.string.toggle_free_mode)
            binding.tvModeStatus.text = "Fitur dasar pelacakan e-wallet aktif"
            binding.tvUserStatusBadge.text = getString(R.string.user_tier_student)
            binding.tvUserStatusBadge.setBackgroundColor(Color.parseColor("#E0E7FF"))
            binding.tvUserStatusBadge.setTextColor(Color.parseColor("#4F46E5"))
        }
    }

    private fun checkListenerStatus() {
        val context = context ?: return
        val isActive = com.example.service.TransactionNotificationListener.isListenerActive(context)

        if (isActive) {
            binding.tvListenerStatusIndicator.text = getString(R.string.listener_status_active)
            binding.tvListenerStatusIndicator.setTextColor(Color.parseColor("#10B981"))
        } else {
            val isPermissionGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            if (isPermissionGranted) {
                // Permission is granted in settings, auto-rebind to heal connection
                com.example.service.TransactionNotificationListener.ensureServiceConnected(context)
                binding.tvListenerStatusIndicator.text = "Aktif (Auto-Rebound)"
                binding.tvListenerStatusIndicator.setTextColor(Color.parseColor("#10B981"))
            } else {
                binding.tvListenerStatusIndicator.text = getString(R.string.listener_status_inactive)
                binding.tvListenerStatusIndicator.setTextColor(Color.parseColor("#EF4444"))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = ProfileFragment()
    }
}
