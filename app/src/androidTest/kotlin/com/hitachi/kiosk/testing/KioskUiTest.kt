package com.hitachi.kiosk.testing

import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.hitachi.kiosk.MainActivity
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.navigation.screenTag
import kotlinx.coroutines.runBlocking

/** The splash waits 3.9 s and may wait up to 10 s for initialisation (FR-001), so screen changes allow 12 s. */
const val SCREEN_TIMEOUT_MS = 12_000L

/** Waits until the screen [screenId] is shown. */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.awaitScreen(
    screenId: String,
    timeoutMillis: Long = SCREEN_TIMEOUT_MS,
) {
    waitUntilAtLeastOneExists(hasTestTag(screenTag(screenId)), timeoutMillis)
}

/** Waits until a node with [text] is shown. Cards merge their texts into one node, so this reads the unmerged tree. */
fun ComposeTestRule.awaitText(
    text: String,
    timeoutMillis: Long = SCREEN_TIMEOUT_MS,
) {
    waitUntil(timeoutMillis) { hasNodeWithText(text) }
}

/** Whether a node with [text] exists, in the unmerged tree. */
fun ComposeTestRule.hasNodeWithText(text: String): Boolean =
    onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/** Taps the node tagged [tag]. */
fun ComposeTestRule.tap(tag: String) {
    onNodeWithTag(tag).performClick()
}

/** A string of the app under test. */
fun string(
    @StringRes id: Int,
    vararg args: Any,
): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

/** Starts the app as the launcher does. */
fun launchKiosk(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)

/**
 * Stored-state helpers on the real repositories (the backend behind them is the test's fake). The stored state
 * outlives a test, so every test starts with [reset].
 */
class KioskState(
    private val activation: ActivationRepository,
    private val merchants: MerchantRepository,
) {
    /** Not activated: Splash goes to Sign in. */
    fun reset() =
        runBlocking {
            activation.load()
            merchants.logOut()
        }

    /** Activated, no merchant: Splash goes to Choose a restaurant. */
    fun activated() =
        runBlocking {
            reset()
            activation.activate(SampleData.deviceIdentity)
        }

    /** Bound to Urban Grill: Splash goes to Idle. */
    fun bound() =
        runBlocking {
            activated()
            merchants.bind(SampleData.urbanGrill)
        }
}
