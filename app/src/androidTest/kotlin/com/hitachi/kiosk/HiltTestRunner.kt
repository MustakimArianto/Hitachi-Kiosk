package com.hitachi.kiosk

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Runs instrumented tests on [HiltTestApplication] so `@TestInstallIn` modules can swap in fakes (§14). That
 * application is not `KioskApp`, so it is not WorkManager's configuration provider: a test WorkManager is installed,
 * so the system's job service never meets an uninitialised WorkManager (for example a catalog sync scheduled by an
 * earlier install of the debug app).
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(classLoader, HiltTestApplication::class.java.name, context)

    override fun callApplicationOnCreate(app: Application) {
        super.callApplicationOnCreate(app)
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }
}
