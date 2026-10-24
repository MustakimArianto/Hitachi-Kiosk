package com.hitachi.kiosk.core.domain.model

/**
 * The device's own settings in `settings.preferences_pb` (architecture §6, technical-design §6.2). They are local
 * per kiosk (A-25) and only restrict what merchant config allows (A-24). 0.4.0 reads the values the ordering screens
 * need; the staff screens that change them arrive in 0.7.0 (FR-025, FR-026).
 *
 * @property upsellEnabled "Suggest add-ons" (FR-014, FR-026). On by default.
 * @property idleTimeoutSeconds "Return to idle after" 30 / 60 / 120 s (FR-023, FR-026). 60 by default.
 * @property paymentMethods the methods staff left on in Payment settings (FR-025). All by default.
 * @property roundingStep "Round total to" (FR-025), or `null` for the config default (A-25).
 */
data class KioskSettings(
    val upsellEnabled: Boolean = true,
    val idleTimeoutSeconds: Int = DEFAULT_IDLE_TIMEOUT_SECONDS,
    val paymentMethods: Set<PaymentMethod> = PaymentMethod.entries.toSet(),
    val roundingStep: Long? = null,
) {
    companion object {
        /** FR-026 default. */
        const val DEFAULT_IDLE_TIMEOUT_SECONDS: Int = 60

        /** The FR-026 choices; any other stored value falls back to the default. */
        val IDLE_TIMEOUT_CHOICES: List<Int> = listOf(30, 60, 120)
    }
}

/**
 * The method tiles of Your order (FR-015): enabled in merchant config **and** on in Payment settings, in config order,
 * so the first one (QRIS in the design) is the default selection. Empty means "No payment methods right now" (A-14).
 */
fun MerchantConfig.visiblePaymentMethods(settings: KioskSettings): List<PaymentMethod> =
    paymentMethods
        .filter { it.enabled && it.method in settings.paymentMethods }
        .map { it.method }
        .distinct()
