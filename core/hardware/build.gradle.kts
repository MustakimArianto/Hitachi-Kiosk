// Device identity, printers and card terminal implementations (architecture §3, §9). Must not depend on :core:data.
plugins {
    alias(libs.plugins.kiosk.android.library)
    alias(libs.plugins.kiosk.hilt)
}

android {
    namespace = "com.hitachi.kiosk.core.hardware"
}

dependencies {
    api(projects.core.domain)

    // SerialCache keeps its own identity.preferences_pb (technical-design §6.2, §9.1).
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
