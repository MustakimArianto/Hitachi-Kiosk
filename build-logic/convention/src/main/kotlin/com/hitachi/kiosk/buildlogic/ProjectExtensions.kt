package com.hitachi.kiosk.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** SDK levels shared by every Android module (NFR-001, TR-PLT-01). */
internal object KioskSdk {
    const val COMPILE = 37
    const val TARGET = 37
    const val MIN = 30
}

/** Bytecode level for every module (technology-stack §2). */
internal const val JVM_TARGET = 17

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalArgumentException("Unknown library alias '$alias' in libs.versions.toml") }

internal fun VersionCatalog.pluginId(alias: String): String =
    findPlugin(alias).orElseThrow { IllegalArgumentException("Unknown plugin alias '$alias' in libs.versions.toml") }
        .get()
        .pluginId
