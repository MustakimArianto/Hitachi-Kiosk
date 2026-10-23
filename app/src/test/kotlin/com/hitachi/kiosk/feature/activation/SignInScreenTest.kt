package com.hitachi.kiosk.feature.activation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-013 / FR-002 / FR-003: the button sends Log in only when enabled, and each state shows its copy. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class SignInScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<SignInEvent>()

    private fun show(state: SignInUiState) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) { DesignFrame { SignInScreen(state = state, onEvent = { events += it }) } }
        }
    }

    @Test
    fun `Log in sends the event when enabled`() {
        show(SignInSamples.Default)

        composeRule.onNodeWithTag(SIGN_IN_BUTTON_TAG).assertIsEnabled().performClick()

        assertThat(events).containsExactly(SignInEvent.LogIn)
        composeRule.onNodeWithText("Log in").assertExists()
        composeRule.onNodeWithText("Need help? Contact your Hitachi Kiosk administrator.").assertExists()
    }

    @Test
    fun `a busy button is disabled, says Signing in and ignores taps`() {
        show(SignInSamples.Busy)

        composeRule
            .onNodeWithTag(SIGN_IN_BUTTON_TAG)
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
            .performClick()

        assertThat(events).isEmpty()
        composeRule.onNodeWithText("Signing in…").assertExists()
    }

    @Test
    fun `serial unreadable keeps Log in disabled and shows the restart copy`() {
        show(SignInSamples.SerialUnreadable)

        composeRule.onNodeWithTag(SIGN_IN_BUTTON_TAG).assertIsNotEnabled().performClick()

        assertThat(events).isEmpty()
        composeRule.onNodeWithText("Can't read this device's serial number").assertExists()
        composeRule
            .onNodeWithText("Restart the kiosk. If it keeps happening, contact your administrator.")
            .assertExists()
    }

    @Test
    fun `not registered shows the serial number and Try again`() {
        show(SignInSamples.NotRegistered)

        composeRule.onNodeWithText("This kiosk isn't registered yet").assertExists()
        composeRule.onNodeWithText(SignInSamples.SERIAL_NUMBER).assertExists()
        composeRule.onNodeWithText("Still stuck? Contact your Hitachi Kiosk administrator.").assertExists()
        composeRule.onNodeWithTag(SIGN_IN_BUTTON_TAG).performClick()
        assertThat(events).containsExactly(SignInEvent.LogIn)
    }

    @Test
    fun `the error panel is a live region`() {
        show(SignInSamples.Network)

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .assertExists()
        composeRule.onNodeWithText("Can't reach Hitachi Kiosk", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a disabled device shows its copy`() {
        show(SignInSamples.Disabled)

        composeRule.onNodeWithText("This kiosk has been disabled").assertExists()
        composeRule.onNodeWithText("Contact your administrator.").assertExists()
    }

    @Test
    fun `any other failure shows the generic copy`() {
        show(SignInSamples.Generic)

        composeRule.onNodeWithText("Couldn't sign in").assertExists()
        composeRule
            .onNodeWithText("Something went wrong. Try again. If it keeps happening, contact your administrator.")
            .assertExists()
    }

    @Test
    fun `the header shows the real version`() {
        show(SignInSamples.Default)

        composeRule.onNodeWithText("Version 0.2.0").assertExists()
        composeRule.onNodeWithTag(SIGN_IN_SCREEN_TAG).assertExists()
    }
}
