package com.hitachi.kiosk.feature.activation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DEV-013: the `Login` artboard, its `S_Login_*` boards and scenario states, plus larger text (TR-MNT-08). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class SignInScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Login default`() = capture("Login", SignInSamples.Default)

    @Test
    fun `Login busy`() = capture("Login_busy", SignInSamples.Busy)

    @Test
    fun `S_Login_notRegistered`() = capture("S_Login_notRegistered", SignInSamples.NotRegistered)

    @Test
    fun `S_Login_networkTimeout`() = capture("S_Login_networkTimeout", SignInSamples.Network)

    @Test
    fun `Login serial number unreadable`() = capture("Login_snUnreadable", SignInSamples.SerialUnreadable)

    @Test
    fun `Login device disabled`() = capture("Login_disabled", SignInSamples.Disabled)

    @Test
    fun `Login with larger text`() = capture("Login_largeText", SignInSamples.Default, largeText = true)

    @Test
    fun `S_Login_notRegistered with larger text`() =
        capture("S_Login_notRegistered_largeText", SignInSamples.NotRegistered, largeText = true)

    private fun capture(
        name: String,
        state: SignInUiState,
        largeText: Boolean = false,
    ) {
        composeRule.setContent {
            KioskTheme(largeText = largeText, reduceMotion = true) {
                DesignFrame { SignInScreen(state = state, onEvent = {}) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
