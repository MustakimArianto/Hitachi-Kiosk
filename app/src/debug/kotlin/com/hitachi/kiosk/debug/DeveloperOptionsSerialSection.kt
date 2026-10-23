package com.hitachi.kiosk.debug

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.dpx

/**
 * The DEV-012 serial-number spike (ADR-0002): every source's raw result, the cache, the production provider and the
 * device facts, with actions to read again, clear the cache (TC-006), and copy or share the results.
 */
@Composable
fun SerialDiagnosticsSection(
    diagnostics: SerialDiagnostics?,
    onRefresh: () -> Unit,
    onClearCache: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dpx)) {
        KioskText(text = stringResource(R.string.dev_options_serial), style = typography.section, color = colors.ink)
        KioskText(
            text = stringResource(R.string.dev_options_serial_hint),
            style = typography.meta,
            color = colors.muted,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(KioskTheme.shapes.card)
                    .background(colors.surface)
                    .border(width = 2.dpx, color = colors.cardBorder, shape = KioskTheme.shapes.card)
                    .padding(horizontal = 40.dpx, vertical = 24.dpx),
            verticalArrangement = Arrangement.spacedBy(16.dpx),
        ) {
            if (diagnostics == null) {
                KioskText(text = stringResource(R.string.dev_options_serial_loading), style = typography.body)
            } else {
                diagnostics.rows.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dpx)) {
                        KioskText(text = row.key, style = typography.meta, color = colors.muted)
                        KioskText(text = row.value, style = typography.body, color = colors.ink)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dpx)) {
            ActionButton(R.string.dev_options_serial_refresh, onRefresh, Modifier.weight(1f))
            ActionButton(R.string.dev_options_serial_clear, onClearCache, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dpx)) {
            ActionButton(R.string.dev_options_serial_copy, onCopy, Modifier.weight(1f), enabled = diagnostics != null)
            ActionButton(R.string.dev_options_serial_share, onShare, Modifier.weight(1f), enabled = diagnostics != null)
        }
    }
}

@Composable
private fun ActionButton(
    @StringRes label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = KioskTheme.colors
    Box(
        modifier =
            modifier
                .heightIn(min = 96.dpx)
                .clip(KioskTheme.shapes.pill)
                .background(if (enabled) colors.ink else colors.muted)
                .selectable(selected = false, enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 40.dpx, vertical = 24.dpx),
        contentAlignment = Alignment.Center,
    ) {
        KioskText(text = stringResource(label), style = KioskTheme.typography.button, color = colors.bg)
    }
}
