package com.hitachi.kiosk.core.data.prefs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.hitachi.kiosk.core.data.api.KioskJson
import com.hitachi.kiosk.core.domain.log.KioskLog
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import kotlin.coroutines.cancellation.CancellationException

/** The content of `secure.pb`. [toString] never prints the token (NFR-006). */
@Serializable
internal data class SecureStore(
    val deviceToken: String? = null,
) {
    override fun toString(): String = "SecureStore(deviceToken=${if (deviceToken == null) "null" else "•••"})"
}

/**
 * The device token in the typed DataStore `secure.pb`, encrypted with Tink AES-256-GCM under a keyset wrapped by an
 * Android Keystore key (architecture §6, technical-design §6.3 TD-05, NFR-005).
 *
 * Any failure to read (lost Keystore key, tampered file, I/O) wipes the store and reads as "no token", so the
 * kiosk routes to Sign in instead of crashing (FR-001, TR-REL-06).
 */
internal class SecureTokenStore(
    private val dataStore: DataStore<SecureStore>,
) : TokenStore {
    override suspend fun read(): String? =
        try {
            dataStore.data.first().deviceToken
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            reset(e)
        } catch (e: GeneralSecurityException) {
            reset(e)
        }

    override suspend fun write(token: String) {
        dataStore.updateData { SecureStore(deviceToken = token) }
    }

    override suspend fun clear() {
        try {
            dataStore.updateData { SecureStore() }
        } catch (e: IOException) {
            KioskLog.e("token_store_clear_failed", "type" to e.javaClass.simpleName)
        } catch (e: GeneralSecurityException) {
            KioskLog.e("token_store_clear_failed", "type" to e.javaClass.simpleName)
        }
    }

    private suspend fun reset(error: Exception): String? {
        KioskLog.e("token_store_reset", "type" to error.javaClass.simpleName)
        clear()
        return null
    }
}

/**
 * Encrypts [SecureStore] with the [Aead] from [aead], built on first use (technical-design §6.3). DataStore reads
 * and writes on its own I/O thread, so building the Keystore-backed keyset there keeps it off the main thread.
 * A file that fails to decrypt is a [CorruptionException]; the DataStore's corruption handler replaces it.
 */
internal class EncryptedSecureStoreSerializer(
    aead: () -> Aead,
) : Serializer<SecureStore> {
    private val delegate by lazy { AeadSerializer(aead(), SecureStoreJsonSerializer, ASSOCIATED_DATA) }

    override val defaultValue: SecureStore = SecureStore()

    override suspend fun readFrom(input: InputStream): SecureStore = delegate.readFrom(input)

    override suspend fun writeTo(
        t: SecureStore,
        output: OutputStream,
    ) = delegate.writeTo(t, output)

    private companion object {
        /** Binds the ciphertext to this file's purpose (technical-design §6.3). */
        val ASSOCIATED_DATA = "kiosk.secure.v1".encodeToByteArray()
    }
}

/** Plain JSON of [SecureStore]; only ever written through [EncryptedSecureStoreSerializer]. */
internal object SecureStoreJsonSerializer : Serializer<SecureStore> {
    override val defaultValue: SecureStore = SecureStore()

    override suspend fun readFrom(input: InputStream): SecureStore =
        try {
            KioskJson.decodeFromString(SecureStore.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("secure.pb is not a SecureStore", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("secure.pb is not a SecureStore", e)
        }

    override suspend fun writeTo(
        t: SecureStore,
        output: OutputStream,
    ) {
        output.write(KioskJson.encodeToString(SecureStore.serializer(), t).encodeToByteArray())
    }
}
