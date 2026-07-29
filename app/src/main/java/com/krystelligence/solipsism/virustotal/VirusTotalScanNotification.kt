package com.krystelligence.solipsism.virustotal

import android.Manifest
import android.annotation.SuppressLint
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

    @SuppressLint("MissingPermission")
    fun showScanning(fileName: String) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(application, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_settings_shield)
            .setContentTitle(application.getString(R.string.virus_total_scanning))
            .setContentText(fileName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        runCatching { NotificationManagerCompat.from(application).notify(NOTIFICATION_ID, notification) }
    }

    @SuppressLint("MissingPermission")
    fun showBlocked(fileName: String, detections: Int?) {
        if (!canNotify()) return
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
        runCatching { NotificationManagerCompat.from(application).notify(NOTIFICATION_ID, notification) }
    }

    fun hide() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "virus_total_scans"
        const val NOTIFICATION_ID = 7301
    }
}
