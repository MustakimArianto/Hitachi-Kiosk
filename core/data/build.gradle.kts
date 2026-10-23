import com.hitachi.kiosk.buildlogic.kioskCoverage

// Retrofit API, DTOs and mappers, interceptors and error mapping (architecture §3, §7).
plugins {
    alias(libs.plugins.kiosk.android.library)
    alias(libs.plugins.kiosk.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hitachi.kiosk.core.data"
}

dependencies {
    api(projects.core.domain)
    // DefaultBackendApi and NetworkModule expose Retrofit and OkHttp types (Interceptor, @KioskBackend clients).
    api(libs.retrofit)
    api(libs.okhttp)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.okhttp.mockwebserver3.junit4)
}

kioskCoverage(minLinePercent = 70)
