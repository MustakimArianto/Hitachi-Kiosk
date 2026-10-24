package com.hitachi.kiosk.ui.qr

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.hitachi.kiosk.core.designsystem.component.QrCenterMark
import com.hitachi.kiosk.core.designsystem.theme.KioskPalette
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * TC-079 / DEV-048 / FR-017: renders [QrCode] at 480 dpx with the merchant centre mark on a 1080 × 1920 mdpi screen
 * (1 dpx = 1 px, as on the kiosk), decodes the bitmap with ZXing and compares the text with the input.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class QrCodeDecodeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a typical QRIS string decodes to the input with the centre mark on`() {
        // 280 characters, like a dynamic QRIS from the gateway.
        assertDecodes(QrisSamples.payload(280))
    }

    @Test
    fun `the mock server QRIS string decodes to the input`() {
        assertDecodes(
            "00020101021226650013ID.CO.EXAMPLE.WWW0118936000000000000000303UMI51440014ID.CO.QRIS.WWW" +
                "0215ID1020000004721520458125303360540617270058",
        )
    }

    @Test
    fun `the longest EMVCo payload of 512 characters decodes to the input`() {
        // The EMVCo merchant-presented QR payload is at most 512 characters, so the backend cannot send more.
        assertDecodes(QrisSamples.payload(512))
    }

    @Test
    fun `a numeric string decodes to the input`() {
        assertDecodes(QrisSamples.numeric(300))
    }

    @Test
    fun `the centre mark covers at most 20 percent of the QR width`() {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame {
                    QrCode(content = QrisSamples.payload(280), modifier = Modifier.testTag(QR_TAG)) {
                        QrCenterMark(Modifier.testTag(MARK_TAG))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val qr = composeRule.onNodeWithTag(QR_TAG).fetchSemanticsNode().size
        val mark = composeRule.onNodeWithTag(MARK_TAG).fetchSemanticsNode().size
        val markWidthPx = mark.width

        assertThat(qr.width).isEqualTo(QR_SIZE_PX)
        assertThat(markWidthPx.toFloat() / qr.width).isAtMost(QR_MARK_MAX_FRACTION)
        assertThat(markWidthPx).isGreaterThan(0)
    }

    @Test
    fun `content that cannot be encoded renders a blank square and reports the failure`() {
        var failures = 0
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame {
                    QrCode(content = "", modifier = Modifier.testTag(QR_TAG), onEncodeFailed = { failures++ })
                }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(QR_TAG).captureToImage().asAndroidBitmap()

        assertThat(failures).isEqualTo(1)
        assertThat(allPixelsAre(bitmap, KioskPalette.Surface.toArgb())).isTrue()
    }

    private fun assertDecodes(content: String) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame {
                    QrCode(content = content, modifier = Modifier.testTag(QR_TAG)) { QrCenterMark() }
                }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(QR_TAG).captureToImage().asAndroidBitmap()

        assertThat(bitmap.width).isEqualTo(QR_SIZE_PX)
        assertThat(decode(bitmap)).isEqualTo(content)
    }

    private fun decode(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    private fun allPixelsAre(
        bitmap: Bitmap,
        argb: Int,
    ): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.all { it == argb }
    }

    private companion object {
        const val QR_TAG = "qr"
        const val MARK_TAG = "qr-mark"
    }
}
