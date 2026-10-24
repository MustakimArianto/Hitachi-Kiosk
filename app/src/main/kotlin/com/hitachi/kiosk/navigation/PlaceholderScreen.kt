package com.hitachi.kiosk.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx

/** One navigation edge shown as a button on a placeholder screen (DEV-010a). */
@Immutable
data class PlaceholderAction(
    @param:StringRes val label: Int,
    val onClick: () -> Unit,
)

/**
 * The labelled placeholder every route shows until its milestone builds the real screen (DEV-010a). It names the
 * screen ID and offers one button per outgoing edge of PRD §5.1. Built from design tokens only.
 */
@Composable
fun PlaceholderScreen(
    screenId: String,
    @StringRes title: Int,
    actions: List<PlaceholderAction>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onMarkClick: (() -> Unit)? = null,
) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    val markDescription = stringResource(R.string.placeholder_developer_mark)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 72.dpx, vertical = 48.dpx)
                .testTag(screenTag(screenId)),
        verticalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        Box(
            modifier =
                Modifier
                    .size(96.dpx)
                    .clip(KioskTheme.shapes.card)
                    .background(colors.ink)
                    .semantics { contentDescription = markDescription }
                    .then(if (onMarkClick != null) Modifier.clickable(onClick = onMarkClick) else Modifier)
                    .testTag(MARK_TAG),
        )
        KioskText(text = screenId, style = typography.meta, color = colors.muted)
        KioskText(text = stringResource(title), style = typography.title, color = colors.ink)
        if (subtitle != null) {
            KioskText(text = subtitle, style = typography.section, color = colors.ink)
        }
        KioskText(text = stringResource(R.string.placeholder_caption), style = typography.body, color = colors.muted)
        actions.forEach { action -> PlaceholderButton(action) }
    }
}

@Composable
private fun PlaceholderButton(action: PlaceholderAction) {
    val colors = KioskTheme.colors
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dpx)
                .clip(KioskTheme.shapes.pill)
                .background(colors.ink)
                .clickable(role = Role.Button, onClick = action.onClick)
                .padding(horizontal = 40.dpx, vertical = 24.dpx),
        contentAlignment = Alignment.CenterStart,
    ) {
        KioskText(text = stringResource(action.label), style = KioskTheme.typography.button, color = colors.bg)
    }
}

/** Test tag of a placeholder's root, used by the edge-walk test. */
fun screenTag(screenId: String): String = "screen:$screenId"

/** Test tag of the brand mark that opens Developer options in debug builds (DEV-009). */
const val MARK_TAG = "brand-mark"

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun PlaceholderScreenPreview() {
    KioskTheme {
        DesignFrame {
            PlaceholderScreen(
                screenId = "SCR-10",
                title = R.string.screen_your_order,
                actions =
                    listOf(
                        PlaceholderAction(R.string.nav_add_more) {},
                        PlaceholderAction(R.string.nav_simulate_inactivity) {},
                    ),
            )
        }
    }
}
