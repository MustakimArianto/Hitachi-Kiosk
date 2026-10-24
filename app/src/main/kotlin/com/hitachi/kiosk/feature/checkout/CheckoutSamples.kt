package com.hitachi.kiosk.feature.checkout

import com.hitachi.kiosk.core.domain.model.PaymentMethod

/**
 * Sample states for previews and the Checkout screenshot boards, after the design's sample order (Rp 173.000 for
 * "Urban Grill · Kiosk 3", `Checkout.dc.html`). The QR payload is made up: it only has to look like a QRIS code.
 */
internal object CheckoutSamples {
    /** A made-up EMVCo-style payload, long enough for a realistic code. Not a real payment. */
    const val SAMPLE_QR: String =
        "00020101021226650013ID.CO.EXAMPLE.WWW0118936000000000000000303UMI51440014ID.CO.QRIS.WWW0215ID1020000004721" +
            "0303UMI52045812530336054061730005802ID5911Urban Grill6007Jakarta6105101106219" +
            "0515A1B2C3D4E5F6A7B80703K036304ABCD"

    private val both = listOf(PaymentMethod.QRIS, PaymentMethod.CARD)

    private val base =
        CheckoutUiState(
            brandName = "Urban Grill",
            kioskName = "Kiosk 3",
            amount = "Rp 173.000",
            methods = both,
        )

    /** `qr`: the QR with 5:00 on the ring. */
    val Qr = base.copy(stage = CheckoutStage.WaitingForQr(SAMPLE_QR, remainingSeconds = 300, totalSeconds = 300))

    /** The QR close to its end. */
    val QrExpiring = base.copy(stage = CheckoutStage.WaitingForQr(SAMPLE_QR, remainingSeconds = 42, totalSeconds = 300))

    /** `card`: the terminal view. */
    val Card = base.copy(stage = CheckoutStage.WaitingForCard(terminalPrompted = true))

    /** `creating` on QRIS: "Creating your payment…" with "Setting up your QR code…". */
    val Creating = base.copy(stage = CheckoutStage.Creating(PaymentMethod.QRIS))

    /** `creating` on card. */
    val CreatingCard = base.copy(stage = CheckoutStage.Creating(PaymentMethod.CARD))

    /** `checking`: "Checking your payment…" (no board in the design). */
    val Checking = base.copy(stage = CheckoutStage.Checking(PaymentMethod.QRIS))

    /** "Confirming payment" after a QRIS payment. */
    val Processing = base.copy(stage = CheckoutStage.Processing(PaymentMethod.QRIS))

    /** "Confirming payment" after a card approval. */
    val ProcessingCard = base.copy(stage = CheckoutStage.Processing(PaymentMethod.CARD))

    /** `expired`: "QR code expired". */
    val Expired = failed(PaymentMethod.QRIS, FailureKind.EXPIRED, notCharged = true)

    /** `cancelled`: "Payment cancelled" in the payer's app. */
    val Cancelled = failed(PaymentMethod.QRIS, FailureKind.CANCELLED, notCharged = true)

    /** "Payment cancelled" on the terminal. */
    val CancelledOnTerminal = failed(PaymentMethod.CARD, FailureKind.CANCELLED, notCharged = true)

    /** `declined`: "Card declined". */
    val Declined = failed(PaymentMethod.CARD, FailureKind.DECLINED, notCharged = true)

    /** `terminalTimeout`: "Terminal not responding"; the terminal's silence never claims "not charged" here. */
    val TerminalTimeout = failed(PaymentMethod.CARD, FailureKind.TERMINAL_TIMEOUT, notCharged = true)

    /** `offline`: "Ordering is paused", "Try again" disabled as "Reconnecting…". */
    val Offline = failed(PaymentMethod.QRIS, FailureKind.UNREACHABLE, offline = true)

    /** The order could not be sent while online: "Can't reach the restaurant system." */
    val Unreachable = failed(PaymentMethod.QRIS, FailureKind.UNREACHABLE)

    private fun failed(
        method: PaymentMethod,
        kind: FailureKind,
        notCharged: Boolean = false,
        offline: Boolean = false,
    ): CheckoutUiState =
        base.copy(
            stage =
                CheckoutStage.Failed(
                    CheckoutFailure(
                        method = method,
                        kind = kind,
                        notCharged = notCharged,
                        offline = offline,
                        switchTo = if (method == PaymentMethod.QRIS) PaymentMethod.CARD else PaymentMethod.QRIS,
                    ),
                ),
        )
}
