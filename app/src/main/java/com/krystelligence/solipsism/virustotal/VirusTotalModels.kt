package com.krystelligence.solipsism.virustotal

import okhttp3.Call
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

data class VirusTotalStats(
    val malicious: Int,
    val suspicious: Int,
    val harmless: Int,
    val undetected: Int
)

sealed interface VirusTotalVerdict {
    data class Clean(val stats: VirusTotalStats, val sha256: String) : VirusTotalVerdict
    data class Blocked(val stats: VirusTotalStats, val sha256: String) : VirusTotalVerdict
}

class VirusTotalException(
    val reason: Reason,
    message: String
) : Exception(message) {
    enum class Reason {
        INVALID_API_KEY,
        RATE_LIMITED,
        FILE_TOO_LARGE,
        NETWORK,
        ANALYSIS_TIMEOUT,
        INVALID_RESPONSE
    }
}

class VirusTotalCancellationSignal {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var activeCall: Call? = null

    fun cancel() {
        cancelled.set(true)
        activeCall?.cancel()
    }

    internal fun throwIfCancelled() {
        if (cancelled.get()) throw CancellationException("VirusTotal scan cancelled")
    }

    internal fun track(call: Call) {
        throwIfCancelled()
        activeCall = call
        if (cancelled.get()) call.cancel()
    }

    internal fun clear(call: Call) {
        if (activeCall === call) activeCall = null
    }
}
