package com.hitachi.kiosk.feature.merchant

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DEV-015 / FR-004: what each control sends, and the disabled semantics of closed cards and coming-soon rows. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class MerchantsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<MerchantsEvent>()

    private fun show(state: MerchantsUiState) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame { MerchantsScreen(state = state, onEvent = { events += it }) }
            }
        }
    }

    private val disabled = SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)

    @Test
    fun `a live open card selects its merchant`() {
        show(MerchantsSamples.Default)

        composeRule
            .onNodeWithTag(merchantCardTag(MerchantsSamples.UrbanGrill.id))
            .assertContentDescriptionEquals("Urban Grill, open")
            .assertIsEnabled()
            .performClick()

        assertThat(events).containsExactly(MerchantsEvent.Select(MerchantsSamples.UrbanGrill.id))
    }

    @Test
    fun `every coming-soon row is shown, announced as disabled and sends nothing`() {
        show(MerchantsSamples.Default)

        MerchantsSamples.ComingSoon.forEach { row ->
            composeRule
                .onNodeWithTag(comingSoonRowTag(row.id))
                .performScrollTo()
                .assert(disabled)
                .assertContentDescriptionEquals("${row.name}, ${row.cuisine}, coming soon")
                .performClick()
        }

        assertThat(events).isEmpty()
    }

    @Test
    fun `a closed card is disabled, shows its opening time and sends nothing`() {
        show(MerchantsSamples.Closed)

        composeRule
            .onNodeWithTag(merchantCardTag(MerchantsSamples.UrbanGrill.id))
            .assert(disabled)
            .assertContentDescriptionEquals("Urban Grill, Closed · opens 10:00")
            .performClick()

        assertThat(events).isEmpty()
    }

    @Test
    fun `a closed card without an opening time just says Closed`() {
        show(
            MerchantsUiState(
                MerchantsContent.Loaded(
                    live = listOf(MerchantsSamples.UrbanGrill.copy(isOpen = false)),
                    comingSoon = emptyList(),
                ),
            ),
        )

        composeRule
            .onNodeWithTag(merchantCardTag(MerchantsSamples.UrbanGrill.id))
            .assertContentDescriptionEquals("Urban Grill, Closed")
    }

    @Test
    fun `while binding every card ignores taps`() {
        show(MerchantsSamples.Binding)

        composeRule
            .onNodeWithTag(merchantCardTag(MerchantsSamples.UrbanGrill.id))
            .assertIsNotEnabled()
            .performClick()

        assertThat(events).isEmpty()
    }

    @Test
    fun `the failure panel sends Retry and Log out`() {
        show(MerchantsSamples.BindFailed)

        composeRule.onNodeWithText("Couldn't connect this kiosk", useUnmergedTree = true).assertExists()
        composeRule
            .onNodeWithText(
                "This kiosk could not be linked to Urban Grill. Check the connection and retry.",
                useUnmergedTree = true,
            ).assertExists()
        composeRule.onNodeWithTag(MERCHANTS_RETRY_TAG).performClick()
        composeRule.onNodeWithTag(MERCHANTS_FAILURE_LOG_OUT_TAG).performClick()

        assertThat(events).containsExactly(MerchantsEvent.Retry, MerchantsEvent.LogOut).inOrder()
    }

    @Test
    fun `the menu download failure names the merchant`() {
        show(MerchantsSamples.MenuDownloadFailed)

        composeRule.onNodeWithText("Couldn't download the menu", useUnmergedTree = true).assertExists()
        composeRule
            .onNodeWithText(
                "The menu for Urban Grill did not finish downloading. Check the connection and retry.",
                useUnmergedTree = true,
            ).assertExists()
    }

    @Test
    fun `the load failure has its own copy`() {
        show(MerchantsUiState(MerchantsContent.Failed(MerchantsFailure.LOAD)))

        composeRule.onNodeWithText("Couldn't load restaurants", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Check the connection and retry.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the header Log out sends Log out`() {
        show(MerchantsSamples.Default)

        composeRule.onNodeWithTag(MERCHANTS_LOG_OUT_TAG).performClick()

        assertThat(events).containsExactly(MerchantsEvent.LogOut)
    }

    @Test
    fun `the header Log out ignores taps when disabled`() {
        show(MerchantsSamples.Default.copy(logOutEnabled = false))

        composeRule.onNodeWithTag(MERCHANTS_LOG_OUT_TAG).assertIsNotEnabled().performClick()

        assertThat(events).isEmpty()
    }

    @Test
    fun `the empty state refreshes the list`() {
        show(MerchantsUiState(MerchantsContent.Empty))

        composeRule.onNodeWithText("No restaurants yet").assertExists()
        composeRule.onNodeWithText("Refresh list").performClick()

        assertThat(events).containsExactly(MerchantsEvent.Retry)
    }

    @Test
    fun `the skeleton is announced as loading`() {
        show(MerchantsUiState(MerchantsContent.Loading))

        composeRule.onNodeWithContentDescription("Loading restaurants").assertExists()
        composeRule.onNodeWithTag(MERCHANTS_SCREEN_TAG).assertExists()
    }
}
