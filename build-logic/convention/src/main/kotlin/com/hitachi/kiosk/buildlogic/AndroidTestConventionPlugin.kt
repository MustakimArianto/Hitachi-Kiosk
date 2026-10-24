package com.hitachi.kiosk.buildlogic

import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `kiosk.android.test`: an Android test module (`com.android.test`) that instruments another module, such as
 * `:benchmark` (DEV-031, technical-design §13). SDK levels and JVM 17 as everywhere else. Its [MODULE_CHECK_TASK]
 * runs detekt and ktlint and compiles the instrumentation APK; the tests themselves need a device, so `verify` does
 * not run them.
 */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.test")
            pluginManager.apply("kiosk.quality")

            extensions.configure<TestExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = KioskSdk.TARGET
            }

            tasks.named(MODULE_CHECK_TASK) { dependsOn("assemble") }
        }
    }
}
