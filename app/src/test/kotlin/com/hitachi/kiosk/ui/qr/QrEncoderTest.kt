package com.hitachi.kiosk.ui.qr

import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.log.LogSink
import org.junit.After
import org.junit.Test

/** DEV-048 / TR-INT-02: encoding at level H with a 4 module quiet zone, failure handling and pixel layout. */
class QrEncoderTest {
    private val logged = mutableListOf<Pair<String, Map<String, String>>>()

    @After
    fun resetLog() = KioskLog.install(LogSink { _, _, _, _ -> })

    @Test
    fun `a QRIS string encodes to a square matrix with a quiet zone`() {
        val matrix = QrEncoder.encode(QrisSamples.payload(260))!!

        assertThat(matrix.width).isEqualTo(matrix.height)
        for (i in 0 until matrix.width) {
            for (q in 0 until QR_QUIET_ZONE_MODULES) {
                assertThat(matrix.get(i, q)).isFalse()
                assertThat(matrix.get(q, i)).isFalse()
                assertThat(matrix.get(i, matrix.height - 1 - q)).isFalse()
                assertThat(matrix.get(matrix.width - 1 - q, i)).isFalse()
            }
        }
    }

    @Test
    fun `level H is used, which needs a bigger code than M for the same data`() {
        // 100 bytes fit version 6 (41 modules) at level M but need version 10 (57 modules) at level H.
        val matrix = QrEncoder.encode("x".repeat(100))!!

        assertThat(matrix.width).isEqualTo(57 + 2 * QR_QUIET_ZONE_MODULES)
    }

    @Test
    fun `blank content gives null and logs without the content`() {
        KioskLog.install { _, event, fields, _ -> logged += event to fields }

        assertThat(QrEncoder.encode("")).isNull()
        assertThat(QrEncoder.encode("   ")).isNull()
        assertThat(logged.map { it.first }).containsExactly("qr.encode_failed", "qr.encode_failed")
    }

    @Test
    fun `content too long for any QR version gives null and does not log it`() {
        KioskLog.install { _, event, fields, _ -> logged += event to fields }
        val tooLong = "SECRET" + "9".repeat(4000)

        assertThat(QrEncoder.encode(tooLong)).isNull()

        assertThat(logged).hasSize(1)
        val (event, fields) = logged.single()
        assertThat(event).isEqualTo("qr.encode_failed")
        assertThat(fields.values.joinToString()).doesNotContain("SECRET")
        assertThat(fields["length"]).isEqualTo(tooLong.length.toString())
    }

    @Test
    fun `layout uses whole pixels per module and centres the grid`() {
        assertThat(QrLayout.of(matrixSize = 37, canvasPx = 480)).isEqualTo(QrLayout(modulePx = 12, offsetPx = 18))
        assertThat(QrLayout.of(matrixSize = 120, canvasPx = 480)).isEqualTo(QrLayout(modulePx = 4, offsetPx = 0))
        assertThat(QrLayout.of(matrixSize = 125, canvasPx = 480)).isEqualTo(QrLayout(modulePx = 3, offsetPx = 52))
    }

    @Test
    fun `layout gives null when the canvas is smaller than the matrix`() {
        assertThat(QrLayout.of(matrixSize = 125, canvasPx = 124)).isNull()
        assertThat(QrLayout.of(matrixSize = 0, canvasPx = 480)).isNull()
    }
}
