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
}

// Until DEV-012 adds the first implementation and its tests, the generated Hilt test sources contain no tests.
tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests.set(false)
}
