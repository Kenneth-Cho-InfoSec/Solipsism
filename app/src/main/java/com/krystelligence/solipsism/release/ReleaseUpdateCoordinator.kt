package com.krystelligence.solipsism.release

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krystelligence.solipsism.BuildConfig
import com.krystelligence.solipsism.R
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.utils.openUrlInOwnBrowser

class ReleaseUpdateCoordinator(
    private val context: Context,
    private val preferences: UserPreferences,
    private val repository: ReleaseRepository = ReleaseRepository(),
    private val installerPackage: String? = context.installerPackageName(),
) {
    suspend fun check(activity: AppCompatActivity) {
        val currentVersion = BuildConfig.VERSION_NAME
        val firstRun = preferences.lastAcknowledgedAppVersion == null
        val versionChanged = !firstRun && preferences.lastAcknowledgedAppVersion != currentVersion

        if (firstRun) {
            preferences.lastAcknowledgedAppVersion = currentVersion
        } else if (versionChanged && preferences.releaseNotesEnabled) {
            val release = repository.fetchExact(currentVersion)
            if (release != null && preferences.releaseNotesShownVersion != release.tagName) {
                showReleaseNotes(activity, release)
                return
            }
            if (release == null) return
            preferences.releaseNotesShownVersion = release.tagName
            preferences.lastAcknowledgedAppVersion = currentVersion
        } else if (versionChanged) {
            preferences.lastAcknowledgedAppVersion = currentVersion
        }

        if (!preferences.updateNotificationsEnabled || System.currentTimeMillis() < preferences.updateReminderSnoozeUntil) return
        // Store builds update through the store. Offering the GitHub APK would install a
        // package signed with a different key and break in-place updates (#57).
        if (isStoreInstaller(installerPackage)) return
        val latest = repository.fetchLatestStable() ?: return
        if (latest.prerelease || ReleaseVersion.compare(currentVersion, latest.tagName) >= 0) return
        if (preferences.lastNotifiedReleaseTag == latest.tagName) return
        showUpdatePrompt(activity, latest)
    }

    private fun showReleaseNotes(activity: AppCompatActivity, release: ReleaseInfo) {
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.release_notes_title, release.tagName))
            .setMessage(release.body.ifBlank { activity.getString(R.string.release_notes_unavailable) })
            .setNeutralButton(R.string.release_notes_view_online) { _, _ -> openReleasePage(release) }
            .setPositiveButton(R.string.action_done, null)
            .create()
        dialog.setOnDismissListener {
            preferences.releaseNotesShownVersion = release.tagName
            preferences.lastAcknowledgedAppVersion = BuildConfig.VERSION_NAME
        }
        dialog.show()
    }

    private fun showUpdatePrompt(activity: AppCompatActivity, release: ReleaseInfo) {
        preferences.lastNotifiedReleaseTag = release.tagName
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.update_available_title, release.tagName))
            .setMessage(R.string.update_available_summary)
            .setNeutralButton(R.string.update_remind_later) { _, _ ->
                preferences.updateReminderSnoozeUntil = System.currentTimeMillis() + SNOOZE_MS
            }
            .setNegativeButton(R.string.action_not_now, null)
        release.preferredApk?.let { asset ->
            builder.setPositiveButton(R.string.update_download) { _, _ ->
                context.openUrlInOwnBrowser(asset.browserDownloadUrl)
            }
        } ?: builder.setPositiveButton(R.string.release_notes_view_online) { _, _ -> openReleasePage(release) }
        builder.show()
    }

    private fun openReleasePage(release: ReleaseInfo) {
        context.openUrlInOwnBrowser(release.htmlUrl)
    }

    private companion object {
        const val SNOOZE_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

/** Package names of the store clients that build and sign Solipsism themselves. */
private val STORE_INSTALLER_PACKAGES = setOf(
    "org.fdroid.fdroid",
    "org.fdroid.basic",
    "org.fdroid.fdroid.privileged",
)

internal fun isStoreInstaller(installerPackage: String?): Boolean =
    installerPackage != null && installerPackage in STORE_INSTALLER_PACKAGES

private fun Context.installerPackageName(): String? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        packageManager.getInstallSourceInfo(packageName).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstallerPackageName(packageName)
    }
}.getOrNull()
