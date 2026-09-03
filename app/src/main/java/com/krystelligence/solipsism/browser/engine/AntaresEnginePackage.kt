package com.krystelligence.solipsism.browser.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.krystelligence.solipsism.BuildConfig
import java.security.MessageDigest

class AntaresEnginePackage(private val context: Context) {
    data class Status(
        val installed: Boolean,
        val platformSupported: Boolean,
        val trusted: Boolean,
        val versionName: String?,
        val reason: String?,
    ) {
        val usable: Boolean get() = installed && platformSupported && trusted
    }

    fun status(): Status {
        // In the merged distribution the renderer is packaged in Solipsism itself.
        // Keep the status API so older chooser/settings code remains source-compatible.
        if (nativeRendererAvailable()) {
            return Status(
                installed = true,
                platformSupported = true,
                trusted = true,
                versionName = "0.2.1",
                reason = null,
            )
        }
        if (Build.VERSION.SDK_INT < AntaresProtocol.MIN_ANDROID_API ||
            Build.SUPPORTED_64_BIT_ABIS.none { it == "arm64-v8a" || it == "x86_64" }
        ) {
            return Status(
                installed = isInstalled(),
                platformSupported = false,
                trusted = false,
                versionName = null,
                reason = "Antares requires Android 13 or newer on a 64-bit ARM or x86 device",
            )
        }
        return statusOnSupportedPlatform()
    }

    private fun nativeRendererAvailable(): Boolean = runCatching {
        System.loadLibrary("c++_shared")
        System.loadLibrary("servoshell")
    }.isSuccess

    @RequiresApi(AntaresProtocol.MIN_ANDROID_API)
    private fun statusOnSupportedPlatform(): Status {
        val packageInfo = try {
            context.packageManager.getPackageInfo(
                AntaresProtocol.PACKAGE_NAME,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        if (packageInfo == null) {
            return Status(false, true, false, null, "Antares Engine is not installed")
        }
        val trusted = isTrusted(packageInfo.signingInfo)
        val reason = when {
            !trusted -> "The installed Antares Engine signature is not trusted by this build"
            else -> null
        }
        return Status(true, true, trusted, packageInfo.versionName, reason)
    }

    fun serviceIntent(): Intent = Intent(AntaresProtocol.SERVICE_ACTION).setPackage(AntaresProtocol.PACKAGE_NAME)

    @RequiresApi(AntaresProtocol.MIN_ANDROID_API)
    private fun isTrusted(signingInfo: android.content.pm.SigningInfo?): Boolean {
        val certificates = signingInfo?.let { info ->
            (info.apkContentsSigners.asList() + info.signingCertificateHistory.orEmpty().toList())
                .distinctBy { it.toByteArray().contentToString() }
                .map { it.toByteArray() }
        }.orEmpty()
        val pinned = BuildConfig.ANTARES_CERT_SHA256
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.replace(":", "").uppercase() }
            .toSet()
        val ownCertificates = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        ).signingInfo?.let { info ->
            (info.apkContentsSigners.asList() + info.signingCertificateHistory.orEmpty().toList())
                .distinctBy { it.toByteArray().contentToString() }
                .map { it.toByteArray() }
        }.orEmpty()
        val legacy = BuildConfig.ANTARES_LEGACY_CERT_SHA256
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.replace(":", "").uppercase() }
        val trustedDigests = (pinned + legacy + if (BuildConfig.DEBUG) {
            ownCertificates.map(::sha256)
        } else emptyList()).toSet()
        return certificates.any { sha256(it) in trustedDigests }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02X".format(it) }

    private fun isInstalled(): Boolean = runCatching {
        context.packageManager.getApplicationInfo(AntaresProtocol.PACKAGE_NAME, 0)
    }.isSuccess
}
