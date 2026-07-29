package com.krystelligence.solipsism.virustotal

import android.app.Application
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirusTotalScanner @Inject constructor(
    private val application: Application,
    private val apiKeyStore: VirusTotalApiKeyStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.MINUTES)
        .callTimeout(12, TimeUnit.MINUTES)
        .build()

    fun scan(
        file: File,
        sha256: String,
        mimeType: String?,
        cancellation: VirusTotalCancellationSignal
    ): VirusTotalVerdict {
        cancellation.throwIfCancelled()
        val apiKey = apiKeyStore.get()
        if (apiKey.isBlank()) {
            throw VirusTotalException(
                VirusTotalException.Reason.INVALID_API_KEY,
                application.getString(com.krystelligence.solipsism.R.string.virus_total_api_key_missing)
            )
        }

        val existing = getExistingReport(apiKey, sha256, cancellation)
        val stats = existing ?: uploadAndAwaitAnalysis(apiKey, file, mimeType, cancellation)
        return if (stats.malicious > 0) {
            VirusTotalVerdict.Blocked(stats, sha256)
        } else {
            VirusTotalVerdict.Clean(stats, sha256)
        }
    }

    private fun getExistingReport(
        apiKey: String,
        sha256: String,
        cancellation: VirusTotalCancellationSignal
    ): VirusTotalStats? {
        val request = Request.Builder()
            .url("$API_BASE/files/$sha256")
            .header(API_KEY_HEADER, apiKey)
            .get()
            .build()
        return execute(request, cancellation) { code, body ->
            when (code) {
                200 -> parseFileStats(JSONObject(body))
                404 -> null
                else -> throwForResponse(code, body)
            }
        }
    }

    private fun uploadAndAwaitAnalysis(
        apiKey: String,
        file: File,
        mimeType: String?,
        cancellation: VirusTotalCancellationSignal
    ): VirusTotalStats {
        if (file.length() > MAXIMUM_UPLOAD_BYTES) {
            throw VirusTotalException(
                VirusTotalException.Reason.FILE_TOO_LARGE,
                "VirusTotal accepts files up to 650 MB"
            )
        }

        val uploadUrl = if (file.length() > STANDARD_UPLOAD_BYTES) {
            getLargeFileUploadUrl(apiKey, cancellation)
        } else {
            "$API_BASE/files"
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody(mimeType?.toMediaTypeOrNull())
            )
            .build()
        val request = Request.Builder()
            .url(uploadUrl)
            .header(API_KEY_HEADER, apiKey)
            .post(body)
            .build()
        val analysisId = execute(request, cancellation) { code, responseBody ->
            if (code !in 200..299) throwForResponse(code, responseBody)
            JSONObject(responseBody).getJSONObject("data").getString("id")
        }
        return awaitAnalysis(apiKey, analysisId, cancellation)
    }

    private fun getLargeFileUploadUrl(
        apiKey: String,
        cancellation: VirusTotalCancellationSignal
    ): String {
        val request = Request.Builder()
            .url("$API_BASE/files/upload_url")
            .header(API_KEY_HEADER, apiKey)
            .get()
            .build()
        return execute(request, cancellation) { code, body ->
            if (code !in 200..299) throwForResponse(code, body)
            val data = JSONObject(body).get("data")
            when (data) {
                is String -> data
                is JSONObject -> data.getString("url")
                else -> throw VirusTotalException(
                    VirusTotalException.Reason.INVALID_RESPONSE,
                    "Invalid large-file upload URL"
                )
            }
        }
    }

    private fun awaitAnalysis(
        apiKey: String,
        analysisId: String,
        cancellation: VirusTotalCancellationSignal
    ): VirusTotalStats {
        repeat(MAXIMUM_POLL_ATTEMPTS) { attempt ->
            cancellation.throwIfCancelled()
            if (attempt > 0) Thread.sleep(POLL_INTERVAL_MILLIS)
            cancellation.throwIfCancelled()
            val request = Request.Builder()
                .url("$API_BASE/analyses/$analysisId")
                .header(API_KEY_HEADER, apiKey)
                .get()
                .build()
            val result = execute(request, cancellation) { code, body ->
                if (code !in 200..299) throwForResponse(code, body)
                val attributes = JSONObject(body).getJSONObject("data").getJSONObject("attributes")
                if (attributes.optString("status") != "completed") {
                    null
                } else {
                    parseStats(attributes.getJSONObject("stats"))
                }
            }
            if (result != null) return result
        }
        throw VirusTotalException(
            VirusTotalException.Reason.ANALYSIS_TIMEOUT,
            "VirusTotal did not finish the analysis in time"
        )
    }

    private fun parseFileStats(root: JSONObject): VirusTotalStats =
        parseStats(
            root.getJSONObject("data")
                .getJSONObject("attributes")
                .getJSONObject("last_analysis_stats")
        )

    private fun parseStats(stats: JSONObject) = VirusTotalStats(
        malicious = stats.optInt("malicious"),
        suspicious = stats.optInt("suspicious"),
        harmless = stats.optInt("harmless"),
        undetected = stats.optInt("undetected")
    )

    private fun throwForResponse(code: Int, body: String): Nothing {
        val reason = when (code) {
            401, 403 -> VirusTotalException.Reason.INVALID_API_KEY
            429 -> VirusTotalException.Reason.RATE_LIMITED
            else -> VirusTotalException.Reason.INVALID_RESPONSE
        }
        val apiMessage = runCatching {
            JSONObject(body).getJSONObject("error").optString("message")
        }.getOrNull()
        throw VirusTotalException(reason, apiMessage?.takeIf(String::isNotBlank) ?: "VirusTotal error $code")
    }

    private fun <T> execute(
        request: Request,
        cancellation: VirusTotalCancellationSignal,
        block: (Int, String) -> T
    ): T {
        val call = client.newCall(request)
        cancellation.track(call)
        try {
            call.execute().use { response ->
                return block(response.code, response.body.string())
            }
        } catch (exception: VirusTotalException) {
            throw exception
        } catch (exception: IOException) {
            throw VirusTotalException(
                VirusTotalException.Reason.NETWORK,
                exception.message ?: "Network error"
            )
        } catch (exception: Exception) {
            cancellation.throwIfCancelled()
            throw VirusTotalException(
                VirusTotalException.Reason.INVALID_RESPONSE,
                exception.message ?: "Invalid VirusTotal response"
            )
        } finally {
            cancellation.clear(call)
        }
    }

    private companion object {
        const val API_BASE = "https://www.virustotal.com/api/v3"
        const val API_KEY_HEADER = "x-apikey"
        const val STANDARD_UPLOAD_BYTES = 32L * 1024L * 1024L
        const val MAXIMUM_UPLOAD_BYTES = 650L * 1024L * 1024L
        const val MAXIMUM_POLL_ATTEMPTS = 15
        const val POLL_INTERVAL_MILLIS = 20_000L
    }
}
