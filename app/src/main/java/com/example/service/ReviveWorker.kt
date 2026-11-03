package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * ReviveWorker is a periodic AndroidX WorkManager worker.
 *
 * Its role is to run at periodic intervals even when the app is completely closed
 * or removed from Recent Apps, inspecting whether the [TransactionNotificationListener]
 * is alive and bound. If killed or disconnected, it automatically revives the listener
 * using [TransactionNotificationListener.ensureServiceConnected].
 */
class ReviveWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "ReviveWorker running health check on NotificationListenerService...")

        val isActive = TransactionNotificationListener.isListenerActive(applicationContext)

        if (!isActive) {
            Log.w(TAG, "Listener is NOT active. Reviving listener connection and KeepAliveService...")
            // Programmatically request rebind and execute component toggle
            TransactionNotificationListener.ensureServiceConnected(applicationContext)

            // Ensure KeepAliveService is also running
            KeepAliveService.start(applicationContext)
        } else {
            Log.i(TAG, "Health check passed: NotificationListener is currently connected and active.")
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "ReviveWorker"
        private const val UNIQUE_PERIODIC_WORK_NAME = "autospent_revive_periodic_worker"
        private const val UNIQUE_ONE_TIME_WORK_NAME = "autospent_revive_onetime_worker"

        /**
         * Schedules periodic background revival checks and executes an initial immediate check.
         */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)

            // 1. Schedule Periodic Work (runs every 15 minutes - minimum interval in Android)
            val periodicWork = PeriodicWorkRequestBuilder<ReviveWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(Constraints.NONE)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )

            // 2. Also run an immediate one-time check on app launch
            val oneTimeWork = OneTimeWorkRequestBuilder<ReviveWorker>()
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeWork
            )

            Log.i(TAG, "ReviveWorker periodic and one-time tasks enqueued successfully.")
        }
    }
}
