// Macrobenchmarks and the baseline profile generator for :app (DEV-031, NFR-003, technical-design §13).
// Runs on a connected device or emulator against the app's release-like `benchmarkRelease` build (R8, not debuggable,
// signed with the debug key, fakes behind BindingOverrides). See docs/perf/baseline-0.3.0.md.
plugins {
    alias(libs.plugins.kiosk.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.hitachi.kiosk.benchmark"
    targetProjectPath = ":app"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The emulator is fine for a trend; on a real device remove the suppression (testing-plan §1.3).
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    // The benchmark APK instruments the app from its own process (required by Macrobenchmark).
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Generate the profile on the connected device or emulator (`./gradlew :app:generateBaselineProfile`).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
