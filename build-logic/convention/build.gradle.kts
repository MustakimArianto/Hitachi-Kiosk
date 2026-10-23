plugins {
    `kotlin-dsl`
}

group = "com.hitachi.kiosk.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
    compileOnly(libs.detekt.gradle.plugin)
    compileOnly(libs.ktlint.gradle.plugin)
    compileOnly(libs.kover.gradle.plugin)
    compileOnly(libs.roborazzi.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "kiosk.android.application"
            implementationClass = "com.hitachi.kiosk.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "kiosk.android.library"
            implementationClass = "com.hitachi.kiosk.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("jvmLibrary") {
            id = "kiosk.jvm.library"
            implementationClass = "com.hitachi.kiosk.buildlogic.JvmLibraryConventionPlugin"
        }
        register("compose") {
            id = "kiosk.compose"
            implementationClass = "com.hitachi.kiosk.buildlogic.ComposeConventionPlugin"
        }
        register("hilt") {
            id = "kiosk.hilt"
            implementationClass = "com.hitachi.kiosk.buildlogic.HiltConventionPlugin"
        }
        register("quality") {
            id = "kiosk.quality"
            implementationClass = "com.hitachi.kiosk.buildlogic.QualityConventionPlugin"
        }
        register("verify") {
            id = "kiosk.verify"
            implementationClass = "com.hitachi.kiosk.buildlogic.VerifyConventionPlugin"
        }
    }
}
