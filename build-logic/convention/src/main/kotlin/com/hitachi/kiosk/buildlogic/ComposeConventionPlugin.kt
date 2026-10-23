package com.hitachi.kiosk.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * `kiosk.compose`: Compose compiler and BOM for an Android module. `-Pkiosk.composeReports=true` writes the
 * compiler reports and metrics to `build/compose-reports` (stability review, technical-design §10.4).
 */
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-compose"))

            extensions.getByType<CommonExtension>().buildFeatures.compose = true

            extensions.getByType<ComposeCompilerGradlePluginExtension>().apply {
                stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose/stability.conf"))
                if (providers.gradleProperty("kiosk.composeReports").orNull == "true") {
                    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
                    metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
                }
            }

            dependencies {
                val bom = platform(libs.lib("androidx-compose-bom"))
                "implementation"(bom)
                "testImplementation"(bom)
                "androidTestImplementation"(bom)
                // The Compose test rule drives Espresso. Its transitive 3.5.0 calls InputManager.getInstance(),
                // which API 37 removed, so tests are constrained to the current Espresso on device and on Robolectric.
                constraints {
                    val espresso = libs.lib("androidx-test-espresso-core")
                    "testImplementation"(espresso)
                    "androidTestImplementation"(espresso)
                }
            }
        }
    }
}
