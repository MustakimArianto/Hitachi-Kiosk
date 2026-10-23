package com.hitachi.kiosk.core.data.prefs

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.hitachi.kiosk.core.domain.log.KioskLog
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * The AES-256-GCM [Aead] for `secure.pb` (technical-design §6.3, TD-05). Tink keeps the keyset, wrapped by the
 * Android Keystore key `kiosk_master_key`, in the small preferences file `kiosk_keyset_prefs`; the token itself is
 * never there. If the keyset cannot be unwrapped (for example the Keystore key was lost in a device restore), the
 * keyset is deleted and a new one created; the old `secure.pb` then fails to decrypt and is replaced, so the kiosk
 * asks to sign in again (FR-001). Blocking: call it off the main thread.
 */
internal object KeystoreAead {
    private const val KEYSET_NAME = "kiosk_keyset"
    private const val KEYSET_PREFS = "kiosk_keyset_prefs"
    private const val MASTER_KEY_URI = "android-keystore://kiosk_master_key"

    fun create(context: Context): Aead {
        AeadConfig.register()
        return try {
            build(context)
        } catch (e: GeneralSecurityException) {
            reset(context, e)
        } catch (e: IOException) {
            reset(context, e)
        }
    }

    private fun reset(
        context: Context,
        error: Exception,
    ): Aead {
        KioskLog.e("token_store_reset", "type" to error.javaClass.simpleName, "cause" to "keyset")
        context.deleteSharedPreferences(KEYSET_PREFS)
        return build(context)
    }

    private fun build(context: Context): Aead =
        AndroidKeysetManager
            .Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}
