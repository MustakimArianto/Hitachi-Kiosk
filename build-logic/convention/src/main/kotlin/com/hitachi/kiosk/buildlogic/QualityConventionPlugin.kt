package com.hitachi.kiosk.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** Name of the per-module task that `./gradlew verify` depends on (technical-design §2.6). */
internal const val MODULE_CHECK_TASK = "kioskCheck"

/**
 * `kiosk.quality`: detekt, ktlint, Kover and dependency analysis for one module, plus the module's
 * [MODULE_CHECK_TASK].
 * Android Lint is configured by the Android conventions.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("detekt"))
            pluginManager.apply(libs.pluginId("ktlint"))
            pluginManager.apply(libs.pluginId("kover"))
            // Reports to the root buildHealth task (technical-design §2.6).
            pluginManager.apply(libs.pluginId("dependency-analysis"))

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                parallel = true
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                source.setFrom(
                    fileTree("src") { include("*/kotlin/**/*.kt", "*/java/**/*.kt") },
                    fileTree(".") { include("*.kts") },
                )
            }
            tasks.withType<Detekt>().configureEach {
                jvmTarget = JVM_TARGET.toString()
                reports {
                    html.required.set(true)
                    xml.required.set(false)
                    sarif.required.set(false)
                    txt.required.set(false)
                    md.required.set(false)
                }
            }
            dependencies {
                "detektPlugins"(libs.lib("compose-rules-detekt"))
            }

            extensions.configure<KtlintExtension> {
                version.set(libs.findVersion("ktlint").get().requiredVersion)
                filter {
                    exclude { it.file.path.contains("${java.io.File.separator}build${java.io.File.separator}") }
                }
            }

            extensions.configure<KoverProjectExtension> {
                reports.filters.excludes {
                    // Generated or wiring-only code: Hilt/Dagger output, BuildConfig and Hilt modules.
                    classes(
                        "*_Factory",
                        "*_Factory\$*",
                        "*_MembersInjector",
                        "*_Provide*Factory*",
                        "Hilt_*",
                        "*_HiltModules*",
                        "*_GeneratedInjector",
                        "*.BuildConfig",
                        "*.di.*Module",
                        "*.di.*Module\$*",
                        "dagger.hilt.internal.*",
                        "hilt_aggregated_deps.*",
                    )
                    annotatedBy("dagger.internal.DaggerGenerated", "javax.annotation.processing.Generated")
                }
            }

            tasks.register(MODULE_CHECK_TASK) {
                group = "verification"
                description = "Static analysis and tests for this module (part of ./gradlew verify)."
                dependsOn("detekt", "ktlintCheck")
            }
        }
    }
}

/** Adds the Android checks of the debug variant to [MODULE_CHECK_TASK]. */
internal fun Project.registerAndroidUnitTestChecks() {
    tasks.named(MODULE_CHECK_TASK) {
        dependsOn("lintDebug", "testDebugUnitTest")
    }
    pluginManager.withPlugin(libs.pluginId("roborazzi")) {
        tasks.named(MODULE_CHECK_TASK) { dependsOn("verifyRoborazziDebug") }
    }
}

/**
 * Enforces a minimum line coverage for this module through Kover (NFR-011, TR-MNT-02) and adds the
 * verification to [MODULE_CHECK_TASK]. Android modules are measured on the debug unit tests.
 */
fun Project.kioskCoverage(minLinePercent: Int) {
    val isAndroid = pluginManager.hasPlugin("com.android.library") || pluginManager.hasPlugin("com.android.application")
    extensions.configure<KoverProjectExtension> {
        reports {
            if (isAndroid) {
                variant("debug") { verify { rule { minBound(minLinePercent) } } }
            } else {
                total { verify { rule { minBound(minLinePercent) } } }
            }
        }
    }
    tasks.named(MODULE_CHECK_TASK) {
        dependsOn(if (isAndroid) "koverVerifyDebug" else "koverVerify")
    }
}
