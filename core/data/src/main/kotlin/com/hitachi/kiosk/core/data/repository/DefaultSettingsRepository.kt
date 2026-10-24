package com.hitachi.kiosk.core.data.repository

import com.hitachi.kiosk.core.data.prefs.SettingsStore
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** [SettingsRepository] on the [SettingsStore] (FR-025, FR-026, technical-design §6.2). */
internal class DefaultSettingsRepository
    @Inject
    constructor(
        private val store: SettingsStore,
    ) : SettingsRepository {
        override val settings: Flow<KioskSettings> = store.settings

        override suspend fun update(transform: (KioskSettings) -> KioskSettings) = store.update(transform)
    }
