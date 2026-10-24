// Single-Activity app: navigation, feature packages, Hilt wiring and the debug Developer options (architecture §3).
plugins {
    alias(libs.plugins.kiosk.android.application)
    alias(libs.plugins.kiosk.compose)
    alias(libs.plugins.kiosk.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.hitachi.kiosk"

    defaultConfig {
        applicationId = "com.hitachi.kiosk"
        testInstrumentationRunner = "com.hitachi.kiosk.HiltTestRunner"
    }

    // Instrumented tests run with system animations off, which the app reads as reduce motion (TR-ACC-05): Idle's
    // back-to-back slide animations would otherwise never let Compose go idle.
    testOptions {
        animationsDisabled = true
    }
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
    runtimeOnly(libs.androidx.profileinstaller)

    // Debug-only Developer options (DEV-009): fakes and their switching never ship in release.
    debugImplementation(projects.core.testing)
    debugImplementation(libs.androidx.datastore.preferences)
    debugRuntimeOnly(libs.leakcanary.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
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
    androidTestImplementation(libs.androidx.work.testing)
    kspAndroidTest(libs.hilt.compiler)
}
