package com.hitachi.kiosk.navigation

import kotlinx.serialization.Serializable

// Type-safe routes, one per screen of PRD §4 (architecture §11). SCR-13 "Still there?" is an overlay above the
// NavHost, not a route. Only IDs travel as arguments; the cart is shared through the OrderSessionHolder.

/** SCR-01. Always the start destination. */
@Serializable data object SplashRoute

/** SCR-02. */
@Serializable data object SignInRoute

/** SCR-03. */
@Serializable data object MerchantsRoute

/** SCR-04. The root of every customer session. */
@Serializable data object IdleRoute

/** Nested graph of one customer order (SCR-05…SCR-12, SCR-19). Leaving it pops back to Idle. */
@Serializable data object CustomerGraph

/** SCR-05. */
@Serializable data object OrderTypeRoute

/** SCR-06. */
@Serializable data object MenuRoute

/** SCR-07. [comboId] preselects a combo; `null` starts on the first one (FR-011). */
@Serializable data class ComboRoute(
    val comboId: String? = null,
)

/** SCR-08 for one product (FR-010). */
@Serializable data class ItemDetailRoute(
    val productId: String,
)

/** SCR-09. */
@Serializable data object UpsellRoute

/** SCR-10. */
@Serializable data object YourOrderRoute

/** SCR-11. */
@Serializable data object CheckoutRoute

/** SCR-12. */
@Serializable data object CompleteRoute

/** SCR-19. One route with an internal step state (step 1 "Your table", step 2 "Your plan"). */
@Serializable data object BudgetPlanRoute

/** Nested staff graph, entered only through the PIN gate (SCR-14…SCR-18, SCR-20). */
@Serializable data object StaffGraph

/** SCR-14, PIN gate. */
@Serializable data object StaffPinRoute

/** SCR-14, Settings home. */
@Serializable data object SettingsHomeRoute

/** SCR-15. */
@Serializable data object PrinterSettingsRoute

/** SCR-16. */
@Serializable data object PaymentSettingsRoute

/** SCR-17. */
@Serializable data object KioskSettingsRoute

/** SCR-18. */
@Serializable data object DeviceSettingsRoute

/** SCR-20. */
@Serializable data object ChangePinRoute
