package com.hitachi.kiosk.inactivity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SCR-13 interactions (FR-023 AC2, DEV-040): tap anywhere or "Continue ordering" continues, "Start over" starts over. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class StillThereOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private var state: StillThereState by mutableStateOf(StillThereState.Showing(15))

    private fun show() {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame {
                    StillThereHost(
                        state = state,
                        onContinue = { calls += "continue" },
                        onStartOver = { calls += "startOver" },
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(KioskTheme.colors.bg)
                                .testTag(SCREEN_TAG)
                                .clickable { calls += "screen" },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the overlay reads the design copy with the seconds left`() {
        state = StillThereState.Showing(12)
        show()

        composeRule.onNodeWithText("Still there?").assertExists()
        composeRule
            .onNodeWithText("Your order will be cancelled in 12 s. Tap anywhere to keep ordering.")
            .assertExists()
        composeRule
            .onNodeWithTag(
                STILL_THERE_RING_TAG,
                useUnmergedTree = true,
            ).assertContentDescriptionEquals("12 seconds left")
    }

    @Test
    fun `a tap anywhere on the overlay continues and never reaches the screen below`() {
        show()

        composeRule.onNodeWithTag(STILL_THERE_TAG).performTouchInput { click(Offset(40f, 40f)) }
        composeRule.onNodeWithTag(STILL_THERE_TAG).performTouchInput { click(Offset(width / 2f, height - 40f)) }

        assertThat(calls).containsExactly("continue", "continue")
    }

    @Test
    fun `a tap on the card text continues`() {
        show()

        composeRule.onNodeWithText("Still there?").performClick()

        assertThat(calls).containsExactly("continue")
    }

    @Test
    fun `Continue ordering continues`() {
        show()

        composeRule.onNodeWithText("Continue ordering").performClick()

        assertThat(calls).containsExactly("continue")
    }

    @Test
    fun `Start over starts over`() {
        show()

        composeRule.onNodeWithText("Start over").performClick()

        assertThat(calls).containsExactly("startOver")
    }

    @Test
    fun `hidden draws only the screen, which takes taps again`() {
        state = StillThereState.Hidden
        show()

        composeRule.onNodeWithTag(STILL_THERE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SCREEN_TAG).performClick()

        assertThat(calls).containsExactly("screen")
    }

    private companion object {
        const val SCREEN_TAG = "screen-below"
    }
}
