package com.hitachi.kiosk.core.domain.repository

import com.hitachi.kiosk.core.domain.model.KioskSettings
import kotlinx.coroutines.flow.Flow

/** The device settings (architecture §6). Every change is visible to the next screen without a restart (FR-026). */
interface SettingsRepository {
    /** The current settings; defaults until staff change something. */
    val settings: Flow<KioskSettings>

    /** Changes the settings atomically. Staff settings (0.7.0) and tests write through this. */
    suspend fun update(transform: (KioskSettings) -> KioskSettings)
}
