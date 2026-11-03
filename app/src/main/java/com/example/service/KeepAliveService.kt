package com.example.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * KeepAliveService is a persistent Foreground Service designed to survive
 * task removal when the user swipes the app away from the Recent Apps list.
 *
 * Mechanisms:
 * 1. Returns [START_STICKY] in [onStartCommand] to tell Android OS to resurrect the service
 *    if killed under memory pressure.
 * 2. Runs as a low-priority Foreground Service with a continuous notification to keep the process
 *    in the foreground process tier.
 * 3. Overrides [onTaskRemoved] to resurrect itself and programmatically rebind the
 *    [TransactionNotificationListener] when the user closes the app from Recents.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "KeepAliveService created.")
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "KeepAliveService started with START_STICKY.")

        // Ensure notification is active
        startForegroundNotification()

        // Verify and revive NotificationListener connection
        TransactionNotificationListener.ensureServiceConnected(this)

        // START_STICKY informs Android to recreate the service if it gets killed
        return START_STICKY
    }

    /**
     * Triggered when the user swipes the app out of the Recent Apps screen.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "App swiped away from Recents! Executing resurrection sequence...")

        // 1. Programmatically attempt to restore/rebind the listener service
        TransactionNotificationListener.ensureServiceConnected(this)

        // 2. Schedule AlarmManager resurrection fallback in case Android OS terminates the process
        scheduleResurrectionAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "KeepAliveService destroyed! Scheduling resurrection...")
        scheduleResurrectionAlarm()
    }

    /**
     * Schedules a quick AlarmManager one-shot trigger to revive the service
     * if the process is completely cleared by the OS.
     */
    private fun scheduleResurrectionAlarm() {
        try {
            val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                REQUEST_CODE_RESTART,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerTime = SystemClock.elapsedRealtime() + 2000 // 2 seconds

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Resurrection alarm scheduled for KeepAliveService.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule resurrection alarm: ${e.message}")
        }
    }

    /**
     * Builds and starts a low-priority, ongoing foreground notification.
     */
    private fun startForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_KEEPALIVE,
                "AutoSpent Process Sentinel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga proses AutoSpent tetap aktif saat aplikasi ditutup"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_KEEPALIVE)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("AutoSpent Sentinel Active")
            .setContentText("Monitoring e-wallet transactions in background")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID_KEEPALIVE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID_KEEPALIVE, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startForeground for KeepAliveService: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID_KEEPALIVE = "autospent_keepalive_sentinel"
        private const val NOTIFICATION_ID_KEEPALIVE = 1002
        private const val REQUEST_CODE_RESTART = 2001

        /**
         * Starts the KeepAliveService safely across all Android versions.
         */
        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
                Log.i(TAG, "KeepAliveService start requested.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start KeepAliveService: ${e.message}", e)
            }
        }

        /**
         * Stops the KeepAliveService if explicitly required.
         */
        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.stopService(intent)
        }
    }
}
