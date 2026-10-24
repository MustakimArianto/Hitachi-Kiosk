package com.hitachi.kiosk.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.model.PaymentFailureReason
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import com.hitachi.kiosk.core.domain.model.PaymentStatus
import com.hitachi.kiosk.core.testing.fake.FakeBackendApi
import com.hitachi.kiosk.core.testing.fixture.SampleData
import com.hitachi.kiosk.devtools.CheckoutDebugTools
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Test tag of the simulate bar's "Approve". */
const val SIMULATE_APPROVE_TAG = "simulate-approve"

/** Test tag of the simulate bar's "Decline". */
const val SIMULATE_DECLINE_TAG = "simulate-decline"

/**
 * The switchable bindings the simulate bar acts on. [DeveloperOptionsModule] fills it when Hilt builds them, so the bar
 * reaches the same fake backend and terminal the payment machine talks to.
 */
@Singleton
class DebugBindings
    @Inject
    constructor() {
        @Volatile
        var backend: SwitchableBackendApi? = null

        @Volatile
        var terminal: SwitchableCardTerminal? = null
    }

/**
 * Debug only: the design's "Prototype only · simulate the result" bar for Checkout (FR-019, PRD §3 C6). "Approve" and
 * "Decline" force the result against the fakes: the next E10 answer says `PAID`, or a failed payment, and a terminal
 * waiting for the bar approves or declines. It works only while the backend is the in-app fake, and for a card payment
 * only while the terminal is not the release adapter; otherwise it draws nothing.
 *
 * It sets one-shot answers, so the next payment behaves like the scenario again.
 */
@Singleton
class CheckoutSimulator(
    private val developerSettings: StateFlow<DeveloperSettings>,
    private val bindings: DebugBindings,
) : CheckoutDebugTools {
    @Inject
    constructor(store: DeveloperSettingsStore, bindings: DebugBindings) : this(store.settings, bindings)

    @Composable
    override fun SimulateBar(
        method: PaymentMethod,
        modifier: Modifier,
    ) {
        val settings by developerSettings.collectAsStateWithLifecycle()
        val usesFakes =
            settings.backendMode == BackendMode.FAKES &&
                (method == PaymentMethod.QRIS || settings.terminalMode != TerminalMode.REAL)
        if (!usesFakes) return
        SimulateBarContent(
            onApprove = { approve(method) },
            onDecline = { decline(method) },
            modifier = modifier,
        )
    }

    /** The next E10 says `PAID`; a card sale that waits for the bar is approved on the terminal. */
    internal fun approve(method: PaymentMethod) {
        fake()?.paymentStatus?.enqueue(Outcome.Success(SampleData.paymentPaid.copy(method = method)))
        if (method == PaymentMethod.CARD) bindings.terminal?.manual?.approve()
    }

    /** The next E10 says the payment failed; a card sale that waits for the bar is declined on the terminal. */
    internal fun decline(method: PaymentMethod) {
        val failed =
            when (method) {
                PaymentMethod.QRIS -> {
                    SampleData.paymentPending.copy(
                        status = PaymentStatus.FAILED,
                        failureReason = PaymentFailureReason.CANCELLED_BY_PAYER,
                    )
                }

                PaymentMethod.CARD -> {
                    SampleData.paymentDeclined
                }
            }
        fake()?.paymentStatus?.enqueue(Outcome.Success(failed))
        if (method == PaymentMethod.CARD) bindings.terminal?.manual?.decline()
    }

    private fun fake(): FakeBackendApi? = bindings.backend?.fakeOrNull()
}

@Composable
private fun SimulateBarContent(
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(112.dpx)
                .clip(KioskTheme.shapes.large)
                .background(colors.surfaceAlt)
                .padding(start = 32.dpx, end = 12.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dpx),
    ) {
        KioskText(
            text = stringResource(R.string.checkout_simulate_label),
            style = KioskTheme.typography.meta.copy(fontSize = 24.spx),
            color = colors.muted,
            modifier = Modifier.weight(1f),
        )
        SimulateButton(
            stringResource(R.string.checkout_simulate_approve),
            colors.success,
            SIMULATE_APPROVE_TAG,
            onApprove,
        )
        SimulateButton(stringResource(R.string.checkout_simulate_decline), colors.ink, SIMULATE_DECLINE_TAG, onDecline)
    }
}

@Composable
private fun SimulateButton(
    text: String,
    background: Color,
    tag: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
    Box(
        modifier =
            Modifier
                .height(88.dpx)
                .pressScale(scale)
                .clip(KioskTheme.shapes.pill)
                .background(background)
                .testTag(tag)
                .selectable(
                    selected = false,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = 32.dpx),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(
            text = text,
            style = KioskTheme.typography.button.copy(fontSize = 28.spx, fontWeight = FontWeight.W700),
            color = KioskTheme.colors.surface,
            maxLines = 1,
        )
    }
}
