package com.hitachi.kiosk.devtools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hitachi.kiosk.core.domain.model.PaymentMethod

/**
 * The debug-only tool drawn on Checkout: the design's "Prototype only · simulate the result" bar (FR-019, PRD §3 C6).
 * Only the `debug` source set binds it; release builds get an empty `Optional`, so Checkout draws nothing there and
 * the release APK holds neither the bar nor its text (FR-019 AC3, TC-087).
 */
interface CheckoutDebugTools {
    /**
     * The bar for a payment made with [method]. It forces the result against the fake backend and the fake terminal,
     * and draws nothing when they are not the ones in use.
     */
    @Composable
    fun SimulateBar(
        method: PaymentMethod,
        modifier: Modifier,
    )
}
