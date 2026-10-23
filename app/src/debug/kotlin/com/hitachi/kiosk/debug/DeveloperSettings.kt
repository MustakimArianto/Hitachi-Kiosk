package com.hitachi.kiosk.debug

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hitachi.kiosk.core.testing.fake.FakeScenario
import com.hitachi.kiosk.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Where backend calls go in a debug build (technical-design §4.3). */
enum class BackendMode { FAKES, MOCK_SERVER, REAL }

/** The Developer options choices (DEV-009). Debug builds start on the fakes with the happy path. */
data class DeveloperSettings(
    val backendMode: BackendMode = BackendMode.FAKES,
    val scenario: FakeScenario = FakeScenario.HAPPY_PATH,
)

private val Context.developerDataStore: DataStore<Preferences> by preferencesDataStore(name = "developer_options")

/** Persists [DeveloperSettings] in a debug-only DataStore file. Reads never block the main thread. */
@Singleton
class DeveloperSettingsStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ) {
        private val dataStore = context.developerDataStore

        val settings: StateFlow<DeveloperSettings> =
            dataStore.data
                .map { prefs ->
                    DeveloperSettings(
                        backendMode =
                            prefs[BACKEND_MODE]?.let { runCatching { BackendMode.valueOf(it) }.getOrNull() }
                                ?: BackendMode.FAKES,
                        scenario = prefs[SCENARIO]?.let(FakeScenario::fromId) ?: FakeScenario.HAPPY_PATH,
                    )
                }.stateIn(scope, SharingStarted.Eagerly, DeveloperSettings())

        suspend fun setBackendMode(mode: BackendMode) {
            dataStore.edit { it[BACKEND_MODE] = mode.name }
        }

        suspend fun setScenario(scenario: FakeScenario) {
            dataStore.edit { it[SCENARIO] = scenario.id }
        }

        private companion object {
            val BACKEND_MODE = stringPreferencesKey("backend_mode")
            val SCENARIO = stringPreferencesKey("scenario")
        }
    }
