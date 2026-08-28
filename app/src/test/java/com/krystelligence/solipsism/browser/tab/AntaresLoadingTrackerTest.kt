package com.krystelligence.solipsism.browser.tab

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class AntaresLoadingTrackerTest {
    @Test
    fun `a real completion cancels fallbacks and reports complete`() {
        val scheduler = FakeScheduler()
        val reports = mutableListOf<Int>()
        val tracker = tracker(scheduler, reports)

        tracker.started()
        scheduler.run(AntaresLoadingTracker.ADVANCE_INTERVAL_MS)
        tracker.complete()
        scheduler.runAll()

        assertThat(reports.first()).isEqualTo(10)
        assertThat(reports).contains(25, 100)
        assertThat(reports.last()).isEqualTo(100)
    }

    @Test
    fun `an operational page completes when Servo omits load ended`() {
        val scheduler = FakeScheduler()
        val reports = mutableListOf<Int>()
        val tracker = tracker(scheduler, reports)

        tracker.started()
        tracker.pageBecameOperational()
        scheduler.run(AntaresLoadingTracker.OPERATIONAL_SETTLE_DURATION_MS)

        assertThat(reports.last()).isEqualTo(100)
        assertThat(tracker.progress).isEqualTo(100)
    }

    @Test
    fun `a missing operational and completion callback cannot leave progress stuck`() {
        val scheduler = FakeScheduler()
        val reports = mutableListOf<Int>()
        val tracker = tracker(scheduler, reports)

        tracker.started()
        scheduler.run(AntaresLoadingTracker.MAX_LOADING_DURATION_MS)

        assertThat(reports).contains(10)
        assertThat(reports.last()).isEqualTo(100)
    }

    private fun tracker(scheduler: FakeScheduler, reports: MutableList<Int>) =
        AntaresLoadingTracker(
            schedule = scheduler::schedule,
            cancel = scheduler::cancel,
            report = reports::add,
        )

    private class FakeScheduler {
        private data class Task(val runnable: Runnable, val at: Long)

        private val tasks = mutableListOf<Task>()
        private var now = 0L

        fun schedule(runnable: Runnable, delay: Long) {
            tasks += Task(runnable, now + delay)
        }

        fun cancel(runnable: Runnable) {
            tasks.removeAll { it.runnable === runnable }
        }

        fun run(duration: Long) {
            val end = now + duration
            while (true) {
                val next = tasks.filter { it.at <= end }.minByOrNull(Task::at) ?: break
                tasks.remove(next)
                now = next.at
                next.runnable.run()
            }
            now = end
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                run((tasks.minOf(Task::at) - now).coerceAtLeast(0L))
            }
        }
    }
}
