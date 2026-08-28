package com.krystelligence.solipsism.browser.tab

/**
 * Converts Servo's coarse Started/Complete navigation notifications into responsive browser UI.
 *
 * Servo's Android demo documents that Complete can be omitted for cached navigations. The hard
 * timeout prevents a permanently stale loading bar, while [pageBecameOperational] finishes sooner
 * once Servo has supplied a usable document title. A real completion notification always wins.
 */
internal class AntaresLoadingTracker(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val report: (Int) -> Unit,
) {
    var progress: Int = 0
        private set

    private val advance = object : Runnable {
        override fun run() {
            if (progress !in START_PROGRESS until MAX_SYNTHETIC_PROGRESS) return
            update(
                when {
                    progress < 70 -> (progress + 15).coerceAtMost(70)
                    else -> (progress + 4).coerceAtMost(MAX_SYNTHETIC_PROGRESS)
                },
            )
            if (progress < MAX_SYNTHETIC_PROGRESS) schedule(this, ADVANCE_INTERVAL_MS)
        }
    }
    private val forceComplete = Runnable(::complete)

    fun started() {
        cancelPending()
        update(START_PROGRESS)
        schedule(advance, ADVANCE_INTERVAL_MS)
        schedule(forceComplete, MAX_LOADING_DURATION_MS)
    }

    fun pageBecameOperational() {
        if (progress !in START_PROGRESS..MAX_SYNTHETIC_PROGRESS) return
        cancel(forceComplete)
        schedule(forceComplete, OPERATIONAL_SETTLE_DURATION_MS)
    }

    fun complete() {
        cancelPending()
        update(COMPLETE_PROGRESS)
    }

    fun dispose() = cancelPending()

    private fun cancelPending() {
        cancel(advance)
        cancel(forceComplete)
    }

    private fun update(value: Int) {
        if (progress == value) return
        progress = value
        report(value)
    }

    internal companion object {
        const val START_PROGRESS = 10
        const val MAX_SYNTHETIC_PROGRESS = 90
        const val COMPLETE_PROGRESS = 100
        const val ADVANCE_INTERVAL_MS = 280L
        const val OPERATIONAL_SETTLE_DURATION_MS = 1_500L
        const val MAX_LOADING_DURATION_MS = 10_000L
    }
}
