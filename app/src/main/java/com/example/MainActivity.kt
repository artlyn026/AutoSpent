package com.example

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.databinding.ActivityMainBinding
import com.example.ui.fragment.AnalyticsFragment
import com.example.ui.fragment.DashboardFragment
import com.example.ui.fragment.HistoryFragment
import com.example.ui.fragment.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val dashboardFragment by lazy { DashboardFragment.newInstance() }
    private val historyFragment by lazy { HistoryFragment.newInstance() }
    private val analyticsFragment by lazy { AnalyticsFragment.newInstance() }
    private val profileFragment by lazy { ProfileFragment.newInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Enable Edge-to-Edge display
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Safe Area Insets (Universal WindowInsets for status bar, cutout & nav bar)
        applyWindowInsets()

        // 3. Navigation Setup
        setupNavigation()

        // Set default fragment to Dashboard
        if (savedInstanceState == null) {
            loadFragment(dashboardFragment)
        }

        // 4. Start KeepAliveService to prevent swipe-away process termination
        com.example.service.KeepAliveService.start(this)

        // 5. Enqueue AndroidX WorkManager periodic revival worker
        com.example.service.ReviveWorker.schedule(this)

        // 6. Check and prompt user for Notification Access permission
        promptNotificationListenerPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        // Ensure KeepAliveService is running and listener binder is connected
        com.example.service.KeepAliveService.start(this)
        com.example.service.TransactionNotificationListener.ensureServiceConnected(this)
    }

    /**
     * Dynamically applies window insets to prevent the top header from overlapping
     * with the Android system status bar (time, battery, signal) and camera cutouts/notches.
     */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            // Dynamically add top padding to the top toolbar to avoid status bar overlap
            binding.topToolbar.updatePadding(
                top = systemBars.top
            )

            // Dynamically add bottom padding to bottom navigation for gesture navigation bars
            binding.bottomNavigation.updatePadding(
                bottom = systemBars.bottom
            )

            windowInsets
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    loadFragment(dashboardFragment)
                    true
                }
                R.id.navigation_history -> {
                    loadFragment(historyFragment)
                    true
                }
                R.id.navigation_analytics -> {
                    loadFragment(analyticsFragment)
                    true
                }
                R.id.navigation_profile -> {
                    loadFragment(profileFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /**
     * Checks if the NotificationListenerService is enabled for this app.
     * Prompts the user with an explanation dialog and opens system settings if not granted.
     */
    private fun promptNotificationListenerPermissionIfNeeded() {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this)
        val isPermissionGranted = enabledPackages.contains(packageName)

        if (!isPermissionGranted) {
            AlertDialog.Builder(this)
                .setTitle("Aktifkan Akses Notifikasi")
                .setMessage(
                    "Finzy (AutoSpent) membutuhkan izin akses notifikasi agar dapat mendeteksi " +
                    "transaksi dari e-wallet (GoPay, ShopeePay, DANA, OVO, BCA) secara otomatis di background."
                )
                .setPositiveButton("Buka Pengaturan") { _, _ ->
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        val appDetailIntent = Intent(Settings.ACTION_SETTINGS)
                        startActivity(appDetailIntent)
                    }
                }
                .setNegativeButton("Nanti", null)
                .show()
        }
    }
}
