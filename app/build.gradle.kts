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
    // Merchant photos and logos (NFR-013); the OkHttp fetcher is found at runtime through Coil's service loader.
    implementation(libs.coil.compose)
    runtimeOnly(libs.coil.network.okhttp)
    runtimeOnly(libs.androidx.profileinstaller)

    // Debug-only Developer options (DEV-009): fakes and their switching never ship in release.
    debugImplementation(projects.core.testing)
    debugImplementation(libs.androidx.datastore.preferences)
    debugRuntimeOnly(libs.leakcanary.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.okhttp)
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
