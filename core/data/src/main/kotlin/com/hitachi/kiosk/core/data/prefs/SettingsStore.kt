package com.hitachi.kiosk.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.hitachi.kiosk.core.data.mapper.enumOrNull
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The device settings in `settings.preferences_pb` (technical-design §6.2). A missing or unusable value reads as its
 * default, so a fresh or damaged file still gives a working kiosk. Keys not listed here (printers, brightness, …)
 * arrive with their settings screens in 0.6.0 and 0.7.0.
 */
internal class SettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<KioskSettings> = dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun update(transform: (KioskSettings) -> KioskSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings(): KioskSettings {
        val defaults = KioskSettings()
        val methods =
            this[PAYMENT_METHODS]?.mapNotNull { enumOrNull<PaymentMethod>(it) }?.toSet() ?: defaults.paymentMethods
        return KioskSettings(
            upsellEnabled = this[UPSELL_ENABLED] ?: defaults.upsellEnabled,
            idleTimeoutSeconds =
                this[IDLE_TIMEOUT_SEC]?.takeIf { it in KioskSettings.IDLE_TIMEOUT_CHOICES }
                    ?: defaults.idleTimeoutSeconds,
            paymentMethods = methods,
            roundingStep = this[ROUNDING_STEP]?.takeIf { it >= 0 },
        )
    }

    private fun MutablePreferences.write(settings: KioskSettings) {
        this[UPSELL_ENABLED] = settings.upsellEnabled
        this[IDLE_TIMEOUT_SEC] = settings.idleTimeoutSeconds
        this[PAYMENT_METHODS] = settings.paymentMethods.map { it.name }.toSet()
        val step = settings.roundingStep
        if (step == null) remove(ROUNDING_STEP) else this[ROUNDING_STEP] = step
    }

    private companion object {
        val UPSELL_ENABLED = booleanPreferencesKey("upsellEnabled")
        val IDLE_TIMEOUT_SEC = intPreferencesKey("idleTimeoutSec")
        val PAYMENT_METHODS = stringSetPreferencesKey("paymentMethods")
        val ROUNDING_STEP = longPreferencesKey("roundingStep")
    }
}
