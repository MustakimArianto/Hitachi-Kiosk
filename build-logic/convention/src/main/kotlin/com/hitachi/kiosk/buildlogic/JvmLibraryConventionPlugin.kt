package com.hitachi.kiosk.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * `kiosk.jvm.library`: a plain Kotlin/JVM module. `:core:domain` uses it so `android.*` cannot compile there
 * (TR-MNT-01); `:core:testing` uses it because fakes depend on the domain only.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-jvm"))
            pluginManager.apply("kiosk.quality")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.toVersion(JVM_TARGET)
                targetCompatibility = JavaVersion.toVersion(JVM_TARGET)
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions.jvmTarget.set(JvmTarget.fromTarget(JVM_TARGET.toString()))
            }

            tasks.named(MODULE_CHECK_TASK) { dependsOn("test") }
        }
    }
}
