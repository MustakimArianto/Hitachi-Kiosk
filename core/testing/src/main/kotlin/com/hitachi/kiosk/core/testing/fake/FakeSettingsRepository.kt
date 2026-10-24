package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A [SettingsRepository] in memory; a test sets [value] directly (FR-014, FR-015, FR-023). */
class FakeSettingsRepository(
    initial: KioskSettings = KioskSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<KioskSettings> = state.asStateFlow()

    var value: KioskSettings
        get() = state.value
        set(next) {
            state.value = next
        }

    override suspend fun update(transform: (KioskSettings) -> KioskSettings) {
        state.update(transform)
    }
}
