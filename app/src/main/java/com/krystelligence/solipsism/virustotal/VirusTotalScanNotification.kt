package com.krystelligence.solipsism.virustotal

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.krystelligence.solipsism.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirusTotalScanNotification @Inject constructor(
    private val application: Application,
    private val notificationManager: NotificationManager
) {
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    application.getString(R.string.virus_total_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    fun showScanning(fileName: String) {
        if (!notificationsAllowed()) return
        val notification = NotificationCompat.Builder(application, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_settings_shield)
            .setContentTitle(application.getString(R.string.virus_total_scanning))
            .setContentText(fileName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        try {
            NotificationManagerCompat.from(application).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and the post.
        }
    }

    fun showBlocked(fileName: String, detections: Int?) {
        if (!notificationsAllowed()) return
        val notification = NotificationCompat.Builder(application, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_settings_shield)
            .setContentTitle(application.getString(R.string.virus_total_download_blocked))
            .setContentText(
                detections?.let {
                    application.getString(R.string.virus_total_detection_count, it, fileName)
                } ?: application.getString(R.string.malware_local_detection, fileName)
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        try {
            NotificationManagerCompat.from(application).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and the post.
        }
    }

    fun hide() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            (ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED &&
                NotificationManagerCompat.from(application).areNotificationsEnabled())

    private companion object {
        const val CHANNEL_ID = "virus_total_scans"
        const val NOTIFICATION_ID = 7301
    }
}
