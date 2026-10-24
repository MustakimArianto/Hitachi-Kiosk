package com.hitachi.kiosk.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hitachi.kiosk.benchmark.KioskUi.idleToItemAndBack
import com.hitachi.kiosk.benchmark.KioskUi.launchToIdle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile (TR-PRF-19): startup to Idle and the J1 browse path to the item and back. Run with
 * `./gradlew :app:generateBaselineProfile`, which writes `app/src/main/generated/baselineProfiles/` (committed).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndBrowse() =
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            // The previous pass ends on Menu; a fresh process starts on Splash again.
            killProcess()
            launchToIdle()
            idleToItemAndBack()
        }
}
