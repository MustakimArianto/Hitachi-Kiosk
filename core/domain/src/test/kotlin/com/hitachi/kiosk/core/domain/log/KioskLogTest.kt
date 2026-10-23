package com.hitachi.kiosk.core.domain.log

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class KioskLogTest {
    private data class Record(
        val level: LogLevel,
        val event: String,
        val fields: Map<String, String>,
        val error: Throwable?,
    )

    private val records = mutableListOf<Record>()

    @After
    fun tearDown() {
        KioskLog.install { _, _, _, _ -> }
    }

    @Test
    fun `records reach the installed sink with their level`() {
        KioskLog.install { level, event, fields, error -> records += Record(level, event, fields, error) }
        val failure = IllegalStateException("boom")

        KioskLog.d("debug_event", "a" to 1)
        KioskLog.i("info_event")
        KioskLog.w("warn_event", "b" to null)
        KioskLog.e("error_event", error = failure)

        assertThat(
            records.map { it.level },
        ).containsExactly(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR)
            .inOrder()
        assertThat(records[0].fields).containsExactly("a", "1")
        assertThat(records[2].fields).containsExactly("b", "null")
        assertThat(records[3].error).isSameInstanceAs(failure)
    }

    @Test
    fun `fields are redacted before the sink sees them`() {
        KioskLog.install { level, event, fields, error -> records += Record(level, event, fields, error) }

        KioskLog.i("payment_started", "paymentId" to "pay_Q1x8XYZ1", "pin" to "1234", "orderId" to "ord_7Hq2k9")

        assertThat(records.single().fields).containsExactly("paymentId", "•••XYZ1", "orderId", "ord_7Hq2k9")
    }

    @Test
    fun `without a sink records are dropped`() {
        KioskLog.i("nobody_listens")

        assertThat(records).isEmpty()
    }
}
