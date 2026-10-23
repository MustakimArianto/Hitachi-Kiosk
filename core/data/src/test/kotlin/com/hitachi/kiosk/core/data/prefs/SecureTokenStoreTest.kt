package com.hitachi.kiosk.core.data.prefs

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The encrypted token store (NFR-005, technical-design §6.3), with a plain Tink keyset instead of the Android
 * Keystore one. TC-146 checks the same on a device with `run-as`.
 */
class SecureTokenStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "secure.pb") }

    private fun newAead(): Aead {
        AeadConfig.register()
        return KeysetHandle
            .generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun newStore(
        aead: Aead,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    ): SecureTokenStore =
        SecureTokenStore(
            DataStoreFactory.create(
                serializer = EncryptedSecureStoreSerializer { aead },
                corruptionHandler = ReplaceFileCorruptionHandler { SecureStore() },
                scope = scope,
                produceFile = { file },
            ),
        )

    @Test
    fun `a written token reads back and is not stored in plain text`() =
        runTest {
            val store = newStore(newAead())

            store.write(TOKEN)

            assertThat(store.read()).isEqualTo(TOKEN)
            assertThat(file.readBytes().decodeToString()).doesNotContain(TOKEN)
        }

    @Test
    fun `clear removes the token`() =
        runTest {
            val store = newStore(newAead())
            store.write(TOKEN)

            store.clear()

            assertThat(store.read()).isNull()
        }

    @Test
    fun `a file that does not decrypt reads as no token`() =
        runTest {
            val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            newStore(newAead(), firstScope).write(TOKEN)
            firstScope.coroutineContext[Job]?.cancelAndJoin()

            // A different key, as after the Keystore key is lost: the old file no longer decrypts (FR-001).
            assertThat(newStore(newAead()).read()).isNull()
        }

    @Test
    fun `a tampered file reads as no token`() =
        runTest {
            file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

            assertThat(newStore(newAead()).read()).isNull()
        }

    @Test
    fun `toString never prints the token`() {
        assertThat(SecureStore(TOKEN).toString()).doesNotContain(TOKEN)
        assertThat(SecureStore().toString()).contains("null")
    }

    private companion object {
        const val TOKEN = "dvt_9f2c4a7e1b8d4f0aa3c6e2b17d5f9e01"
    }
}
