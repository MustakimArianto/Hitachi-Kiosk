// Scriptable fakes, API fixtures and test rules (NFR-012). Used only by test source sets and the debug build.
plugins {
    alias(libs.plugins.kiosk.jvm.library)
}

dependencies {
    api(projects.core.domain)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)

    testImplementation(libs.truth)
    testImplementation(libs.turbine)
}
