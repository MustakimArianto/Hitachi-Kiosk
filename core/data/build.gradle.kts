import com.hitachi.kiosk.buildlogic.kioskCoverage

// Retrofit API, DTOs and mappers, interceptors and error mapping (architecture §3, §7).
plugins {
    alias(libs.plugins.kiosk.android.library)
    alias(libs.plugins.kiosk.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

android {
    namespace = "com.hitachi.kiosk.core.data"
}

// Room schemas are committed (technical-design §6.1, TR-DAT-04).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.core.domain)
    // DefaultBackendApi and NetworkModule expose Retrofit and OkHttp types (Interceptor, @KioskBackend clients).
    api(libs.retrofit)
    api(libs.okhttp)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    // Device state and the encrypted token store (architecture §6, technical-design §6.2–§6.3, TD-05).
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.tink)
    implementation(libs.tink.android)
    // Catalog cache (DEV-021) and the periodic sync worker (DEV-023).
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.okhttp.mockwebserver3.junit4)
    // In-memory Room and the network callbacks run on Robolectric (technical-design §14).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
}

kioskCoverage(minLinePercent = 70)
