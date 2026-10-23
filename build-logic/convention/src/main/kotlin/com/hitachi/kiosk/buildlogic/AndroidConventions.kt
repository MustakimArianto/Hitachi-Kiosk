package com.hitachi.kiosk.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project

/** Settings shared by `:app` and every Android library module (technical-design §2.2). */
internal fun Project.configureAndroidCommon(android: CommonExtension) {
    android.apply {
        compileSdk = KioskSdk.COMPILE
        defaultConfig.minSdk = KioskSdk.MIN
        compileOptions.sourceCompatibility = JavaVersion.toVersion(JVM_TARGET)
        compileOptions.targetCompatibility = JavaVersion.toVersion(JVM_TARGET)
        testOptions.unitTests.isIncludeAndroidResources = true
        // Robolectric 4.17 on the API 37 image reaches into jdk.internal.access, which JDK 17+ hides.
        testOptions.unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
        packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*.md")
        lint.configureKioskLint(this@configureAndroidCommon)
    }
}

/** Android Lint is part of the verify gate (NFR-011, TR-MNT-03): any finding fails the build. */
private fun Lint.configureKioskLint(project: Project) {
    abortOnError = true
    warningsAsErrors = true
    checkReleaseBuilds = false
    targetSdk = KioskSdk.TARGET
    lintConfig = project.rootProject.file("config/lint/lint.xml")
    htmlReport = true
    xmlReport = false
}
