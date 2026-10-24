package com.hitachi.kiosk.debug

import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.devtools.FrameDiagnostics
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToLong

/**
 * Logs janky frames with JankStats (TR-PRF-05, TC-143): each janky frame at DEBUG (`frame_jank`), and the frame and
 * jank counts at INFO (`frame_summary`) whenever the Activity pauses. Only frame timings are logged, never screen
 * content or customer data (D-23). Built into the debug and `benchmarkRelease` builds only (technical-design §13).
 */
class JankStatsFrameDiagnostics : FrameDiagnostics {
    override fun track(activity: ComponentActivity) {
        val counts = FrameCounts()
        val jankStats = JankStats.createAndTrack(activity.window, counts::onFrame)
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    jankStats.isTrackingEnabled = true
                }

                override fun onPause(owner: LifecycleOwner) {
                    jankStats.isTrackingEnabled = false
                    counts.logAndReset()
                }
            },
        )
    }

    /** Frame counts since the last summary. JankStats reports on its own thread, so the counters are atomic. */
    private class FrameCounts {
        private val frames = AtomicInteger()
        private val janky = AtomicInteger()

        fun onFrame(frame: FrameData) {
            frames.incrementAndGet()
            if (!frame.isJank) return
            janky.incrementAndGet()
            KioskLog.d(
                "frame_jank",
                "duration_ms" to frame.frameDurationUiNanos.nanosToMillis(),
                "states" to frame.states.joinToString(",") { "${it.key}=${it.value}" },
            )
        }

        fun logAndReset() {
            val total = frames.getAndSet(0)
            val jank = janky.getAndSet(0)
            if (total == 0) return
            KioskLog.i(
                "frame_summary",
                "frames" to total,
                "janky" to jank,
                "janky_percent" to (jank * PERCENT * TENTHS / total).roundToLong() / TENTHS.toDouble(),
            )
        }

        private fun Long.nanosToMillis(): Double = (this / NANOS_PER_TENTH_MILLI).toDouble() / TENTHS

        private companion object {
            const val PERCENT = 100.0
            const val TENTHS = 10
            const val NANOS_PER_TENTH_MILLI = 100_000L
        }
    }
}
