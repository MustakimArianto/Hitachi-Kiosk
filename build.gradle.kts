plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.versions)
    alias(libs.plugins.kiosk.verify)
}

// Dependency health is part of ./gradlew verify (TR-MNT-01): any advice fails the build.
dependencyAnalysis {
    structure {
        // Artifact families used through one alias; the plugin must not ask for their parts one by one.
        bundle("hilt") {
            includeGroup("com.google.dagger")
            includeDependency("javax.inject:javax.inject")
            // Hilt's generated entry points reference these AndroidX classes.
            include("androidx\\.fragment:.*")
            include("androidx\\.activity:.*")
            include("androidx\\.lifecycle:.*")
        }
        bundle("compose") {
            include("androidx\\.compose\\..*")
            include("androidx\\.annotation:.*")
        }
        bundle("navigation") { include("androidx\\.navigation:.*") }
        bundle("datastore") { include("androidx\\.datastore:.*") }
        bundle("room") {
            include("androidx\\.room:.*")
            include("androidx\\.sqlite:.*")
        }
        bundle("work") { include("androidx\\.work:.*") }
        bundle("hilt-work") { include("androidx\\.hilt:hilt-(work|common)") }
        bundle("coil") { include("io\\.coil-kt\\.coil3:.*") }
        bundle("kotlinx-serialization") { include("org\\.jetbrains\\.kotlinx:kotlinx-serialization-.*") }
        bundle("kotlinx-coroutines") { include("org\\.jetbrains\\.kotlinx:kotlinx-coroutines-.*") }
        bundle("okhttp") {
            includeDependency("com.squareup.okhttp3:okhttp")
            includeDependency("com.squareup.okio:okio")
        }
        // benchmark-macro-junit4 brings the Macrobenchmark API (benchmark-macro) that :benchmark calls.
        bundle("benchmark") { include("androidx\\.benchmark:.*") }
        bundle("roborazzi") { include("io\\.github\\.takahirom\\.roborazzi:.*") }
        bundle("robolectric") { include("org\\.robolectric:.*") }
        bundle("androidx-test") {
            include("androidx\\.test(\\..*)?:.*")
            includeDependency("junit:junit")
        }
        bundle("truth") {
            includeDependency("com.google.truth:truth")
            include("com\\.google\\.guava:.*")
        }
    }
    issues {
        all {
            onAny { severity("fail") }
            // kiosk.hilt declares Hilt as implementation in every module that uses it.
            onIncorrectConfiguration { exclude("com.google.dagger:hilt-android") }
        }
        // :app code never uses Retrofit; only the generated Hilt component names it, through the internal
        // :core:data implementations it constructs (architecture §3: Retrofit stays in :core:data).
        project(":app") {
            onUsedTransitiveDependencies { exclude("com.squareup.retrofit2:retrofit") }
        }
    }
}

// ./gradlew dependencyUpdates lists newer stable versions only (technology-stack §9).
tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>().configureEach {
    rejectVersionIf {
        val unstable = Regex("(?i).*[.-](alpha|beta|rc|cr|m|eap|dev|snapshot)[.\\d-]*")
        unstable.matches(candidate.version) && !unstable.matches(currentVersion)
    }
}
