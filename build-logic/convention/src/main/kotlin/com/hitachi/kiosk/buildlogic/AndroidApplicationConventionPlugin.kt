package com.hitachi.kiosk.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import java.util.Properties

/**
 * `kiosk.android.application`: SDK levels, versioning (§2.4), backend environments (§2.3, TD-02), the release
 * build (§2.5) and the `checkReleaseApk` task (TR-SEC-07). Section numbers refer to technical-design.md.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("kiosk.quality")

            val version = KioskVersion.from(this)
            val envOverride = providers.gradleProperty("kiosk.env").orNull?.let(KioskEnv::parse)
            val debugEnv = envOverride ?: KioskEnv.DEV
            val releaseEnv = envOverride ?: KioskEnv.PROD

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig {
                    targetSdk = KioskSdk.TARGET
                    versionCode = version.code
                    versionName = version.name
                }
                buildFeatures.buildConfig = true

                val releaseSigning = releaseSigningOrNull()
                buildTypes {
                    getByName("debug") {
                        environmentFields(debugEnv).forEach { (name, value) -> buildConfigField("String", name, value) }
                        buildConfigField("String", "MOCK_BASE_URL", quoted(requiredProperty("kiosk.baseUrl.mock")))
                    }
                    getByName("release") {
                        environmentFields(releaseEnv).forEach { (name, value) -> buildConfigField("String", name, value) }
                        isMinifyEnabled = true
                        isShrinkResources = true
                        isDebuggable = false
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                        if (releaseSigning != null) {
                            signingConfig = signingConfigs.create("release") {
                                storeFile = file(releaseSigning.storeFile)
                                storePassword = releaseSigning.storePassword
                                keyAlias = releaseSigning.keyAlias
                                keyPassword = releaseSigning.keyPassword
                                enableV1Signing = false
                                enableV2Signing = true
                                enableV3Signing = true
                            }
                        }
                    }
                }
            }

            registerAndroidUnitTestChecks()
            registerReleaseChecks(releaseEnv)
        }
    }

    private fun Project.environmentFields(env: KioskEnv): Map<String, String> {
        val pins = providers.gradleProperty("kiosk.pins.${env.id}").orNull
            ?: providers.environmentVariable("KIOSK_PINS_${env.id.uppercase()}").orNull
            ?: ""
        return mapOf(
            "KIOSK_ENV" to quoted(env.id),
            "API_BASE_URL" to quoted(requiredProperty("kiosk.baseUrl.${env.id}")),
            "CERT_PINS" to quoted(pins),
        )
    }

    private fun Project.registerReleaseChecks(releaseEnv: KioskEnv) {
        // A release build must never point at the dev backend (technical-design §2.3).
        val checkReleaseEnvironment = tasks.register("checkReleaseEnvironment") {
            group = "verification"
            description = "Fails when a release build is configured with kiosk.env=dev."
            val env = releaseEnv
            doLast {
                if (env == KioskEnv.DEV) {
                    throw GradleException("Release builds cannot use kiosk.env=dev. Use staging or prod.")
                }
            }
        }
        tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseEnvironment) }

        val androidComponents = extensions.getByType<ApplicationAndroidComponentsExtension>()
        val sdkDirectory = androidComponents.sdkComponents.sdkDirectory
        val buildToolsVersion = extensions.getByType<ApplicationExtension>().buildToolsVersion
        androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
            val apkDirectory = variant.artifacts.get(SingleArtifact.APK)
            val mappingFile = variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
            tasks.register<Exec>("checkReleaseApk") {
                group = "verification"
                description = "Checks the release APK for debug code, debuggable flags and exported components."
                inputs.files(apkDirectory)
                inputs.files(mappingFile)
                val script = rootProject.layout.projectDirectory.file("scripts/check-release-apk.sh")
                inputs.file(script)
                val stamp = layout.buildDirectory.file("reports/check-release-apk/ok.txt")
                outputs.file(stamp)
                executable = "bash"
                argumentProviders.add {
                    listOf(
                        script.asFile.absolutePath,
                        apkDirectory.get().asFile.absolutePath,
                        mappingFile.get().asFile.absolutePath,
                        sdkDirectory.get().asFile.absolutePath,
                        buildToolsVersion,
                        stamp.get().asFile.absolutePath,
                    )
                }
            }
        }
    }

    private fun Project.releaseSigningOrNull(): SigningValues? {
        val local = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) file.inputStream().use(::load)
        }

        fun value(env: String, localKey: String): String? =
            providers.environmentVariable(env).orNull ?: local.getProperty(localKey)

        val storeFile = value("KIOSK_KEYSTORE_PATH", "kiosk.keystore.path") ?: return null
        return SigningValues(
            storeFile = storeFile,
            storePassword = value("KIOSK_KEYSTORE_PASSWORD", "kiosk.keystore.password").orEmpty(),
            keyAlias = value("KIOSK_KEY_ALIAS", "kiosk.key.alias").orEmpty(),
            keyPassword = value("KIOSK_KEY_PASSWORD", "kiosk.key.password").orEmpty(),
        )
    }

    private data class SigningValues(
        val storeFile: String,
        val storePassword: String,
        val keyAlias: String,
        val keyPassword: String,
    )
}

/** Backend environment selected by `-Pkiosk.env` (technical-design §2.3). */
internal enum class KioskEnv(val id: String) {
    DEV("dev"),
    STAGING("staging"),
    PROD("prod"),
    ;

    companion object {
        fun parse(value: String): KioskEnv =
            entries.firstOrNull { it.id == value }
                ?: throw GradleException("kiosk.env must be one of ${entries.joinToString { it.id }}, was '$value'")
    }
}

/** `versionName = M.m.p`, `versionCode = M*10000 + m*100 + p` (NFR-014, conventions §10). */
internal data class KioskVersion(val major: Int, val minor: Int, val patch: Int) {
    val name: String get() = "$major.$minor.$patch"
    val code: Int get() = major * 10_000 + minor * 100 + patch

    companion object {
        fun from(project: Project): KioskVersion =
            KioskVersion(
                major = project.requiredProperty("kiosk.version.major").toInt(),
                minor = project.requiredProperty("kiosk.version.minor").toInt(),
                patch = project.requiredProperty("kiosk.version.patch").toInt(),
            )
    }
}

private fun Project.requiredProperty(name: String): String =
    providers.gradleProperty(name).orNull ?: throw GradleException("Missing Gradle property '$name' in gradle.properties")

private fun quoted(value: String): String = "\"$value\""
