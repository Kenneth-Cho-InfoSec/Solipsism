package com.krystelligence.solipsism.adblock.source

import com.krystelligence.solipsism.adblock.parser.HostsFileParser
import com.krystelligence.solipsism.browser.di.HostsClient
import com.krystelligence.solipsism.browser.di.NetworkScheduler
import com.krystelligence.solipsism.log.Logger
import com.krystelligence.solipsism.preference.UserPreferences
import com.krystelligence.solipsism.preference.userAgent
import android.app.Application
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.coroutines.resume

/**
 * A [HostsDataSource] that loads hosts from an [HttpUrl].
 */
class UrlHostsDataSource @AssistedInject constructor(
    @Assisted private val url: HttpUrl,
    @HostsClient private val okHttpClient: Single<OkHttpClient>,
    private val logger: Logger,
    private val userPreferences: UserPreferences,
    private val application: Application,
    @NetworkScheduler
    private val networkDispatcher: CoroutineDispatcher,
) : HostsDataSource {

    override suspend fun loadHosts(): HostsResult = withContext(networkDispatcher) {
        if (!url.isHttps) {
            return@withContext HostsResult.Failure(IOException("Remote hosts source must use HTTPS"))
        }

        suspendCancellableCoroutine<HostsResult> { emitter ->
            val client = okHttpClient.blockingGet()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userPreferences.userAgent(application))
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    emitter.resume(HostsResult.Failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { successfulResponse ->
                        if (!successfulResponse.isSuccessful) {
                            return emitter.resume(HostsResult.Failure(IOException("Error reading remote file")))
                        }
                        if (successfulResponse.body.contentLength() > MAX_HOSTS_FILE_BYTES) {
                            return emitter.resume(HostsResult.Failure(IOException("Remote hosts file is too large")))
                        }
                        val input = InputStreamReader(
                            LimitedInputStream(successfulResponse.body.byteStream(), MAX_HOSTS_FILE_BYTES)
                        )

                        val hostsFileParser = HostsFileParser(logger)

                        val domains = hostsFileParser.parseInput(input)

                        logger.log(TAG, "Loaded ${domains.size} domains")
                        emitter.resume(HostsResult.Success(domains))
                    }
                }
            })
        }
    }

    override suspend fun identifier(): String = url.toString()

    companion object {
        private const val TAG = "UrlHostsDataSource"
        private const val MAX_HOSTS_FILE_BYTES = 5L * 1024L * 1024L
    }

    private class LimitedInputStream(
        private val delegate: InputStream,
        private val maxBytes: Long
    ) : InputStream() {
        private var bytesRead = 0L

        override fun read(): Int {
            if (bytesRead >= maxBytes) {
                throw IOException("Remote hosts file is too large")
            }
            return delegate.read().also {
                if (it != -1) {
                    bytesRead++
                }
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesRead >= maxBytes) {
                throw IOException("Remote hosts file is too large")
            }
            val allowed = minOf(length.toLong(), maxBytes - bytesRead).toInt()
            return delegate.read(buffer, offset, allowed).also {
                if (it > 0) {
                    bytesRead += it
                }
            }
        }
    }

    /**
     * Used to create the data source.
     */
    @AssistedFactory
    interface Factory {
        /**
         * Create the data source for the provided URL.
         */
        fun create(url: HttpUrl): UrlHostsDataSource
    }

}
