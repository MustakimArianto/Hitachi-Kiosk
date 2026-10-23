package com.hitachi.kiosk.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `kiosk.android.library`: SDK levels, JVM 17, consumer rules, no BuildConfig unless a module asks for it. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("kiosk.quality")

            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                testOptions.targetSdk = KioskSdk.TARGET
                if (file("consumer-rules.pro").exists()) {
                    defaultConfig.consumerProguardFiles("consumer-rules.pro")
                }
            }

            registerAndroidUnitTestChecks()
        }
    }
}
