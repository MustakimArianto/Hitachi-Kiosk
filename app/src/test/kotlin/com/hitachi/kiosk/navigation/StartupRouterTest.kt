package com.hitachi.kiosk.navigation

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.testing.fake.FakeActivationRepository
import com.hitachi.kiosk.core.testing.fake.FakeCatalogSyncRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** FR-001 AC1–AC3 and the DEV-011 cases: the four routes, the 10 s cap and unreadable storage. */
class StartupRouterTest {
    private val activation = FakeActivationRepository()
    private val catalog = FakeCatalogSyncRepository()

    private fun TestScope.router() = StartupRouter(activation, catalog, backgroundScope)

    @Test
    fun `not activated goes to Sign in after the minimum display time`() =
        runTest {
            val destination = router().route()

            assertThat(destination).isEqualTo(StartDestination.SIGN_IN)
            assertThat(currentTime).isEqualTo(StartupRouter.MINIMUM_DISPLAY.inWholeMilliseconds)
            assertThat(catalog.synced).isEmpty()
        }

    @Test
    fun `activated without a merchant goes to Choose a restaurant`() =
        runTest {
            activation.stored = SampleData.activatedState

            assertThat(router().route()).isEqualTo(StartDestination.MERCHANTS)
            assertThat(catalog.synced).isEmpty()
        }

    @Test
    fun `bound goes to Idle and starts a background sync`() =
        runTest {
            activation.stored = SampleData.boundState

            assertThat(router().route()).isEqualTo(StartDestination.IDLE)
            runCurrent()
            assertThat(catalog.synced).containsExactly(SampleData.MERCHANT_ID)
        }

    @Test
    fun `bound and offline still goes to Idle on time because the sync is not awaited`() =
        runTest {
            activation.stored = SampleData.boundState
            // FR-001 AC2: a sync that hangs on the network does not hold the splash.
            catalog.script.succeed(KioskMode.SINGLE, delay = 60.seconds)

            assertThat(router().route()).isEqualTo(StartDestination.IDLE)
            assertThat(currentTime).isEqualTo(StartupRouter.MINIMUM_DISPLAY.inWholeMilliseconds)
        }

    @Test
    fun `a slow load waits for the load, not only the minimum`() =
        runTest {
            activation.stored = SampleData.boundState
            activation.loadDelay = 6.seconds

            assertThat(router().route()).isEqualTo(StartDestination.IDLE)
            assertThat(currentTime).isEqualTo(6_000)
        }

    @Test
    fun `a load slower than 10 s is cut off and routes with what is known`() =
        runTest {
            activation.stored = SampleData.boundState
            activation.loadDelay = 30.seconds

            val routing = async { router().route() }

            assertThat(routing.await()).isEqualTo(StartDestination.SIGN_IN)
            assertThat(currentTime).isEqualTo(StartupRouter.INIT_CAP.inWholeMilliseconds)
        }

    @Test
    fun `unreadable storage reads as not activated and goes to Sign in`() =
        runTest {
            // The data layer wipes corrupt storage and reports NotActivated (DeviceSessionTest); routing follows.
            activation.stored = DeviceState.NotActivated

            assertThat(router().route()).isEqualTo(StartDestination.SIGN_IN)
            assertThat(activation.loadCount).isEqualTo(1)
        }
}
