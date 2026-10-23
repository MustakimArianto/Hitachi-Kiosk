package com.hitachi.kiosk.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitachi.kiosk.BuildConfig
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
            KioskTheme {
                DesignFrame {
                    DeveloperOptionsScreen(
                        build = build,
                        settings = settings,
                        onBackendModeChange = { mode -> scope.launch { store.setBackendMode(mode) } },
                        onScenarioChange = { scenario -> scope.launch { store.setScenario(scenario) } },
                        onClose = ::finish,
                    )
                }
            }
        }
    }
}
