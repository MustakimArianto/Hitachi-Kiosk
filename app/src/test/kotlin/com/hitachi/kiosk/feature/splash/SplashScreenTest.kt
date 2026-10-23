package com.hitachi.kiosk.feature.splash

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-010 / DEV-009: the brand mark is described and reports every tap; Splash never navigates by itself. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class SplashScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `each tap on the brand mark is reported`() {
        var taps = 0
        composeRule.setContent {
            KioskTheme(reduceMotion = true) { DesignFrame { SplashScreen(onMarkClick = { taps++ }) } }
        }

        repeat(5) { composeRule.onNodeWithTag("brand-mark").performClick() }

        assertThat(taps).isEqualTo(5)
        composeRule.onNodeWithTag("screen:SCR-01").assertExists()
        composeRule.onNodeWithTag("brand-mark").assertContentDescriptionEquals("Hitachi Kiosk logo")
    }
}
