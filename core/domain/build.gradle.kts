import com.hitachi.kiosk.buildlogic.kioskCoverage

// Pure Kotlin/JVM: models, engines, state machines and the ports to every external system (architecture §3).
plugins {
    alias(libs.plugins.kiosk.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

kioskCoverage(minLinePercent = 90)
