import com.hitachi.kiosk.buildlogic.kioskCoverage

// Pure Kotlin/JVM: models, engines, state machines and the ports to every external system (architecture §3).
plugins {
    alias(libs.plugins.kiosk.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.property)
}

kioskCoverage(minLinePercent = 90)

// The golden case tables in docs/ are read by PricingCasesTest and BudgetPlanCasesTest (TC-060, FR-032).
val caseTables =
    rootProject.layout.projectDirectory
        .dir("docs")
        .let { docs -> listOf(docs.file("pricing-cases.md"), docs.file("budget-plan-cases.md")) }

tasks.withType<Test>().configureEach {
    caseTables.forEach { table ->
        inputs.file(table).withPropertyName(table.asFile.name).withPathSensitivity(PathSensitivity.RELATIVE)
    }
    systemProperty(
        "kiosk.docsDir",
        rootProject.layout.projectDirectory
            .dir("docs")
            .asFile.absolutePath,
    )
}
