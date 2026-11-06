package com.example.smsexpensertracker

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkerFactory
import dagger.hilt.android.HiltAndroidApp
import com.example.smsexpensertracker.NotificationHelper

import androidx.work.WorkManager

/**
 * Application class that enables Hilt DI and provides a WorkManager configuration.
 */
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        // Initialise the budget alert notification channel.
        NotificationHelper.createChannel(this)
        // Schedule periodic budget checks (every 12 hours).
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<BudgetCheckWorker>(12, java.util.concurrent.TimeUnit.HOURS)
            .build()
        androidx.work.WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "budget_check_periodic",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }


    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
