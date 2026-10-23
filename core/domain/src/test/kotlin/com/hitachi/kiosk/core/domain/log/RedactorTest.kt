package com.hitachi.kiosk.core.domain.log

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** NFR-006, technical-design §12: every redaction rule. */
class RedactorTest {
    @Test
    fun `PINs, proofs, tokens and authorization headers are dropped`() {
        val redacted =
            Redactor.redact(
                mapOf(
                    "pin" to "1234",
                    "currentPin" to "1234",
                    "newPin" to "5678",
                    "currentPinProof" to "proof",
                    "deviceToken" to "dvt_secret",
                    "Authorization" to "Bearer dvt_secret",
                    "event" to "kept",
                ),
            )

        assertThat(redacted).containsExactly("event", "kept")
    }

    @Test
    fun `payment references keep only their last 4 characters`() {
        val redacted =
            Redactor.redact(
                mapOf(
                    "paymentId" to "pay_Q1x8",
                    "reference" to "QR-8812-4471-0091",
                    "terminalRef" to "TRX-20261022-000247",
                    "approvalCode" to "123456",
                    "qrString" to "000201010212",
                ),
            )

        assertThat(redacted).containsExactly(
            "paymentId",
            "•••Q1x8",
            "reference",
            "•••0091",
            "terminalRef",
            "•••0247",
            "approvalCode",
            "•••3456",
            "qrString",
            "•••0212",
        )
    }

    @Test
    fun `short references are fully masked`() {
        assertThat(Redactor.truncate("1234")).isEqualTo("•••")
    }

    @Test
    fun `customer fields are dropped`() {
        assertThat(Redactor.redact(mapOf("customerName" to "Budi", "customer_phone" to "0812"))).isEmpty()
    }
}
