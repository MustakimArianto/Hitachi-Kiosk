package com.hitachi.kiosk.ui.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import com.hitachi.kiosk.core.designsystem.component.QrCard
import com.hitachi.kiosk.core.designsystem.component.QrCenterMark
import com.hitachi.kiosk.core.designsystem.component.formatCountdown
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DEV-048 / TC-074: the QRIS card of `Checkout.dc.html` with the merchant mark and the expiry ring. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h1920dp-mdpi")
class QrCardScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `QrCard full code`() = capture("QrCard", remainingSeconds = 300)

    @Test
    fun `QrCard running out`() = capture("QrCard_expiring", remainingSeconds = 42)

    private fun capture(
        name: String,
        remainingSeconds: Int,
    ) {
        composeRule.setContent {
            KioskTheme(reduceMotion = true) {
                DesignFrame {
                    Box(Modifier.testTag(CARD_TAG).background(KioskTheme.colors.bg).padding(48.dpx)) {
                        QrCard(
                            title = "QRIS",
                            caption = "Urban Grill · Kiosk 3",
                            expiryText = "Code expires in ${formatCountdown(remainingSeconds)}",
                            remainingSeconds = remainingSeconds,
                            totalSeconds = 300,
                        ) {
                            QrCode(content = QrisSamples.payload(280), contentDescription = "Payment QR code") {
                                QrCenterMark()
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag(CARD_TAG).captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val CARD_TAG = "qr-card"
    }
}
