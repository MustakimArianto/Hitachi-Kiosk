package com.hitachi.kiosk.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hitachi.kiosk.benchmark.KioskUi.launchToIdle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TC-142 / TR-PRF-01: cold start to the first frame, ×10, median, on a bound kiosk. Measured without and with the
 * baseline profile, so the profile's effect is visible. Run with
 * `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hitachi.kiosk.benchmark.StartupBenchmark`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutProfile() = coldStart(CompilationMode.None())

    @Test
    fun coldStartWithBaselineProfile() = coldStart(CompilationMode.Partial())

    private fun coldStart(compilationMode: CompilationMode) {
        var bound = false
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = {
                // Sign in and bind once (the binding persists), then start every iteration from a killed process.
                if (!bound) {
                    launchToIdle()
                    killProcess()
                    bound = true
                }
                pressHome()
            },
        ) {
            startActivityAndWait()
        }
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
