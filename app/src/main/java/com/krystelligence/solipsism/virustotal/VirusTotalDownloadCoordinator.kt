package com.krystelligence.solipsism.virustotal

import android.app.Activity
import android.app.Application
import android.util.Base64
import com.krystelligence.solipsism.download.DownloadHandler
import com.krystelligence.solipsism.malware.LocalMalwareDatabase
import com.krystelligence.solipsism.preference.UserPreferences
import io.reactivex.rxjava3.core.Single
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class VirusTotalDownloadRequest(
    val url: String,
    val fileName: String,
    val userAgent: String?,
    val cookie: String?,
    val mimeType: String?,
    val blobData: String?
)

sealed interface VirusTotalDownloadResult {
    data class Saved(
        val storedUrl: String,
        val sha256: String,
        val stats: VirusTotalStats?
    ) : VirusTotalDownloadResult

    data class Blocked(
        val sha256: String,
        val source: DetectionSource,
        val stats: VirusTotalStats? = null
    ) : VirusTotalDownloadResult

    enum class DetectionSource {
        LOCAL_DATABASE,
        VIRUS_TOTAL
    }
}

@Singleton
class VirusTotalDownloadCoordinator @Inject constructor(
    private val application: Application,
    private val localDatabase: LocalMalwareDatabase,
    private val scanner: VirusTotalScanner,
    private val apiKeyStore: VirusTotalApiKeyStore,
    private val downloadHandler: DownloadHandler
) {
    private val downloadClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .callTimeout(30, TimeUnit.MINUTES)
        .build()

    fun scanAndSave(
        activity: Activity,
        preferences: UserPreferences,
        request: VirusTotalDownloadRequest,
        cancellation: VirusTotalCancellationSignal
    ): Single<VirusTotalDownloadResult> = Single.fromCallable {
        val staged = File.createTempFile("vt_", ".download", File(application.cacheDir, "scan").also {
            if (!it.isDirectory && !it.mkdirs()) throw IOException("Unable to create scan directory")
        })
        try {
            val sha256 = stageAndHash(request, staged, cancellation)
            cancellation.throwIfCancelled()
            if (localDatabase.contains(
                    sha256,
                    preferences.malwareDefinitionsAutoUpdate,
                    cancellation
                )
            ) {
                VirusTotalDownloadResult.Blocked(
                    sha256 = sha256,
                    source = VirusTotalDownloadResult.DetectionSource.LOCAL_DATABASE
                )
            } else {
                val cloudVerdict = if (
                    preferences.virusTotalCloudEnabled && apiKeyStore.hasKey()
                ) {
                    scanner.scan(staged, sha256, request.mimeType, cancellation)
                } else {
                    null
                }
                when (cloudVerdict) {
                    is VirusTotalVerdict.Blocked -> VirusTotalDownloadResult.Blocked(
                        sha256 = cloudVerdict.sha256,
                        source = VirusTotalDownloadResult.DetectionSource.VIRUS_TOTAL,
                        stats = cloudVerdict.stats
                    )
                    else -> {
                        cancellation.throwIfCancelled()
                        val storedUrl = downloadHandler.publishScannedFile(
                            activity,
                            preferences,
                            staged,
                            request.fileName,
                            request.mimeType
                        )
                        VirusTotalDownloadResult.Saved(
                            storedUrl = storedUrl,
                            sha256 = sha256,
                            stats = (cloudVerdict as? VirusTotalVerdict.Clean)?.stats
                        )
                    }
                }
            }
        } finally {
            staged.delete()
        }
    }

    private fun stageAndHash(
        request: VirusTotalDownloadRequest,
        outputFile: File,
        cancellation: VirusTotalCancellationSignal
    ): String {
        cancellation.throwIfCancelled()
        val digest = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(FileOutputStream(outputFile), digest).use { output ->
            if (request.blobData != null) {
                if (request.blobData.length.toLong() > MAXIMUM_BASE64_CHARACTERS) {
                    throw VirusTotalException(
                        VirusTotalException.Reason.FILE_TOO_LARGE,
                        "Malware Scanner stages files up to 650 MB"
                    )
                }
                output.write(Base64.decode(request.blobData, Base64.DEFAULT))
            } else {
                val httpRequest = Request.Builder()
                    .url(request.url)
                    .apply {
                        request.userAgent?.takeIf(String::isNotBlank)?.let {
                            header("User-Agent", it)
                        }
                        request.cookie?.takeIf(String::isNotBlank)?.let {
                            header("Cookie", it)
                        }
                    }
                    .get()
                    .build()
                val call = downloadClient.newCall(httpRequest)
                cancellation.track(call)
                try {
                    call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Download server returned HTTP ${response.code}")
                    }
                    if (response.body.contentLength() > MAXIMUM_FILE_BYTES) {
                        throw VirusTotalException(
                            VirusTotalException.Reason.FILE_TOO_LARGE,
                            "Malware Scanner stages files up to 650 MB"
                        )
                    }
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            cancellation.throwIfCancelled()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAXIMUM_FILE_BYTES) {
                                throw VirusTotalException(
                                    VirusTotalException.Reason.FILE_TOO_LARGE,
                                    "Malware Scanner stages files up to 650 MB"
                                )
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                    }
                } finally {
                    cancellation.clear(call)
                }
            }
        }
        cancellation.throwIfCancelled()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAXIMUM_FILE_BYTES = 650L * 1024L * 1024L
        const val MAXIMUM_BASE64_CHARACTERS = MAXIMUM_FILE_BYTES * 4L / 3L + 4L
    }
}
