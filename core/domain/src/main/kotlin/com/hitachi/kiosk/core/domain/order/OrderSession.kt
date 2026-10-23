package com.hitachi.kiosk.core.domain.order

import com.hitachi.kiosk.core.domain.cart.Cart
import com.hitachi.kiosk.core.domain.cart.CartAddResult
import com.hitachi.kiosk.core.domain.cart.CartItem
import com.hitachi.kiosk.core.domain.cart.LineChoice
import com.hitachi.kiosk.core.domain.model.OrderType
import com.hitachi.kiosk.core.domain.model.Rupiah
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where an order is in architecture §5.1. 0.3.0 has the states up to building the order; Reviewing and the payment
 * states arrive with the full state machine in 0.4.0 (DEV-041). Idle is the absence of a session
 * ([OrderSessionHolder.current] is `null`).
 */
enum class OrderPhase {
    /** Dine in or Takeaway was picked (FR-007). */
    TYPE_CHOSEN,

    /** The customer reached the Menu (FR-009). */
    BUILDING,
}

/**
 * One order's state (architecture §5.1). [menuVersion] and [configVersion] are the catalog the order started with:
 * a sync that lands mid-order waits until the order ends (FR-028, A-29).
 */
data class OrderState(
    val orderType: OrderType,
    val phase: OrderPhase,
    val cart: Cart,
    val menuVersion: String,
    val configVersion: String,
)

/**
 * One customer order, shared by Order type, Menu, Item and Combo through [OrderSessionHolder] (architecture §5.1).
 * State changes are atomic, so a double tap can never add twice from a stale cart.
 */
class OrderSession(
    orderType: OrderType,
    menuVersion: String,
    configVersion: String,
) {
    private val mutableState =
        MutableStateFlow(
            OrderState(
                orderType = orderType,
                phase = OrderPhase.TYPE_CHOSEN,
                cart = Cart(),
                menuVersion = menuVersion,
                configVersion = configVersion,
            ),
        )

    val state: StateFlow<OrderState> = mutableState.asStateFlow()

    /** "Change" on the Menu: a new order type, the cart kept (FR-007, PRD §5.1). */
    fun changeOrderType(orderType: OrderType) {
        mutableState.update { it.copy(orderType = orderType) }
    }

    /** The first Menu screen of the order (architecture §5.1: TypeChosen → Building). */
    fun startBuilding() {
        mutableState.update { it.copy(phase = OrderPhase.BUILDING) }
    }

    /** Adds to the cart with the merge and limit rules of [Cart.add] (FR-009 AC2, FR-010, BR-05). */
    fun add(
        item: CartItem,
        quantity: Int,
        unitPrice: Rupiah,
        choices: List<LineChoice>,
    ): CartAddResult {
        while (true) {
            val current = mutableState.value
            val result = current.cart.add(item, quantity, unitPrice, choices)
            if (result !is CartAddResult.Added) return result
            if (mutableState.compareAndSet(current, current.copy(cart = result.cart))) return result
        }
    }
}
