package com.hitachi.kiosk.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hitachi.kiosk.benchmark.KioskUi.idleToItemAndBack
import com.hitachi.kiosk.benchmark.KioskUi.launchToIdle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TC-143 / TR-PRF-05 baseline: frame timing of the J1 browse path, Idle → Order type "Dine in" → Menu → "Burgers" →
 * "Smoky Double Burger" → back to Menu, with the baseline profile. Each iteration starts from a fresh process on Idle,
 * outside the measured block. The `benchmarkRelease` build also logs JankStats summaries (`frame_summary`).
 */
@RunWith(AndroidJUnit4::class)
class JourneyBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun idleToItem() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = ITERATIONS,
            setupBlock = {
                killProcess()
                launchToIdle()
            },
        ) {
            idleToItemAndBack()
        }

    private companion object {
        const val ITERATIONS = 5
    }
}
