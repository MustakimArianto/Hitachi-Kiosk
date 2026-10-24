package com.hitachi.kiosk.inactivity

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.navigation.CheckoutRoute
import com.hitachi.kiosk.navigation.CompleteRoute
import com.hitachi.kiosk.navigation.MenuRoute
import com.hitachi.kiosk.navigation.YourOrderRoute
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** FR-023, TC-078: the idle timer never runs on Checkout or Complete, and does on the ordering screens. */
@RunWith(RobolectricTestRunner::class)
class InactivityZonesTest {
    private fun zoneOf(route: Any): InactivityZone {
        val controller = NavHostController(ApplicationProvider.getApplicationContext())
        controller.navigatorProvider.addNavigator(ComposeNavigator())
        controller.graph =
            controller.createGraph(startDestination = route) {
                composable<CheckoutRoute> {}
                composable<CompleteRoute> {}
                composable<YourOrderRoute> {}
                composable<MenuRoute> {}
            }
        return controller.currentDestination.inactivityZone()
    }

    @Test
    fun `Checkout and Complete are outside the idle timer, Menu and Your order are inside (TC-078)`() {
        assertThat(zoneOf(CheckoutRoute(PaymentMethod.QRIS))).isEqualTo(InactivityZone.OFF)
        assertThat(zoneOf(CompleteRoute)).isEqualTo(InactivityZone.OFF)
        assertThat(zoneOf(YourOrderRoute)).isEqualTo(InactivityZone.ORDERING)
        assertThat(zoneOf(MenuRoute)).isEqualTo(InactivityZone.ORDERING)
    }
}
