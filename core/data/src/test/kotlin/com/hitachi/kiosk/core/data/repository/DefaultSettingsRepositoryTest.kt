package com.hitachi.kiosk.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.model.KioskSettings
import com.hitachi.kiosk.core.domain.model.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(produceFile = { folder.root.resolve("settings.preferences_pb") })
    }
    private val repository by lazy { DefaultSettingsRepository(dataStore) }

    @Test
    fun `a fresh file reads as the FR-026 defaults`() =
        runTest {
            assertThat(repository.settings.first()).isEqualTo(KioskSettings())
        }

    @Test
    fun `changes are stored and read back`() =
        runTest {
            val changed =
                KioskSettings(
                    upsellEnabled = false,
                    idleTimeoutSeconds = 30,
                    paymentMethods = setOf(PaymentMethod.QRIS),
                    roundingStep = 500,
                )

            repository.update { changed }
            assertThat(repository.settings.first()).isEqualTo(changed)

            repository.update { it.copy(roundingStep = null) }
            assertThat(repository.settings.first().roundingStep).isNull()
        }

    @Test
    fun `unusable stored values fall back to their defaults`() =
        runTest {
            dataStore.edit {
                it[intPreferencesKey("idleTimeoutSec")] = 45
                it[stringSetPreferencesKey("paymentMethods")] = setOf("COUNTER", "CARD")
            }

            val settings = repository.settings.first()

            assertThat(settings.idleTimeoutSeconds).isEqualTo(60)
            assertThat(settings.paymentMethods).containsExactly(PaymentMethod.CARD)
        }
}
