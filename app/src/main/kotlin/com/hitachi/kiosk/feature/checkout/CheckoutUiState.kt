package com.hitachi.kiosk.feature.checkout

import androidx.compose.runtime.Immutable
import com.hitachi.kiosk.core.domain.model.PaymentMethod

/**
 * SCR-11 Checkout (FR-016–FR-019; `Checkout.dc.html`, `S_Checkout_*`). The screen draws exactly this; the loops of the
 * card tap, the pulse and the spinner are motion and live in the composables.
 *
 * @property stage what the body shows.
 * @property brandName the merchant name of the header and of the QRIS card.
 * @property kioskName the kiosk's display name next to it, e.g. "Kiosk 3" (FR-017).
 * @property amount the formatted total at the top: the backend order total once the order exists, the kiosk total
 *   before that (FR-017, TR-INT-04). Blank only when the cart is gone.
 * @property methods the methods enabled in merchant config and Payment settings, in config order. The switcher shows
 *   only with two (FR-025 AC1).
 * @property busy a leave action is running (Back to order, Cancel order), so a second tap does nothing.
 */
@Immutable
data class CheckoutUiState(
    val stage: CheckoutStage = CheckoutStage.Creating(PaymentMethod.QRIS),
    val brandName: String = "",
    val kioskName: String = "",
    val amount: String = "",
    val methods: List<PaymentMethod> = emptyList(),
    val busy: Boolean = false,
)

/** The body of Checkout; one case per scenario of `Checkout.dc.html`. [method] is the method being paid with. */
@Immutable
sealed interface CheckoutStage {
    val method: PaymentMethod

    /** "Creating your payment…" (`creating`): E7 or E9 is running, or the previous payment is being cancelled. */
    data class Creating(
        override val method: PaymentMethod,
    ) : CheckoutStage

    /**
     * The QR with its "Code expires in m:ss" ring (`qr`). The seconds come from the server expiry through the
     * payment machine (NFR-017).
     */
    data class WaitingForQr(
        val qrString: String,
        val remainingSeconds: Int,
        val totalSeconds: Int,
    ) : CheckoutStage {
        override val method: PaymentMethod get() = PaymentMethod.QRIS

        // The QR payload is a payment secret that must not reach logs (NFR-006).
        override fun toString(): String = "WaitingForQr(remainingSeconds=$remainingSeconds, totalSeconds=$totalSeconds)"
    }

    /** The terminal prompt (`card`); [terminalPrompted] is true once the terminal showed the amount (FR-018). */
    data class WaitingForCard(
        val terminalPrompted: Boolean,
    ) : CheckoutStage {
        override val method: PaymentMethod get() = PaymentMethod.CARD
    }

    /** "Checking your payment…" (`checking`): the backend does not answer; never claims "not charged" (FR-019). */
    data class Checking(
        override val method: PaymentMethod,
    ) : CheckoutStage

    /** "Confirming payment": the terminal approved or the backend reports processing or paid (FR-017). */
    data class Processing(
        override val method: PaymentMethod,
    ) : CheckoutStage

    /** One of the failure boards: `expired`, `cancelled`, `declined`, `terminalTimeout`, `offline` or the generic one. */
    data class Failed(
        val failure: CheckoutFailure,
    ) : CheckoutStage {
        override val method: PaymentMethod get() = failure.method
    }
}

/**
 * Why Checkout shows its failure panel, and what the panel offers (FR-019).
 *
 * @property notCharged the green "You have not been charged · your order is saved" note. Only after a backend terminal
 *   non-paid status (FR-019 AC2); never for an unknown status or for a failure before a payment exists.
 * @property offline the kiosk is offline now: "Try again" is disabled and says "Reconnecting…" (`S_Checkout_offline`).
 * @property switchTo the other method for "Pay by X instead", or `null` when it is not enabled.
 */
@Immutable
data class CheckoutFailure(
    val method: PaymentMethod,
    val kind: FailureKind,
    val notCharged: Boolean = false,
    val offline: Boolean = false,
    val switchTo: PaymentMethod? = null,
) {
    /**
     * The "Ordering is paused" panel of `S_Checkout_offline`: the failure is a lost connection and the kiosk is still
     * offline. It has no switch button; "Back to order" stands in its place.
     */
    val paused: Boolean get() = offline && kind == FailureKind.UNREACHABLE
}

/** The failure copy of Checkout, one value per title (FR-019 AC1). */
enum class FailureKind {
    /** "QR code expired". */
    EXPIRED,

    /** "Payment cancelled": in the payer's app (QRIS) or on the terminal (card). */
    CANCELLED,

    /** "Card declined". */
    DECLINED,

    /** "Terminal not responding". */
    TERMINAL_TIMEOUT,

    /** "Payment didn't go through" with "Can't reach the restaurant system. Please try again." (FR-016). */
    UNREACHABLE,

    /** "Payment didn't go through" with "Something went wrong. Please try again.". */
    GENERIC,
}

/** What the customer can do on Checkout. */
sealed interface CheckoutEvent {
    /** "Back to order": cancels the payment and the order and returns to Your order with the cart intact (FR-016). */
    data object BackToOrder : CheckoutEvent

    /** "Try again": a fresh payment for the same order, or a new submission after a failed E7 (FR-019). */
    data object TryAgain : CheckoutEvent

    /** The method switcher or "Pay by X instead" (FR-017, FR-019). */
    data class PayWith(
        val method: PaymentMethod,
    ) : CheckoutEvent

    /** "Cancel order": cancels the order and returns to Idle (FR-019). */
    data object CancelOrder : CheckoutEvent

    /** The QR could not be drawn from the backend's `qrString`, so the payment cannot be made with it. */
    data object QrEncodeFailed : CheckoutEvent
}

/** One-off effects of SCR-11. */
sealed interface CheckoutEffect {
    /** Back to SCR-10 with the cart intact: "Back to order", `PRICE_CHANGED` or `ITEM_UNAVAILABLE` (FR-016). */
    data object ToYourOrder : CheckoutEffect

    /** The backend reported `PAID` (A-15): on to SCR-12. */
    data object ToComplete : CheckoutEffect

    /** The order ended: back to SCR-04 (FR-019). */
    data object ToIdle : CheckoutEffect
}
