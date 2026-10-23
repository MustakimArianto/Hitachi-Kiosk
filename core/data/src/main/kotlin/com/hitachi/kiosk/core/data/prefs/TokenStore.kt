package com.hitachi.kiosk.core.data.prefs

/**
 * Where the device token is kept (NFR-005). The binding is [SecureTokenStore]; tests use an in-memory store. Reads
 * never throw: storage that cannot be decrypted is wiped and reads as "no token" (FR-001, TR-REL-06).
 */
internal interface TokenStore {
    suspend fun read(): String?

    suspend fun write(token: String)

    suspend fun clear()
}
