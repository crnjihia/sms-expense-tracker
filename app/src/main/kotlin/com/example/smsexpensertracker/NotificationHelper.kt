// path: app/src/main/kotlin/com/example/smsexpensertracker/NotificationHelper.kt
package com.example.smsexpensertracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Helper for managing notification channels, Android 13+ permission checks,
 * and dispatching 80% warning and 100% exhaustion budget alerts.
 */
object NotificationHelper {
    const val CHANNEL_ID = "budget_alerts"
    private const val CHANNEL_NAME = "Budget Alerts"
    private const val CHANNEL_DESCRIPTION = "Alerts when spending reaches 80% or 100% of budget limits"

    /**
     * Creates the notification channel for Android 8.0+ (API 26+).
     * Must be called during application initialization.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Checks whether the app is permitted to post notifications.
     * On Android 13 (API 33+), validates the runtime POST_NOTIFICATIONS permission.
     */
    fun canPostNotifications(context: Context): Boolean {
        val managerCompat = NotificationManagerCompat.from(context)
        if (!managerCompat.areNotificationsEnabled()) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Dispatches a localized budget alert notification (80% warning or 100% exhausted).
     * Returns true if posted successfully, false if notifications are disabled/unpermitted.
     */
    fun showBudgetAlert(
        context: Context,
        budgetId: Long,
        categoryName: String,
        spentKsh: Long,
        limitKsh: Long,
        month: String,
        isExhausted: Boolean
    ): Boolean {
        if (!canPostNotifications(context)) {
            return false
        }

        val percentage = if (limitKsh > 0L) {
            ((spentKsh.toDouble() / limitKsh.toDouble()) * 100).toInt()
        } else {
            100
        }
        val formattedSpent = String.format(Locale.US, "KES %,d", spentKsh)
        val formattedLimit = String.format(Locale.US, "KES %,d", limitKsh)

        val title = if (isExhausted) {
            "🚨 Budget Exhausted: $categoryName"
        } else {
            "⚠️ Budget Alert: $categoryName"
        }

        val message = if (isExhausted) {
            "You have reached or exceeded your $categoryName budget ($formattedSpent of $formattedLimit) for $month."
        } else {
            "You have used $percentage% ($formattedSpent of $formattedLimit) of your $categoryName budget for $month."
        }

        val notificationId = (budgetId % Int.MAX_VALUE).toInt()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    /** Sends a simple notification with the given title and text. */
    fun notify(context: Context, title: String, message: String, notificationId: Int) {
        if (!canPostNotifications(context)) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }
}
