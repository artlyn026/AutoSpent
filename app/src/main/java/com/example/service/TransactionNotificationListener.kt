package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.FinzyApplication
import com.example.MainActivity
import com.example.R
import com.example.data.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * TransactionNotificationListener is a permanent background service that intercepts
 * financial notifications from Indonesian e-wallets (GoPay, OVO, ShopeePay, DANA)
 * and banking apps (BCA, BRImo, SeaBank).
 *
 * Upgraded Features:
 * 1. Persistent Foreground Service (`startForeground()`):
 *    Prevents Android's battery manager/OEM task killers from killing the service during screen-off.
 *    Displays a low-priority, ongoing notification: "AutoSpent is active and guarding your expenses".
 * 2. Self-Healing Rebind Mechanism:
 *    Automatically detects disconnections and rebinds via `requestRebind()` and component toggling.
 */
class TransactionNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.i(TAG, "AutoSpent TransactionNotificationListener connected and active.")

        // 1. Promote to Foreground Service to ensure permanent background survival
        startPersistentForegroundService()
    }

    /**
     * Called when the system unbinds the listener (e.g., after app updates or low-memory kills).
     * Automatically requests rebind to recover the connection without user intervention.
     */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.w(TAG, "AutoSpent TransactionNotificationListener disconnected! Triggering automatic rebind...")

        // Android 7.0 (API 24)+ automatic rebind
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val componentName = ComponentName(this, TransactionNotificationListener::class.java)
            requestRebind(componentName)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        Log.i(TAG, "AutoSpent TransactionNotificationListener destroyed.")
    }

    /**
     * Creates a low-priority notification channel and starts the persistent foreground service.
     */
    private fun startPersistentForegroundService() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create low-priority notification channel for ongoing background monitoring
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_PERSISTENT,
                "AutoSpent Expense Guard Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga pemantauan transaksi e-wallet AutoSpent tetap aktif di latar belakang"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap action to return to MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val persistentNotification = NotificationCompat.Builder(this, CHANNEL_ID_PERSISTENT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("AutoSpent Active Guard")
            .setContentText("AutoSpent is active and guarding your expenses")
            .setSubText("Background Protection")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        // Start Foreground Service with Android 14+ foregroundServiceType compatibility
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID_PERSISTENT,
                    persistentNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID_PERSISTENT, persistentNotification)
            }
            Log.i(TAG, "Started persistent Foreground Service successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
        }
    }

    /**
     * Triggered every time a new notification is posted on the Android device.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            // Safely parse through NotificationParser (Source package checked first)
            val transaction = NotificationParser.parse(sbn)

            if (transaction != null) {
                Log.i(TAG, "Valid transaction from ${transaction.sourceApp}: Rp ${transaction.amount} at ${transaction.merchant}")

                // Immediately insert into Room Database on IO thread
                scope.launch {
                    try {
                        val repo = FinzyApplication.instance.repository
                        val insertedId = repo.insertTransaction(transaction)
                        Log.i(TAG, "Saved transaction #$insertedId to Room database.")

                        // Trigger anti-impulse reminder banner
                        triggerImpulseGuardAlert(transaction)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving intercepted transaction: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in onNotificationPosted: ${e.message}", e)
        }
    }

    /**
     * Sends an Anti-Impulsive buying nudge notification when an expense is detected.
     */
    private fun triggerImpulseGuardAlert(transaction: TransactionEntity) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "finzy_impulse_guard"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Finzy Anti-Impulsive Guard",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi pengingat pengeluaran impulsif untuk mahasiswa"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = String.format("%,.0f", transaction.amount).replace(',', '.')
        val alertMessage = "Pengeluaran ${transaction.sourceApp} Rp $formattedAmount di ${transaction.merchant} tercatat! Ketuk untuk menetapkan kategori."

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Finzy Guard: Transaksi Terdeteksi")
            .setContentText(alertMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alertMessage))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val TAG = "FinzyNotification"
        private const val CHANNEL_ID_PERSISTENT = "autospent_persistent_service"
        private const val NOTIFICATION_ID_PERSISTENT = 1001

        /**
         * State flag tracking whether the listener service is currently active and bound.
         */
        @Volatile
        var isConnected: Boolean = false
            private set

        /**
         * Checks if the listener permission is granted AND the binder is currently connected.
         */
        fun isListenerActive(context: Context): Boolean {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            val isPermissionGranted = enabledPackages.contains(context.packageName)
            return isPermissionGranted && isConnected
        }

        /**
         * Robust Rebind Mechanism:
         * Checks if the listener is active. If permission is granted in system settings
         * but the service has been killed by Android OS (screen-off or update),
         * requests the system to rebind the service automatically without sending
         * the user back to the system settings page.
         *
         * @return true if permission is granted (and rebind was triggered if disconnected),
         *         false if the user has not granted notification access in system settings.
         */
        fun ensureServiceConnected(context: Context): Boolean {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            val isPermissionGranted = enabledPackages.contains(context.packageName)

            if (!isPermissionGranted) {
                return false
            }

            // If permission is granted but the service is currently disconnected or inactive
            if (!isConnected) {
                Log.w(TAG, "Listener permission is granted but service is inactive. Triggering automatic rebind...")
                val componentName = ComponentName(context, TransactionNotificationListener::class.java)

                // 1. Android 7.0+ API requestRebind
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        requestRebind(componentName)
                        Log.d(TAG, "requestRebind() executed successfully.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to call requestRebind: ${e.message}")
                    }
                }

                // 2. Component Toggle Trick (Forces Android NotificationManagerService to immediately rebind)
                try {
                    val pm = context.packageManager
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Log.d(TAG, "Component re-toggle executed to restore listener binder.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed component toggle: ${e.message}")
                }
            }

            return true
        }
    }
}
