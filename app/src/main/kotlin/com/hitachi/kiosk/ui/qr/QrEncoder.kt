package com.hitachi.kiosk.ui.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.hitachi.kiosk.core.domain.log.KioskLog

/** Quiet zone around the code, in modules (TD-07). The QR standard asks for at least 4. */
internal const val QR_QUIET_ZONE_MODULES = 4

/**
 * Encodes a QRIS `qrString` with ZXing at error correction level H and a quiet zone of
 * [QR_QUIET_ZONE_MODULES] (TD-07, FR-017). H is above the PRD floor of M because the merchant mark always covers
 * the centre. The matrix is 1 px per module and includes the quiet zone, so `width` is `modules + 8`.
 */
internal object QrEncoder {
    private val hints =
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to QR_QUIET_ZONE_MODULES,
        )

    /**
     * The module matrix for [content], or `null` when it cannot be encoded (blank, or too long for a version 40
     * code at level H). It never throws and never logs the content: a QR string is payment data (NFR-006).
     */
    fun encode(content: String): BitMatrix? {
        if (content.isBlank()) {
            KioskLog.w("qr.encode_failed", "reason" to "blank")
            return null
        }
        return try {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: WriterException) {
            logFailure(content.length, e)
        } catch (e: IllegalArgumentException) {
            logFailure(content.length, e)
        }
    }

    private fun logFailure(
        length: Int,
        e: Exception,
    ): BitMatrix? {
        // The exception is not passed on: only its class name and the length are safe to log.
        KioskLog.w("qr.encode_failed", "reason" to e.javaClass.simpleName, "length" to length)
        return null
    }
}

/** Where the modules go on a square canvas: whole pixels per module and the offset that centres the grid. */
internal data class QrLayout(
    val modulePx: Int,
    val offsetPx: Int,
) {
    companion object {
        /**
         * Layout for a matrix of [matrixSize] modules (quiet zone included) on a square [canvasPx] wide. Each
         * module is a whole number of pixels, so every edge lands on a pixel boundary and nothing is scaled or
         * blurred (TD-07); the leftover is split evenly and widens the quiet zone. Returns `null` when the canvas
         * is smaller than one pixel per module.
         */
        fun of(
            matrixSize: Int,
            canvasPx: Int,
        ): QrLayout? {
            if (matrixSize <= 0 || canvasPx < matrixSize) return null
            val module = canvasPx / matrixSize
            return QrLayout(modulePx = module, offsetPx = (canvasPx - module * matrixSize) / 2)
        }
    }
}
