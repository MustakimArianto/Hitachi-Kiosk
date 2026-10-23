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

/**
 * Where the serial number comes from in a debug build (FR-002, DEV-012): the real reader, or the fake answering the
 * sample SN, failing ("serial_unavailable") or answering after 5 s (FR-002 AC3).
 */
enum class IdentityMode { REAL, FAKE, FAKE_UNAVAILABLE, FAKE_SLOW }

/**
 * The Developer options choices (DEV-009). Debug builds start on the fakes with the happy path and the fake serial
 * number, so an emulator works out of the box.
 */
data class DeveloperSettings(
    val backendMode: BackendMode = BackendMode.FAKES,
    val scenario: FakeScenario = FakeScenario.HAPPY_PATH,
    val identityMode: IdentityMode = IdentityMode.FAKE,
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
                        identityMode =
                            prefs[IDENTITY_MODE]?.let { runCatching { IdentityMode.valueOf(it) }.getOrNull() }
                                ?: IdentityMode.FAKE,
                    )
                }.stateIn(scope, SharingStarted.Eagerly, DeveloperSettings())

        suspend fun setBackendMode(mode: BackendMode) {
            dataStore.edit { it[BACKEND_MODE] = mode.name }
        }

        suspend fun setScenario(scenario: FakeScenario) {
            dataStore.edit { it[SCENARIO] = scenario.id }
        }

        suspend fun setIdentityMode(mode: IdentityMode) {
            dataStore.edit { it[IDENTITY_MODE] = mode.name }
        }

        private companion object {
            val BACKEND_MODE = stringPreferencesKey("backend_mode")
            val SCENARIO = stringPreferencesKey("scenario")
            val IDENTITY_MODE = stringPreferencesKey("identity_mode")
        }
    }
