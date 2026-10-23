package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.HealthStatus
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlin.time.Duration.Companion.seconds

/**
 * The mock scenarios of api-spec §4, applied to a [FakeBackendApi]. The debug Developer options offer the same list
 * (DEV-009), so testers get identical behaviour from the fakes and from a custom mock server.
 */
enum class FakeScenario(
    val id: String,
) {
    HAPPY_PATH("happy_path") {
        override fun applyTo(api: FakeBackendApi) = Unit
    },
    ACTIVATE_UNREGISTERED("activate_unregistered") {
        override fun applyTo(api: FakeBackendApi) =
            api.activate.fail(DomainError.Server(code = "DEVICE_NOT_REGISTERED", httpStatus = 404))
    },
    ACTIVATE_DISABLED("activate_disabled") {
        override fun applyTo(api: FakeBackendApi) =
            api.activate.fail(DomainError.Server(code = "DEVICE_DISABLED", httpStatus = 403))
    },
    MERCHANTS_EMPTY("merchants_empty") {
        override fun applyTo(api: FakeBackendApi) = api.merchants.succeed(emptyList())
    },
    MERCHANT_CLOSED("merchant_closed") {
        override fun applyTo(api: FakeBackendApi) = api.merchants.succeed(SampleData.merchantsWithClosed)
    },

    /** E3 and E5 succeed, E6 keeps failing with 503, so the menu download fails after binding (FR-004 AC4). */
    MENU_DOWNLOAD_FAILED("menu_download_failed") {
        override fun applyTo(api: FakeBackendApi) =
            api.menu.fail(DomainError.Server(code = "SERVER_ERROR", httpStatus = 503))
    },
    ORDER_PRICE_CHANGED("order_price_changed") {
        override fun applyTo(api: FakeBackendApi) =
            api.createOrder.fail(
                DomainError.PriceChanged(
                    menuVersion = "menu-119",
                    configVersion = "cfg-42",
                    serverTotals = SampleData.changedTotals,
                ),
            )
    },
    ORDER_ITEM_UNAVAILABLE("order_item_unavailable") {
        override fun applyTo(api: FakeBackendApi) =
            api.createOrder.fail(DomainError.ItemUnavailable(lineIds = listOf("l2"), productIds = listOf("c1")))
    },

    /** PENDING for the first [PENDING_POLLS] polls (about 5 s at the 2 s interval), then PAID. */
    QRIS_PAID_AFTER_5S("qris_paid_after_5s") {
        override fun applyTo(api: FakeBackendApi) {
            repeat(PENDING_POLLS) { api.paymentStatus.enqueue(Outcome.Success(SampleData.paymentPending)) }
            api.paymentStatus.succeed(SampleData.paymentPaid)
        }
    },
    QRIS_CANCELLED("qris_cancelled") {
        override fun applyTo(api: FakeBackendApi) =
            api.paymentStatus.succeed(
                SampleData.paymentPending.copy(
                    status = PaymentStatus.FAILED,
                    failureReason = PaymentFailureReason.CANCELLED_BY_PAYER,
                ),
            )
    },

    /** The QR expires 20 s after it is created. */
    QRIS_EXPIRE_FAST("qris_expire_fast") {
        override fun applyTo(api: FakeBackendApi) {
            val payment = SampleData.qrisPayment
            api.startQris.succeed(payment.copy(expiresAt = payment.createdAt + FAST_EXPIRY))
        }
    },
    CARD_DECLINED("card_declined") {
        override fun applyTo(api: FakeBackendApi) = api.paymentStatus.succeed(SampleData.paymentDeclined)
    },
    CARD_TIMEOUT("card_timeout") {
        override fun applyTo(api: FakeBackendApi) =
            api.paymentStatus.succeed(
                SampleData.paymentDeclined.copy(failureReason = PaymentFailureReason.TERMINAL_TIMEOUT, message = null),
            )
    },
    SLOW_NETWORK("slow_network") {
        override fun applyTo(api: FakeBackendApi) {
            api.extraDelay = SLOW_DELAY
        }
    },

    /** The first E7 fails with 503; the retry with the same key creates the order. */
    SERVER_ERROR_ONCE("server_error_once") {
        override fun applyTo(api: FakeBackendApi) =
            api.createOrder.enqueue(Outcome.Failure(DomainError.Server(code = "SERVER_ERROR", httpStatus = 503)))
    },
    GATEWAY_DOWN("gateway_down") {
        override fun applyTo(api: FakeBackendApi) =
            api.health.succeed(SampleData.health.copy(paymentGateway = HealthStatus.DOWN))
    },
    ;

    abstract fun applyTo(api: FakeBackendApi)

    companion object {
        const val PENDING_POLLS = 3
        private val FAST_EXPIRY = 20.seconds
        private val SLOW_DELAY = 8.seconds

        fun fromId(id: String): FakeScenario? = entries.firstOrNull { it.id == id }
    }
}
