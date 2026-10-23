package com.hitachi.kiosk.feature

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.MainActivity
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi.Endpoint
import com.hitachi.kiosk.core.testing.fake.FakeDeviceIdentityProvider
import com.hitachi.kiosk.core.testing.fake.FakeScenario
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.testing.KioskState
import com.hitachi.kiosk.testing.awaitScreen
import com.hitachi.kiosk.testing.awaitText
import com.hitachi.kiosk.testing.hasNodeWithText
import com.hitachi.kiosk.testing.launchKiosk
import com.hitachi.kiosk.testing.string
import com.hitachi.kiosk.testing.tap
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Journeys J5 (first-time setup) and J6 (unregistered device) and the setup edges of PRD §5.1 on the real screens,
 * data layer and encrypted storage, with a fake backend and serial number (DEV-018; TC-001, TC-002, TC-007–TC-009,
 * TC-012, TC-013, TC-015–TC-018).
 */
@HiltAndroidTest
class SetupJourneysUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject
    lateinit var backend: FakeBackendApi

    @Inject
    lateinit var identity: FakeDeviceIdentityProvider

    @Inject
    lateinit var activation: ActivationRepository

    @Inject
    lateinit var merchants: MerchantRepository

    private val state by lazy { KioskState(activation, merchants) }
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hilt.inject()
        state.reset()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch() {
        scenario?.close()
        scenario = launchKiosk()
    }

    @Test
    fun j5_firstTimeSetupBindsAMerchantAndSurvivesARestart() {
        launch()
        compose.awaitScreen("SCR-02")

        compose.tap(SIGN_IN_BUTTON)
        compose.awaitScreen("SCR-03")
        compose.awaitText("Urban Grill")

        compose.tap("merchant:${SampleData.MERCHANT_ID}")
        compose.awaitScreen("SCR-04")
        assertThat(backend.callsTo(Endpoint.E3)).hasSize(1)
        assertThat(backend.callsTo(Endpoint.E6)).hasSize(1)

        // FR-003 AC4 / TC-002: a restart routes a bound kiosk to Idle without Sign in.
        launch()
        compose.awaitScreen("SCR-04")
    }

    @Test
    fun j6_unregisteredDeviceShowsItsSerialNumberAndTryAgainWorks() {
        backend.activate.fail(DomainError.Server(code = "DEVICE_NOT_REGISTERED", httpStatus = 404))
        launch()
        compose.awaitScreen("SCR-02")

        compose.tap(SIGN_IN_BUTTON)
        compose.awaitText(string(R.string.sign_in_error_not_registered_title))
        assertThat(compose.hasNodeWithText(SampleData.SERIAL_NUMBER)).isTrue()
        assertThat(compose.hasNodeWithText(string(R.string.sign_in_button_try_again))).isTrue()

        backend.activate.succeed(SampleData.activation)
        compose.tap(SIGN_IN_BUTTON)
        compose.awaitScreen("SCR-03")
    }

    @Test
    fun anActivatedKioskStartsOnChooseARestaurantAndLogOutReturnsToSignIn() {
        state.activated()
        launch()
        compose.awaitScreen("SCR-03")

        compose.tap(LOG_OUT_BUTTON)
        compose.awaitScreen("SCR-02")
        assertThat(activation.state.value).isEqualTo(DeviceState.NotActivated)

        // TC-018 (a): Log out survives a restart.
        launch()
        compose.awaitScreen("SCR-02")
    }

    @Test
    fun anUnreadableSerialNumberKeepsLogInDisabled() {
        identity.unavailable()
        launch()
        compose.awaitScreen("SCR-02")

        compose.awaitText(string(R.string.sign_in_error_serial_unreadable_title))
        compose.onNodeWithTag(SIGN_IN_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun aSlowSerialNumberKeepsLogInBusyThenEnablesIt() {
        identity.slow(5.seconds)
        launch()
        compose.awaitScreen("SCR-02")
        compose.onNodeWithTag(SIGN_IN_BUTTON).assertIsNotEnabled()

        compose.waitUntil(SLOW_SERIAL_TIMEOUT_MS) {
            runCatching { compose.onNodeWithTag(SIGN_IN_BUTTON).assertIsEnabled() }.isSuccess
        }
    }

    @Test
    fun aDoubleTapOnLogInSendsOneActivation() {
        backend.activate.succeed(SampleData.activation, delay = 2.seconds)
        launch()
        compose.awaitScreen("SCR-02")

        compose.onNodeWithTag(SIGN_IN_BUTTON).performClick().performClick()
        compose.awaitScreen("SCR-03")

        assertThat(backend.callsTo(Endpoint.E1)).hasSize(1)
    }

    @Test
    fun comingSoonRowsIgnoreTapsAndAreDisabled() {
        state.activated()
        launch()
        compose.awaitScreen("SCR-03")

        val bella = compose.onNodeWithTag("soon:mer_bellapasta").performScrollTo()
        bella.assertIsNotEnabled()
        bella.performClick()
        compose.waitForIdle()

        compose.awaitScreen("SCR-03")
        assertThat(backend.callsTo(Endpoint.E3)).isEmpty()
    }

    @Test
    fun aMenuDownloadFailureStaysOnChooseARestaurantAndRetryRecovers() {
        state.activated()
        backend.apply(FakeScenario.MENU_DOWNLOAD_FAILED)
        launch()
        compose.awaitScreen("SCR-03")

        compose.tap("merchant:${SampleData.MERCHANT_ID}")
        compose.awaitText(string(R.string.merchants_failure_menu_download_title))

        backend.menu.succeed(Conditional.Modified(SampleData.menu, "\"menu-118\""))
        compose.tap(RETRY_BUTTON)
        compose.awaitScreen("SCR-03")
        compose.awaitText("Urban Grill")
        compose.tap("merchant:${SampleData.MERCHANT_ID}")
        compose.awaitScreen("SCR-04")
    }

    @Test
    fun anEmptyMerchantListShowsNoRestaurantsYet() {
        state.activated()
        backend.apply(FakeScenario.MERCHANTS_EMPTY)
        launch()

        compose.awaitText(string(R.string.merchants_empty_title))
        assertThat(compose.hasNodeWithText(string(R.string.merchants_empty_body))).isTrue()
    }

    @Test
    fun losingTheActivationOnAnyScreenReturnsToSignIn() {
        state.bound()
        launch()
        compose.awaitScreen("SCR-04")

        // TC-013: what the 401 handler does (SetupIntegrationTest covers the HTTP side); the root reacts.
        runBlocking { merchants.logOut() }

        compose.awaitScreen("SCR-02")
    }

    @Test
    fun aMerchantListFailureShowsRetry() {
        state.activated()
        backend.merchants.fail(DomainError.Network())
        launch()

        compose.awaitText(string(R.string.merchants_failure_load_title), timeoutMillis = LIST_RETRY_TIMEOUT_MS)
        backend.merchants.always(Outcome.Success(SampleData.merchants))
        compose.tap(RETRY_BUTTON)
        compose.awaitText("Urban Grill")
    }

    private companion object {
        const val SIGN_IN_BUTTON = "sign-in-button"
        const val LOG_OUT_BUTTON = "merchants-log-out"
        const val RETRY_BUTTON = "merchants-retry"
        const val SLOW_SERIAL_TIMEOUT_MS = 8_000L

        /** E2 is retried after 1, 2 and 4 s before the failure shows (technical-design §5.3). */
        const val LIST_RETRY_TIMEOUT_MS = 20_000L
    }
}
