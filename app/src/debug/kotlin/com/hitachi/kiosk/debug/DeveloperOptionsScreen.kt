package com.hitachi.kiosk.debug

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.component.KioskText
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import com.hitachi.kiosk.core.designsystem.unit.dpx
import com.hitachi.kiosk.core.testing.fake.FakeScenario
import com.hitachi.kiosk.core.testing.fixture.SampleData

/** Build facts shown at the top of Developer options. */
@Immutable
data class BuildInfo(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val environment: String,
    val baseUrl: String,
    val mockBaseUrl: String,
)

/** The serial-number diagnostics actions (DEV-012). */
@Immutable
data class SerialActions(
    val onRefresh: () -> Unit,
    val onClearCache: () -> Unit,
    val onCopy: () -> Unit,
    val onShare: () -> Unit,
)

/**
 * Stateless Developer options screen (DEV-009): build info, backend mode, fake scenario, the serial-number source
 * and the DEV-012 serial-number diagnostics.
 */
@Composable
fun DeveloperOptionsScreen(
    build: BuildInfo,
    settings: DeveloperSettings,
    serial: SerialDiagnostics?,
    onBackendModeChange: (BackendMode) -> Unit,
    onScenarioChange: (FakeScenario) -> Unit,
    onIdentityModeChange: (IdentityMode) -> Unit,
    onTerminalModeChange: (TerminalMode) -> Unit,
    serialActions: SerialActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KioskTheme.colors
    val typography = KioskTheme.typography
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 72.dpx, vertical = 48.dpx),
        verticalArrangement = Arrangement.spacedBy(24.dpx),
    ) {
        KioskText(text = stringResource(R.string.dev_options_title), style = typography.title, color = colors.ink)

        KioskText(text = stringResource(R.string.dev_options_build), style = typography.section, color = colors.ink)
        KioskText(
            text = stringResource(R.string.dev_options_build_version, build.versionName, build.versionCode),
            style = typography.body,
        )
        KioskText(text = stringResource(R.string.dev_options_build_type, build.buildType), style = typography.body)
        KioskText(text = stringResource(R.string.dev_options_build_env, build.environment), style = typography.body)
        KioskText(text = stringResource(R.string.dev_options_build_url, build.baseUrl), style = typography.body)

        KioskText(text = stringResource(R.string.dev_options_backend), style = typography.section, color = colors.ink)
        Choice(
            label = R.string.dev_options_backend_fakes,
            hint = stringResource(R.string.dev_options_backend_fakes_hint),
            selected = settings.backendMode == BackendMode.FAKES,
        ) { onBackendModeChange(BackendMode.FAKES) }
        Choice(
            label = R.string.dev_options_backend_mock,
            hint = stringResource(R.string.dev_options_backend_mock_hint, build.mockBaseUrl),
            selected = settings.backendMode == BackendMode.MOCK_SERVER,
        ) { onBackendModeChange(BackendMode.MOCK_SERVER) }
        Choice(
            label = R.string.dev_options_backend_real,
            hint = stringResource(R.string.dev_options_backend_real_hint, build.baseUrl),
            selected = settings.backendMode == BackendMode.REAL,
        ) { onBackendModeChange(BackendMode.REAL) }

        KioskText(text = stringResource(R.string.dev_options_scenario), style = typography.section, color = colors.ink)
        KioskText(
            text = stringResource(R.string.dev_options_scenario_hint),
            style = typography.meta,
            color = colors.muted,
        )
        FakeScenario.entries.forEach { scenario ->
            Choice(
                text = scenario.id,
                hint = null,
                selected = settings.scenario == scenario,
            ) { onScenarioChange(scenario) }
        }

        KioskText(text = stringResource(R.string.dev_options_identity), style = typography.section, color = colors.ink)
        Choice(
            label = R.string.dev_options_identity_fake,
            hint = stringResource(R.string.dev_options_identity_fake_hint, SampleData.SERIAL_NUMBER),
            selected = settings.identityMode == IdentityMode.FAKE,
        ) { onIdentityModeChange(IdentityMode.FAKE) }
        Choice(
            label = R.string.dev_options_identity_unavailable,
            hint = stringResource(R.string.dev_options_identity_unavailable_hint),
            selected = settings.identityMode == IdentityMode.FAKE_UNAVAILABLE,
        ) { onIdentityModeChange(IdentityMode.FAKE_UNAVAILABLE) }
        Choice(
            label = R.string.dev_options_identity_slow,
            hint = stringResource(R.string.dev_options_identity_slow_hint, SampleData.SERIAL_NUMBER),
            selected = settings.identityMode == IdentityMode.FAKE_SLOW,
        ) { onIdentityModeChange(IdentityMode.FAKE_SLOW) }
        Choice(
            label = R.string.dev_options_identity_real,
            hint = stringResource(R.string.dev_options_identity_real_hint),
            selected = settings.identityMode == IdentityMode.REAL,
        ) { onIdentityModeChange(IdentityMode.REAL) }

        TerminalSection(mode = settings.terminalMode, onModeChange = onTerminalModeChange)

        SerialDiagnosticsSection(
            diagnostics = serial,
            onRefresh = serialActions.onRefresh,
            onClearCache = serialActions.onClearCache,
            onCopy = serialActions.onCopy,
            onShare = serialActions.onShare,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dpx)
                    .clip(KioskTheme.shapes.pill)
                    .background(colors.ink)
                    .selectable(selected = false, role = Role.Button, onClick = onClose)
                    .padding(horizontal = 40.dpx, vertical = 24.dpx),
            contentAlignment = Alignment.CenterStart,
        ) {
            KioskText(text = stringResource(R.string.dev_options_close), style = typography.button, color = colors.bg)
        }
    }
}

/** The card terminal source of Checkout (FR-018, DEV-049): one choice per [TerminalMode]. */
@Composable
private fun TerminalSection(
    mode: TerminalMode,
    onModeChange: (TerminalMode) -> Unit,
) {
    val colors = KioskTheme.colors
    KioskText(
        text = stringResource(R.string.dev_options_terminal),
        style = KioskTheme.typography.section,
        color = colors.ink,
    )
    KioskText(
        text = stringResource(R.string.dev_options_terminal_hint),
        style = KioskTheme.typography.meta,
        color = colors.muted,
    )
    listOf(
        Triple(TerminalMode.MANUAL, R.string.dev_options_terminal_manual, R.string.dev_options_terminal_manual_hint),
        Triple(TerminalMode.APPROVE, R.string.dev_options_terminal_approve, R.string.dev_options_terminal_approve_hint),
        Triple(TerminalMode.DECLINE, R.string.dev_options_terminal_decline, R.string.dev_options_terminal_decline_hint),
        Triple(
            TerminalMode.CUSTOMER_CANCEL,
            R.string.dev_options_terminal_cancel,
            R.string.dev_options_terminal_cancel_hint,
        ),
        Triple(TerminalMode.ERROR, R.string.dev_options_terminal_error, R.string.dev_options_terminal_error_hint),
        Triple(
            TerminalMode.NO_ANSWER,
            R.string.dev_options_terminal_no_answer,
            R.string.dev_options_terminal_no_answer_hint,
        ),
        Triple(TerminalMode.REAL, R.string.dev_options_terminal_real, R.string.dev_options_terminal_real_hint),
    ).forEach { (option, label, hint) ->
        Choice(label = label, hint = stringResource(hint), selected = mode == option) { onModeChange(option) }
    }
}

@Composable
private fun Choice(
    @StringRes label: Int,
    hint: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Choice(text = stringResource(label), hint = hint, selected = selected, onSelect = onSelect)
}

@Composable
private fun Choice(
    text: String,
    hint: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = KioskTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dpx)
                .clip(KioskTheme.shapes.card)
                .background(if (selected) colors.ink else colors.surface)
                .border(width = 2.dpx, color = colors.cardBorder, shape = KioskTheme.shapes.card)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 40.dpx, vertical = 24.dpx),
        verticalArrangement = Arrangement.spacedBy(8.dpx),
    ) {
        KioskText(text = text, style = KioskTheme.typography.item, color = if (selected) colors.bg else colors.ink)
        if (hint != null) {
            KioskText(
                text = hint,
                style = KioskTheme.typography.meta,
                color = if (selected) colors.bg else colors.muted,
            )
        }
    }
}

@Preview(widthDp = 1080, heightDp = 1920)
@Composable
private fun DeveloperOptionsScreenPreview() {
    KioskTheme {
        DesignFrame {
            DeveloperOptionsScreen(
                build =
                    BuildInfo(
                        "0.1.0",
                        100,
                        "debug",
                        "dev",
                        "https://dev-api.hitachikiosk.example/",
                        "http://10.0.2.2:4010/",
                    ),
                settings = DeveloperSettings(),
                serial =
                    SerialDiagnostics(
                        listOf(
                            DiagnosticRow("IminDeviceIdentityProvider.identify()", "\"IMN24K0003118\""),
                            DiagnosticRow("prop:ro.serialno", "<blank>"),
                            DiagnosticRow("prop:persist.sys.imin.sn", "\"IMN24K0003118\""),
                        ),
                    ),
                onBackendModeChange = {},
                onScenarioChange = {},
                onIdentityModeChange = {},
                onTerminalModeChange = {},
                serialActions = SerialActions(onRefresh = {}, onClearCache = {}, onCopy = {}, onShare = {}),
                onClose = {},
            )
        }
    }
}
