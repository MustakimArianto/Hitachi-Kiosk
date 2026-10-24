package com.hitachi.kiosk.core.domain.order

import com.hitachi.kiosk.core.domain.model.OrderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the current [OrderSession] for the whole app (architecture §5.1). Feature ViewModels read it and send
 * intents to the session; nothing passes the cart through navigation arguments. `:app` provides it as a singleton.
 */
interface OrderSessionHolder {
    /** The order in progress, or `null` at Idle. */
    val current: StateFlow<OrderSession?>

    /** Whether a customer is ordering, so a catalog sync must wait (FR-028, A-29). */
    val isOrderActive: Boolean get() = current.value != null

    /**
     * Starts an order with [orderType] on the catalog [menuVersion] / [configVersion] (FR-007), or changes the type
     * of the order in progress and keeps its cart ("Change" from the Menu, PRD §5.1).
     */
    fun chooseOrderType(
        orderType: OrderType,
        menuVersion: String,
        configVersion: String,
    ): OrderSession

    /**
     * Back to Idle: the order and its cart are gone (FR-006 AC5, BR-09). Every way back to Idle ends here, whatever
     * phase the order reached (architecture §5.1: Completed, Cancelled and TimedOut all lead to Idle).
     */
    fun endOrder()
}

/** The [OrderSessionHolder] of the app: one order at a time, in memory only (A-11). */
class DefaultOrderSessionHolder : OrderSessionHolder {
    private val session = MutableStateFlow<OrderSession?>(null)

    override val current: StateFlow<OrderSession?> = session.asStateFlow()

    override fun chooseOrderType(
        orderType: OrderType,
        menuVersion: String,
        configVersion: String,
    ): OrderSession {
        while (true) {
            val existing = session.value
            if (existing != null) {
                existing.changeOrderType(orderType)
                return existing
            }
            val started = OrderSession(orderType, menuVersion, configVersion)
            if (session.compareAndSet(null, started)) return started
        }
    }

    override fun endOrder() {
        session.value = null
    }
}
