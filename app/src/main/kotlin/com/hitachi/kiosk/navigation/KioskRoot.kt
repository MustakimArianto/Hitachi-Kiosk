package com.hitachi.kiosk.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx

/**
 * The root of the UI (technical-design §10.2): the design frame, the [KioskNavHost] and the SCR-13 inactivity overlay
 * above it. The overlay is a placeholder until DEV-040 adds the `InactivityController`.
 */
@Composable
fun KioskRoot(
    onBrandMarkClick: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    var stillThereVisible by rememberSaveable { mutableStateOf(false) }
    DesignFrame(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            KioskNavHost(
                navController = navController,
                onInactivity = { stillThereVisible = true },
                onBrandMarkClick = onBrandMarkClick,
            )
            if (stillThereVisible) {
                StillThereOverlayPlaceholder(
                    onStillHere = { stillThereVisible = false },
                    onNoResponse = {
                        stillThereVisible = false
                        navController.popBackStack(IdleRoute, false)
                    },
                )
            }
        }
    }
}

@Composable
private fun StillThereOverlayPlaceholder(
    onStillHere: () -> Unit,
    onNoResponse: () -> Unit,
) {
    val colors = KioskTheme.colors
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.ink.copy(alpha = SCRIM_ALPHA))
                // The scrim swallows touches so the screen below cannot be used.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .testTag(screenTag(STILL_THERE_ID)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 72.dpx)
                    .clip(KioskTheme.shapes.card)
                    .background(colors.surface)
                    .padding(48.dpx),
            verticalArrangement = Arrangement.spacedBy(24.dpx),
        ) {
            KioskText(text = STILL_THERE_ID, style = KioskTheme.typography.meta, color = colors.muted)
            KioskText(
                text = stringResource(R.string.screen_still_there),
                style = KioskTheme.typography.title,
                color = colors.ink,
            )
            OverlayButton(R.string.nav_still_here, onStillHere)
            OverlayButton(R.string.nav_no_response, onNoResponse)
        }
    }
}

@Composable
private fun OverlayButton(
    label: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dpx)
                .clip(KioskTheme.shapes.pill)
                .background(KioskTheme.colors.ink)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 40.dpx, vertical = 24.dpx),
        contentAlignment = Alignment.CenterStart,
    ) {
        KioskText(text = stringResource(label), style = KioskTheme.typography.button, color = KioskTheme.colors.bg)
    }
}

private const val STILL_THERE_ID = "SCR-13"
private const val SCRIM_ALPHA = 0.6f
