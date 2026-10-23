// Linen & Paprika tokens, DesignUnit, typography and motion (PRD §11). Compose only, no domain types.
plugins {
    alias(libs.plugins.kiosk.android.library)
    alias(libs.plugins.kiosk.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.hitachi.kiosk.core.designsystem"
}

// Goldens are committed next to the tests (technical-design §14). Record with recordRoborazziDebug.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugRuntimeOnly(libs.androidx.compose.ui.test.manifest)
}
