package com.hitachi.kiosk.feature.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.BrandLockup
import com.hitachi.kiosk.core.designsystem.component.KioskIcon
import com.hitachi.kiosk.core.designsystem.component.KioskIcons
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.motion.KioskEasing
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Common
import com.hitachi.kiosk.core.designsystem.motion.KioskMotion.Merchants
import com.hitachi.kiosk.core.designsystem.motion.animateEntrance
import com.hitachi.kiosk.core.designsystem.motion.animatePressScale
import com.hitachi.kiosk.core.designsystem.motion.fadeIn
import com.hitachi.kiosk.core.designsystem.motion.fadeUp
import com.hitachi.kiosk.core.designsystem.motion.pressScale
import com.hitachi.kiosk.core.designsystem.motion.riseIn
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.designsystem.unit.spx

/** Test tag of the Merchants root. */
const val MERCHANTS_SCREEN_TAG = "screen:SCR-03"

/** Test tag of the header "Log out" pill. */
const val MERCHANTS_LOG_OUT_TAG = "merchants-log-out"

/** Test tag of "Retry" on the failure panel. */
const val MERCHANTS_RETRY_TAG = "merchants-retry"

/** Test tag of "Log out" on the failure panel. */
const val MERCHANTS_FAILURE_LOG_OUT_TAG = "merchants-failure-log-out"

/** Test tag of "Refresh list" in the empty state. */
const val MERCHANTS_REFRESH_TAG = "merchants-refresh"

/** Test tag of a live (open or closed) merchant card. */
fun merchantCardTag(id: String): String = "merchant:$id"

/** Test tag of a coming-soon row. */
fun comingSoonRowTag(id: String): String = "soon:$id"

/** Height of the footer band (caption, its 56 dpx bottom margin and the fade above it), in design px. */
private const val FOOTER_BAND_PX = 152

/** Height of the fade at the top of the footer band, in design px. */
private const val FOOTER_FADE_PX = 48

/** Letter spacing of the empty and failure titles (−0.02 em). */
private const val TIGHT_EM = -0.02f
private const val PANEL_TITLE_LINE_HEIGHT_EM = 1.1f
private const val EMPTY_TITLE_LINE_HEIGHT_EM = 1.05f

/**
 * SCR-03 Choose a restaurant (`Merchants.dc.html`, `S_Merchants_*`, FR-004, DEV-015). Draws [state] exactly. The
 * body below the title scrolls when it does not fit, and the footer caption stays fixed at the bottom above a fade.
 */
@Composable
fun MerchantsScreen(
    state: MerchantsUiState,
    onEvent: (MerchantsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(KioskTheme.colors.bg)
                .testTag(MERCHANTS_SCREEN_TAG),
    ) {
        Column(Modifier.fillMaxSize()) {
            MerchantsHeader(logOutEnabled = state.logOutEnabled, onLogOut = { onEvent(MerchantsEvent.LogOut) })
            MerchantsTitle(Modifier.padding(start = 72.dpx, end = 72.dpx, top = 72.dpx))
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 72.dpx, end = 72.dpx, top = 80.dpx, bottom = (FOOTER_BAND_PX + 24).dpx),
                verticalArrangement = Arrangement.spacedBy(80.dpx),
            ) {
                MerchantsBody(state = state, onEvent = onEvent)
            }
        }
        MerchantsFooter(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun MerchantsBody(
    state: MerchantsUiState,
    onEvent: (MerchantsEvent) -> Unit,
) {
    when (val content = state.content) {
        MerchantsContent.Loading -> {
            MerchantsSkeleton()
        }

        MerchantsContent.Empty -> {
            EmptyCard(onRefresh = { onEvent(MerchantsEvent.Retry) })
        }

        is MerchantsContent.Failed -> {
            FailurePanel(
                failure = content,
                logOutEnabled = state.logOutEnabled,
                onRetry = { onEvent(MerchantsEvent.Retry) },
                onLogOut = { onEvent(MerchantsEvent.LogOut) },
            )
        }

        is MerchantsContent.Loaded -> {
            MerchantsList(content = content, onSelect = { onEvent(MerchantsEvent.Select(it)) })
        }
    }
}

@Composable
private fun MerchantsHeader(
    logOutEnabled: Boolean,
    onLogOut: () -> Unit,
) {
    val colors = KioskTheme.colors
    val fade = animateEntrance(Common.HEADER_FADE_MILLIS, easing = KioskEasing.Ease)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = animatePressScale(interactionSource)
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
        BrandLockup(name = stringResource(R.string.brand_name))
        Row(
            modifier =
                Modifier
                    .height(96.dpx)
                    .pressScale(scale)
                    .clip(KioskTheme.shapes.pill)
                    .border(2.dpx, colors.line, KioskTheme.shapes.pill)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = logOutEnabled,
                        role = Role.Button,
                        onClick = onLogOut,
                    ).testTag(MERCHANTS_LOG_OUT_TAG)
                    .padding(start = 28.dpx, end = 36.dpx),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dpx),
        ) {
            KioskIcon(icon = KioskIcons.LogOut, tint = colors.ink, modifier = Modifier.size(36.dpx))
            KioskText(
                text = stringResource(R.string.merchants_log_out),
                style = KioskTheme.typography.meta.copy(fontSize = 28.spx, fontWeight = FontWeight.W600),
            )
        }
    }
}

@Composable
private fun MerchantsTitle(modifier: Modifier = Modifier) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val step = animateEntrance(Merchants.STEP_UP_MILLIS, Merchants.STEP_UP_DELAY_MILLIS)
    val title = animateEntrance(Merchants.TITLE_RISE_MILLIS, Merchants.TITLE_RISE_DELAY_MILLIS)
    val subtitle = animateEntrance(Merchants.SUBTITLE_UP_MILLIS, Merchants.SUBTITLE_UP_DELAY_MILLIS)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(24.dpx)) {
        KioskText(
            text = stringResource(R.string.merchants_step),
            style = typography.meta,
            color = colors.muted,
            modifier = Modifier.fadeUp(step, Common.UP_DISTANCE_PX.dpx),
        )
        Box(
            modifier =
                Modifier
                    .clipToBounds()
                    .padding(bottom = 8.dpx),
        ) {
            KioskText(
                text = stringResource(R.string.merchants_title),
                style = typography.title.copy(lineHeight = 1.em),
                modifier = Modifier.riseIn(title),
                cssLineBox = true,
            )
        }
        KioskText(
            text = stringResource(R.string.merchants_subtitle),
            style = typography.body,
            color = colors.muted,
            modifier = Modifier.fadeUp(subtitle, Common.UP_DISTANCE_PX.dpx),
        )
    }
}

@Composable
private fun EmptyCard(onRefresh: () -> Unit) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val enter = animateEntrance(Common.PANEL_UP_MILLIS)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fadeUp(enter, Common.UP_DISTANCE_PX.dpx)
                .background(colors.surface, shape)
                .border(2.dpx, colors.line, shape)
                .padding(horizontal = 48.dpx, vertical = 72.dpx),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dpx),
    ) {
        Box(
            modifier =
                Modifier
                    .size(160.dpx)
                    .background(colors.surfaceAlt, KioskTheme.shapes.dock),
            contentAlignment = Alignment.Center,
        ) {
            KioskIcon(icon = KioskIcons.Store, tint = colors.muted, modifier = Modifier.size(80.dpx))
        }
        KioskText(
            text = stringResource(R.string.merchants_empty_title),
            style =
                KioskTheme.typography.title.copy(
                    fontSize = 52.spx,
                    letterSpacing = TIGHT_EM.em,
                    lineHeight = EMPTY_TITLE_LINE_HEIGHT_EM.em,
                ),
            textAlign = TextAlign.Center,
        )
        KioskText(
            text = stringResource(R.string.merchants_empty_body),
            style = KioskTheme.typography.body,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 60.dpx),
        )
        PanelButton(
            text = stringResource(R.string.merchants_empty_refresh),
            onClick = onRefresh,
            style = PanelButtonStyle.Dark,
            modifier =
                Modifier
                    .padding(top = 16.dpx)
                    .height(112.dpx)
                    .testTag(MERCHANTS_REFRESH_TAG),
        )
    }
}

@Composable
private fun FailurePanel(
    failure: MerchantsContent.Failed,
    logOutEnabled: Boolean,
    onRetry: () -> Unit,
    onLogOut: () -> Unit,
) {
    val colors = KioskTheme.colors
    val shape = KioskTheme.shapes.card
    val enter = animateEntrance(Common.PANEL_UP_MILLIS)
    val merchant = failure.merchantName ?: stringResource(R.string.merchants_failure_merchant_fallback)
    val (title, body) =
        when (failure.reason) {
            MerchantsFailure.BIND -> {
                stringResource(R.string.merchants_failure_bind_title) to
                    stringResource(R.string.merchants_failure_bind_body, merchant)
            }

            MerchantsFailure.MENU_DOWNLOAD -> {
                stringResource(R.string.merchants_failure_menu_download_title) to
                    stringResource(R.string.merchants_failure_menu_download_body, merchant)
            }

            MerchantsFailure.LOAD -> {
                stringResource(R.string.merchants_failure_load_title) to
                    stringResource(R.string.merchants_failure_load_body)
            }
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fadeUp(enter, Common.UP_DISTANCE_PX.dpx)
                .background(colors.errorBg, shape)
                .border(2.dpx, colors.errorBorder, shape)
                .padding(48.dpx),
        verticalArrangement = Arrangement.spacedBy(28.dpx),
    ) {
        Column(
            modifier = Modifier.alertSemantics(),
            verticalArrangement = Arrangement.spacedBy(28.dpx),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dpx)) {
                Box(
                    modifier =
                        Modifier
                            .size(96.dpx)
                            .background(colors.accent, RoundedCornerShape(30.dpx)),
                    contentAlignment = Alignment.Center,
                ) {
                    KioskIcon(icon = KioskIcons.Alert, tint = colors.surface, modifier = Modifier.size(52.dpx))
                }
                KioskText(
                    text = title,
                    style =
                        KioskTheme.typography.section.copy(
                            letterSpacing = TIGHT_EM.em,
                            lineHeight = PANEL_TITLE_LINE_HEIGHT_EM.em,
                        ),
                )
            }
            KioskText(text = body, style = KioskTheme.typography.body, color = colors.errorText)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dpx)) {
            PanelButton(
                text = stringResource(R.string.merchants_retry),
                onClick = onRetry,
                style = PanelButtonStyle.Accent,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(120.dpx)
                        .testTag(MERCHANTS_RETRY_TAG),
            )
            PanelButton(
                text = stringResource(R.string.merchants_log_out),
                onClick = onLogOut,
                style = PanelButtonStyle.Outline,
                enabled = logOutEnabled,
                modifier =
                    Modifier
                        .height(120.dpx)
                        .testTag(MERCHANTS_FAILURE_LOG_OUT_TAG),
            )
        }
    }
}

@Composable
private fun MerchantsFooter(modifier: Modifier = Modifier) {
    val bg = KioskTheme.colors.bg
    val fade = animateEntrance(Merchants.FOOTER_FADE_MILLIS, Merchants.FOOTER_FADE_DELAY_MILLIS, KioskEasing.Ease)
    val fadeHeight = FOOTER_FADE_PX.dpx
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(FOOTER_BAND_PX.dpx)
                .background(
                    Brush.verticalGradient(
                        0f to bg.copy(alpha = 0f),
                        (FOOTER_FADE_PX.toFloat() / FOOTER_BAND_PX) to bg,
                        1f to bg,
                    ),
                ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        KioskText(
            text = stringResource(R.string.merchants_footer),
            style = KioskTheme.typography.meta.copy(fontSize = 24.spx),
            color = KioskTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dpx, end = 72.dpx, bottom = 56.dpx, top = fadeHeight)
                    .fadeIn(fade),
        )
    }
}

@Composable
private fun MerchantsPreview(state: MerchantsUiState) {
    KioskTheme(reduceMotion = true) {
        DesignFrame { MerchantsScreen(state = state, onEvent = {}) }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun MerchantsListPreview() = MerchantsPreview(MerchantsSamples.Default)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun MerchantsSkeletonPreview() = MerchantsPreview(MerchantsUiState(MerchantsContent.Loading))

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun MerchantsEmptyPreview() = MerchantsPreview(MerchantsUiState(MerchantsContent.Empty))

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun MerchantsBindFailedPreview() = MerchantsPreview(MerchantsSamples.BindFailed)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun MerchantsClosedPreview() = MerchantsPreview(MerchantsSamples.Closed)

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun MerchantsBindingPreview() = MerchantsPreview(MerchantsSamples.Binding)
