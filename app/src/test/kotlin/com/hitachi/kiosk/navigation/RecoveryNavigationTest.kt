package com.hitachi.kiosk.navigation

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The edge `KioskRoot` takes for an order that a process-death recovery found paid (FR-016 AC4, DEV-052): from Idle,
 * which is outside the customer graph, straight to Complete inside it, with Idle kept below so "Done" returns to it.
 */
@RunWith(RobolectricTestRunner::class)
class RecoveryNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    private fun setUpGraph() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = IdleRoute) {
                composable<IdleRoute> { BasicText("idle") }
                navigation<CustomerGraph>(startDestination = OrderTypeRoute) {
                    composable<OrderTypeRoute> { BasicText("order type") }
                    composable<CompleteRoute> { BasicText("complete") }
                }
            }
        }
    }

    @Test
    fun `Complete opens from Idle and Done pops back to Idle`() {
        setUpGraph()

        composeRule.runOnUiThread { navController.navigate(CompleteRoute) { popUpTo(IdleRoute) } }
        composeRule.waitForIdle()

        assertThat(navController.currentDestination?.hasRoute<CompleteRoute>()).isTrue()
        assertThat(navController.previousBackStackEntry?.destination?.hasRoute<IdleRoute>()).isTrue()

        composeRule.runOnUiThread { navController.popBackStack(IdleRoute, false) }
        composeRule.waitForIdle()

        assertThat(navController.currentDestination?.hasRoute<IdleRoute>()).isTrue()
    }
}
