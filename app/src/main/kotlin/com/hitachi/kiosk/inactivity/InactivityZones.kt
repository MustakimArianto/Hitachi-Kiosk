package com.hitachi.kiosk.inactivity

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.hitachi.kiosk.navigation.BudgetPlanRoute
import com.hitachi.kiosk.navigation.ComboRoute
import com.hitachi.kiosk.navigation.ItemDetailRoute
import com.hitachi.kiosk.navigation.MenuRoute
import com.hitachi.kiosk.navigation.OrderTypeRoute
import com.hitachi.kiosk.navigation.YourOrderRoute

/**
 * The [InactivityZone] of a destination (FR-023: SCR-05…SCR-10 and SCR-19, architecture §11). Everything not listed
 * (Splash, Sign in, Merchants, Idle, Checkout, Complete, staff settings) is [InactivityZone.OFF].
 */
fun NavDestination?.inactivityZone(): InactivityZone =
    when {
        this == null -> InactivityZone.OFF

        hasRoute<OrderTypeRoute>() -> InactivityZone.ORDER_TYPE

        hasRoute<MenuRoute>() ||
            hasRoute<ComboRoute>() ||
            hasRoute<ItemDetailRoute>() ||
            hasRoute<YourOrderRoute>() ||
            hasRoute<BudgetPlanRoute>() -> InactivityZone.ORDERING

        else -> InactivityZone.OFF
    }
