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
 * Where the EDC terminal's answers come from in a debug build (FR-018, DEV-049): the release adapter (no EDC adapter
 * exists before 0.9.0, so every sale fails), a terminal that waits for the Checkout simulate bar ([MANUAL]), or the
 * [com.hitachi.kiosk.core.testing.fake.FakeCardTerminal] answering by itself after 2 s.
 */
enum class TerminalMode { MANUAL, APPROVE, DECLINE, CUSTOMER_CANCEL, ERROR, NO_ANSWER, REAL }

/**
 * The Developer options choices (DEV-009). Debug builds start on the fakes with the happy path, the fake serial
 * number and a card terminal that waits for the simulate bar, so an emulator works out of the box.
 */
data class DeveloperSettings(
    val backendMode: BackendMode = BackendMode.FAKES,
    val scenario: FakeScenario = FakeScenario.HAPPY_PATH,
    val identityMode: IdentityMode = IdentityMode.FAKE,
    val terminalMode: TerminalMode = TerminalMode.MANUAL,
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
                        terminalMode =
                            prefs[TERMINAL_MODE]?.let { runCatching { TerminalMode.valueOf(it) }.getOrNull() }
                                ?: TerminalMode.MANUAL,
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

        suspend fun setTerminalMode(mode: TerminalMode) {
            dataStore.edit { it[TERMINAL_MODE] = mode.name }
        }

        private companion object {
            val BACKEND_MODE = stringPreferencesKey("backend_mode")
            val SCENARIO = stringPreferencesKey("scenario")
            val IDENTITY_MODE = stringPreferencesKey("identity_mode")
            val TERMINAL_MODE = stringPreferencesKey("terminal_mode")
        }
    }
