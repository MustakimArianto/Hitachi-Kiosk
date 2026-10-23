package com.hitachi.kiosk.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register

/**
 * `kiosk.verify` (root project only): the local verification gate `./gradlew verify` (D-31, TD-03,
 * technical-design §2.6) and the API contract checks it runs.
 */
class VerifyConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            require(this == rootProject) { "kiosk.verify is applied to the root project only" }

            val spec = layout.projectDirectory.file("docs/api/api-spec.md")
            val tools = layout.projectDirectory.dir("docs/api/tools")
            val fixtures = layout.projectDirectory.dir("core/testing/src/main/resources/fixtures")

            val checkOpenApi = tasks.register<Exec>("checkOpenApi") {
                group = "verification"
                description = "Regenerates openapi.yaml from api-spec.md, fails on a difference and runs Redocly lint."
                inputs.file(spec)
                inputs.dir(tools)
                inputs.file(layout.projectDirectory.file("docs/api/openapi.yaml"))
                inputs.file(layout.projectDirectory.file("scripts/check-openapi.sh"))
                val stamp = layout.buildDirectory.file("reports/check-openapi/ok.txt")
                outputs.file(stamp)
                workingDir = projectDir
                executable = "bash"
                argumentProviders.add { listOf("scripts/check-openapi.sh", stamp.get().asFile.absolutePath) }
            }

            tasks.register<Exec>("extractApiFixtures") {
                group = "build"
                description = "Writes the api-spec.md JSON examples to :core:testing fixtures."
                inputs.file(spec)
                inputs.dir(tools)
                outputs.dir(fixtures)
                workingDir = projectDir
                commandLine("python3", "docs/api/tools/extract_fixtures.py", "docs/api/api-spec.md", fixtures.asFile.path)
            }

            val checkApiFixtures = tasks.register<Exec>("checkApiFixtures") {
                group = "verification"
                description = "Fails when the :core:testing fixtures differ from the api-spec.md examples."
                inputs.file(spec)
                inputs.dir(tools)
                inputs.dir(fixtures)
                val stamp = layout.buildDirectory.file("reports/check-api-fixtures/ok.txt")
                outputs.file(stamp)
                workingDir = projectDir
                executable = "python3"
                argumentProviders.add {
                    listOf(
                        "docs/api/tools/extract_fixtures.py",
                        "--check",
                        "--stamp",
                        stamp.get().asFile.absolutePath,
                        "docs/api/api-spec.md",
                        fixtures.asFile.path,
                    )
                }
            }

            // Modules with a build file; the ":core" folder project has none.
            val modules = subprojects.filter { it.buildFile.exists() }

            // Aggregated coverage report: ./gradlew koverHtmlReport
            dependencies {
                modules.forEach { "kover"(it) }
            }

            tasks.register("verify") {
                group = "verification"
                description = "The full local gate: lint, detekt, ktlint, unit and screenshot tests, coverage, " +
                    "dependency health, release APK check and API contract checks (D-31)."
                dependsOn(modules.map { "${it.path}:$MODULE_CHECK_TASK" })
                dependsOn("buildHealth", ":app:checkReleaseApk", checkOpenApi, checkApiFixtures)
            }
        }
    }
}
