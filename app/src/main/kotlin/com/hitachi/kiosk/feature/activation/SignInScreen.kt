package com.hitachi.kiosk.feature.activation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.BrandLockup
import com.hitachi.kiosk.core.designsystem.component.BrandMark
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.component.PrimaryCta
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Common
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.SignIn
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.fadeIn
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.riseIn
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

/** `markIn` on Sign in: scale .7 → 1, rotate −6° → 0. */
private const val MARK_START_SCALE = 0.7f
private const val MARK_START_ROTATION = -6f

/** Letter spacing of panel titles (−0.02 em) and the serial number (+0.02 em). */
private const val TIGHT_EM = -0.02f
private const val SERIAL_EM = 0.02f
private const val PANEL_TITLE_LINE_HEIGHT_EM = 1.1f

/** Test tag of the Sign in root. */
const val SIGN_IN_SCREEN_TAG = "screen:SCR-02"

/** Test tag of the Log in / Try again button. */
const val SIGN_IN_BUTTON_TAG = "sign-in-button"

/**
 * SCR-02 Sign in (`Login.dc.html`, `S_Login_*`, FR-002, FR-003, DEV-013). Draws [state] exactly; [onEvent] receives
 * [SignInEvent.LogIn] only while the button is enabled (FR-003 AC3).
 */
@Composable
fun SignInScreen(
    state: SignInUiState,
    onEvent: (SignInEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .testTag(SIGN_IN_SCREEN_TAG),
    ) {
        SignInHeader(versionName = state.versionName)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 72.dpx, end = 72.dpx, top = 168.dpx, bottom = 360.dpx),
            verticalArrangement = Arrangement.spacedBy(64.dpx, Alignment.CenterVertically),
        ) {
            SignInMark()
            SignInIntro(showIntro = state.error == null)
            state.error?.let { ErrorPanel(error = it) }
        }
        SignInDock(
            state = state,
            onLogIn = { onEvent(SignInEvent.LogIn) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SignInHeader(versionName: String) {
    val colors = KioskTheme.colors
    val fade = animateEntrance(Common.HEADER_FADE_MILLIS, easing = KioskEasing.Ease)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 72.dpx, end = 72.dpx, top = 48.dpx)
                .height(96.dpx)
                .fadeIn(fade),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BrandLockup(name = stringResource(R.string.brand_name), bellMark = true)
        Box(
            modifier =
                Modifier
                    .height(96.dpx)
                    .border(2.dpx, colors.line, KioskTheme.shapes.pill)
                    .padding(horizontal = 32.dpx),
            contentAlignment = Alignment.Center,
        ) {
            KioskText(
                text = stringResource(R.string.sign_in_version, versionName),
                style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun SignInMark() {
    val description = stringResource(R.string.sign_in_mark_description)
    val mark = animateEntrance(SignIn.MARK_IN_MILLIS, SignIn.MARK_IN_DELAY_MILLIS)
    BrandMark(
        modifier =
            Modifier
                .size(160.dpx)
                .semantics { contentDescription = description }
                .graphicsLayer {
                    val p = mark.value
                    alpha = p
                    val scale = MARK_START_SCALE + (1f - MARK_START_SCALE) * p
                    scaleX = scale
                    scaleY = scale
                    rotationZ = MARK_START_ROTATION * (1f - p)
                },
    )
}

@Composable
private fun SignInIntro(showIntro: Boolean) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val welcome = animateEntrance(SignIn.WELCOME_UP_MILLIS, SignIn.WELCOME_UP_DELAY_MILLIS)
    val line1 = animateEntrance(SignIn.TITLE_RISE_MILLIS, SignIn.TITLE_RISE_DELAY_MILLIS)
    val line2 = animateEntrance(SignIn.TITLE_RISE_MILLIS, SignIn.TITLE_RISE_SECOND_DELAY_MILLIS)
    Column(verticalArrangement = Arrangement.spacedBy(32.dpx)) {
        KioskText(
            text = stringResource(R.string.sign_in_welcome),
            style = typography.meta,
            color = colors.muted,
            modifier = Modifier.fadeUp(welcome, Common.UP_DISTANCE_PX.dpx),
        )
        Column {
            TitleLine(text = stringResource(R.string.sign_in_title_line1), progress = line1, muted = false)
            TitleLine(text = stringResource(R.string.sign_in_title_line2), progress = line2, muted = true)
        }
        if (showIntro) {
            val intro = animateEntrance(SignIn.INTRO_UP_MILLIS, SignIn.INTRO_UP_DELAY_MILLIS)
            KioskText(
                text = stringResource(R.string.sign_in_intro),
                style = typography.body,
                color = colors.muted,
                modifier =
                    Modifier
                        .widthIn(max = 760.dpx)
                        .fadeUp(intro, Common.UP_DISTANCE_PX.dpx),
            )
        }
    }
}

@Composable
private fun TitleLine(
    text: String,
    progress: State<Float>,
    muted: Boolean,
) {
    Box(
        modifier =
            Modifier
                .clipToBounds()
                .padding(bottom = 8.dpx),
    ) {
        KioskText(
            text = text,
            style = KioskTheme.typography.display,
            color = if (muted) KioskTheme.colors.muted else KioskTheme.colors.ink,
            modifier = Modifier.riseIn(progress),
            cssLineBox = true,
        )
    }
}

@Composable
private fun ErrorPanel(error: SignInError) {
    val colors = KioskTheme.colors
    val copy = error.copy()
    val panelShape = KioskTheme.shapes.card
    val enter = animateEntrance(Common.PANEL_UP_MILLIS)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .fadeUp(enter, Common.UP_DISTANCE_PX.dpx)
                .background(colors.errorBg, panelShape)
                .border(2.dpx, colors.errorBorder, panelShape)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }
                .padding(40.dpx),
        horizontalArrangement = Arrangement.spacedBy(32.dpx),
    ) {
        Box(
            modifier =
                Modifier
                    .size(88.dpx)
                    .background(colors.accent, KioskTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(icon = KioskIcons.Alert, tint = colors.surface, modifier = Modifier.size(48.dpx))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dpx),
        ) {
            KioskText(
                text = stringResource(copy.title),
                style =
                    KioskTheme.typography.section.copy(
                        fontSize = 40.spx,
                        letterSpacing = TIGHT_EM.em,
                        lineHeight = PANEL_TITLE_LINE_HEIGHT_EM.em,
                    ),
            )
            KioskText(text = stringResource(copy.body), style = KioskTheme.typography.body, color = colors.errorText)
            if (error is SignInError.NotRegistered) SerialChip(serialNumber = error.serialNumber)
        }
    }
}

@Composable
private fun SerialChip(serialNumber: String) {
    val colors = KioskTheme.colors
    Row(
        modifier =
            Modifier
                .padding(top = 12.dpx)
                .height(96.dpx)
                .background(colors.surface, KioskTheme.shapes.medium)
                .padding(horizontal = 32.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dpx),
    ) {
        KioskText(
            text = stringResource(R.string.sign_in_error_serial_label),
            style = KioskTheme.typography.meta,
            color = colors.muted,
        )
        KioskText(
            text = serialNumber,
            style =
                KioskTheme.typography.section.copy(
                    fontWeight = FontWeight.W800,
                    letterSpacing = SERIAL_EM.em,
                ),
        )
    }
}

@Composable
private fun SignInDock(
    state: SignInUiState,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dock = animateEntrance(SignIn.DOCK_MILLIS, SignIn.DOCK_DELAY_MILLIS)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 72.dpx, end = 72.dpx, bottom = 48.dpx)
                .fadeUp(dock, SignIn.DOCK_DISTANCE_PX.dpx),
        verticalArrangement = Arrangement.spacedBy(32.dpx),
    ) {
        PrimaryCta(
            text = stringResource(state.button.label()),
            onClick = onLogIn,
            enabled = state.buttonEnabled,
            busy = state.busy,
            locked = !state.buttonEnabled && state.error is SignInError.SerialUnreadable,
            modifier = Modifier.testTag(SIGN_IN_BUTTON_TAG),
        )
        KioskText(
            text = stringResource(if (state.error == null) R.string.sign_in_help else R.string.sign_in_help_error),
            style = KioskTheme.typography.meta,
            color = KioskTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dpx),
        )
    }
}

/** Title and body resources of one error panel (FR-002, FR-003). */
private data class ErrorCopy(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
)

private fun SignInError.copy(): ErrorCopy =
    when (this) {
        is SignInError.NotRegistered -> {
            ErrorCopy(R.string.sign_in_error_not_registered_title, R.string.sign_in_error_not_registered_body)
        }

        SignInError.Disabled -> {
            ErrorCopy(R.string.sign_in_error_disabled_title, R.string.sign_in_error_disabled_body)
        }

        SignInError.Network -> {
            ErrorCopy(R.string.sign_in_error_network_title, R.string.sign_in_error_network_body)
        }

        SignInError.SerialUnreadable -> {
            ErrorCopy(R.string.sign_in_error_serial_unreadable_title, R.string.sign_in_error_serial_unreadable_body)
        }

        SignInError.Generic -> {
            ErrorCopy(R.string.sign_in_error_generic_title, R.string.sign_in_error_generic_body)
        }
    }

@StringRes
private fun SignInButton.label(): Int =
    when (this) {
        SignInButton.LOG_IN -> R.string.sign_in_button_log_in
        SignInButton.SIGNING_IN -> R.string.sign_in_button_signing_in
        SignInButton.TRY_AGAIN -> R.string.sign_in_button_try_again
    }

/** Sample states for previews and screenshot tests (`Login.dc.html` scenarios). */
internal object SignInSamples {
    const val VERSION = "0.2.0"
    const val SERIAL_NUMBER = "IMN24K0003118"

    val Default = SignInUiState(versionName = VERSION, button = SignInButton.LOG_IN, buttonEnabled = true, busy = false)
    val Busy =
        SignInUiState(versionName = VERSION, button = SignInButton.SIGNING_IN, buttonEnabled = false, busy = true)
    val NotRegistered = tryAgain(SignInError.NotRegistered(SERIAL_NUMBER))
    val Network = tryAgain(SignInError.Network)
    val Disabled = tryAgain(SignInError.Disabled)
    val Generic = tryAgain(SignInError.Generic)
    val SerialUnreadable =
        SignInUiState(
            versionName = VERSION,
            button = SignInButton.LOG_IN,
            buttonEnabled = false,
            busy = false,
            error = SignInError.SerialUnreadable,
        )

    private fun tryAgain(error: SignInError) =
        SignInUiState(
            versionName = VERSION,
            button = SignInButton.TRY_AGAIN,
            buttonEnabled = true,
            busy = false,
            error = error,
        )
}

@Composable
private fun SignInPreview(state: SignInUiState) {
    KioskTheme(reduceMotion = true) {
        DesignFrame { SignInScreen(state = state, onEvent = {}) }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun SignInDefaultPreview() = SignInPreview(SignInSamples.Default)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun SignInBusyPreview() = SignInPreview(SignInSamples.Busy)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun SignInNotRegisteredPreview() = SignInPreview(SignInSamples.NotRegistered)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun SignInNetworkPreview() = SignInPreview(SignInSamples.Network)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun SignInSerialUnreadablePreview() = SignInPreview(SignInSamples.SerialUnreadable)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun SignInDisabledPreview() = SignInPreview(SignInSamples.Disabled)
