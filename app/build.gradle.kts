// Single-Activity app: navigation, feature packages, Hilt wiring and the debug Developer options (architecture §3).
plugins {
    alias(libs.plugins.kiosk.android.application)
    alias(libs.plugins.kiosk.compose)
    alias(libs.plugins.kiosk.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.hitachi.kiosk"

    defaultConfig {
        applicationId = "com.hitachi.kiosk"
        testInstrumentationRunner = "com.hitachi.kiosk.HiltTestRunner"
    }

    // Release-like build types for :benchmark (DEV-031, technical-design §13). The androidx.baselineprofile plugin
    // would create them from `release` at finalizeDsl; declaring them here makes their source sets and
    // dependencies configurable below, and the plugin then only applies its overrides (not debuggable, profileable).
    // Both are signed with the debug key and use the fakes through BindingOverrides, because the release build has
    // no fakes and the backend does not exist yet. `release` itself is unchanged (scripts/check-release-apk.sh).
    buildTypes {
        val release = getByName("release")
        // What Macrobenchmark measures: R8 and resource shrinking as in release.
        create("benchmarkRelease") {
            initWith(release)
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            proguardFiles("benchmark-rules.pro")
        }
        // What the baseline profile generator runs: release without R8, so the profile names the real classes.
        create("nonMinifiedRelease") {
            initWith(release)
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    sourceSets {
        // src/benchmarkTarget: BindingOverrides with the :core:testing fakes, so the app reaches Idle offline.
        // src/frameDiagnostics: JankStats frame logging, in debug and in the measured benchmark build only (TC-143).
        named("benchmarkRelease") {
            kotlin.directories += listOf("src/benchmarkTarget/kotlin", "src/frameDiagnostics/kotlin")
        }
        named("nonMinifiedRelease") { kotlin.directories += "src/benchmarkTarget/kotlin" }
        named("debug") { kotlin.directories += "src/frameDiagnostics/kotlin" }
    }
}

// The profile is generated on demand (`./gradlew :app:generateBaselineProfile` with a device connected) and
// committed under src/main/generated/baselineProfiles, so every release build ships it (TR-PRF-19).
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.hardware)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    // Menu, promo and merchant photos with a 200 MB disk cache (NFR-013, technical-design §4.1).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    // WorkManager with Hilt workers for the periodic catalog sync (DEV-023).
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    // Installs the baseline profile on sideloaded builds (TR-PRF-19); the profile comes from :benchmark.
    runtimeOnly(libs.androidx.profileinstaller)
    baselineProfile(projects.benchmark)

    // Debug-only Developer options (DEV-009): fakes and their switching never ship in release.
    debugImplementation(projects.core.testing)
    debugImplementation(libs.androidx.datastore.preferences)
    debugRuntimeOnly(libs.leakcanary.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // JankStats frame logging (src/frameDiagnostics): debug and the benchmark build only, never release (TC-143).
    debugImplementation(libs.androidx.metrics.performance)
    "benchmarkReleaseImplementation"(libs.androidx.metrics.performance)
    // The benchmark builds reach Idle with the fakes (src/benchmarkTarget, DEV-031).
    "benchmarkReleaseImplementation"(projects.core.testing)
    "nonMinifiedReleaseImplementation"(projects.core.testing)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugRuntimeOnly(libs.androidx.compose.ui.test.manifest)
    testDebugImplementation(libs.kotlinx.coroutines.test)
    testDebugImplementation(libs.okhttp.mockwebserver3)
    testDebugImplementation(libs.okhttp.mockwebserver3.junit4)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
