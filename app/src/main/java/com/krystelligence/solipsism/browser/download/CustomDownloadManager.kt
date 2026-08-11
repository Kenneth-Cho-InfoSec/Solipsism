package com.krystelligence.solipsism.browser.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** Dispatches direct URL downloads to a user-selected installed application. */
object CustomDownloadManager {

    private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun isValidPackageName(packageName: String): Boolean = packagePattern.matches(packageName)

    fun installedLabel(context: Context, packageName: String): String? = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
            .loadLabel(context.packageManager).toString()
    }.getOrNull()

    fun dispatch(
        context: Context,
        packageName: String,
        url: String,
        mimeType: String?,
        fileName: String?
    ): Boolean {
        if (!isValidPackageName(packageName) || installedLabel(context, packageName) == null) return false
        val intent = when (packageName) {
            "com.dv.adm" -> Intent(Intent.ACTION_MAIN).apply {
                component = android.content.ComponentName(packageName, "com.dv.adm.AEditor")
                putExtra(Intent.EXTRA_TEXT, url)
                fileName?.takeIf(String::isNotBlank)?.let {
                    putExtra("com.android.extra.filename", it)
                }
            }
            "idm.internet.download.manager", "idm.internet.download.manager.plus" ->
                runCatching {
                    Intent.parseUri(
                        "intent:${url}#Intent;scheme=idmdownload;package=$packageName;" +
                            "S.title=${Uri.encode(fileName.orEmpty())};end",
                        Intent.URI_INTENT_SCHEME
                    )
                }.getOrElse { return false }
            else -> if (mimeType.isNullOrBlank()) {
                Intent(Intent.ACTION_VIEW, url.toUri()).apply { setPackage(packageName) }
            } else {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(url.toUri(), mimeType)
                    setPackage(packageName)
                }
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!mimeType.isNullOrBlank() && type == null) setDataAndType(data, mimeType)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
