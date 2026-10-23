package com.hitachi.kiosk.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitachi.kiosk.BuildConfig
import com.hitachi.kiosk.R
import com.hitachi.kiosk.core.designsystem.theme.KioskTheme
import com.hitachi.kiosk.core.designsystem.unit.DesignFrame
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Debug-only Developer options (DEV-009), opened with 5 taps on the splash mark. */
@AndroidEntryPoint
class DeveloperOptionsActivity : ComponentActivity() {
    @Inject
    lateinit var store: DeveloperSettingsStore

    @Inject
    lateinit var serialDiagnostics: SerialDiagnosticsCollector

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val build =
            BuildInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                buildType = BuildConfig.BUILD_TYPE,
                environment = BuildConfig.KIOSK_ENV,
                baseUrl = BuildConfig.API_BASE_URL,
                mockBaseUrl = BuildConfig.MOCK_BASE_URL,
            )
        setContent {
            val settings by store.settings.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var serial by remember { mutableStateOf<SerialDiagnostics?>(null) }
            val refresh: () -> Unit = {
                scope.launch {
                    serial = null
                    serial = serialDiagnostics.collect(windowFacts())
                }
            }
            LaunchedEffect(Unit) { serial = serialDiagnostics.collect(windowFacts()) }
            KioskTheme {
                DesignFrame {
                    DeveloperOptionsScreen(
                        build = build,
                        settings = settings,
                        serial = serial,
                        onBackendModeChange = { mode -> scope.launch { store.setBackendMode(mode) } },
                        onScenarioChange = { scenario -> scope.launch { store.setScenario(scenario) } },
                        onIdentityModeChange = { mode -> scope.launch { store.setIdentityMode(mode) } },
                        serialActions =
                            SerialActions(
                                onRefresh = refresh,
                                onClearCache = {
                                    scope.launch {
                                        serialDiagnostics.clearCache()
                                        refresh()
                                    }
                                },
                                onCopy = { serial?.let { copy(it.report()) } },
                                onShare = { serial?.let { share(it.report()) } },
                            ),
                        onClose = ::finish,
                    )
                }
            }
        }
    }

    /** The window this Activity got, to check that `screenOrientation="portrait"` is honoured (technical-design §16). */
    private fun windowFacts(): WindowFacts {
        val config = resources.configuration
        val bounds = windowManager.currentWindowMetrics.bounds
        return WindowFacts(
            orientation =
                when (config.orientation) {
                    Configuration.ORIENTATION_PORTRAIT -> "portrait"
                    Configuration.ORIENTATION_LANDSCAPE -> "landscape"
                    else -> "undefined (${config.orientation})"
                },
            requestedOrientation = requestedOrientation,
            widthDp = config.screenWidthDp,
            heightDp = config.screenHeightDp,
            smallestWidthDp = config.smallestScreenWidthDp,
            widthPx = bounds.width(),
            heightPx = bounds.height(),
            densityDpi = config.densityDpi,
        )
    }

    private fun copy(report: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.dev_options_serial_share_title), report))
    }

    private fun share(report: String) {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.dev_options_serial_share_title))
                .putExtra(Intent.EXTRA_TEXT, report)
        startActivity(Intent.createChooser(send, getString(R.string.dev_options_serial_share_title)))
    }
}
