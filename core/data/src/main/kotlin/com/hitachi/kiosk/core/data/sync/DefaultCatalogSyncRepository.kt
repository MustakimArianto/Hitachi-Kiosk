package com.hitachi.kiosk.core.data.sync

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.domain.model.Menu
import com.hitachi.kiosk.core.domain.model.MerchantConfig
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository
import com.hitachi.kiosk.core.domain.retryWithBackoff
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** The config and menu of the last successful sync. */
internal data class CatalogSnapshot(
    val config: MerchantConfig,
    val menu: Menu,
)

/**
 * The 0.2.0 catalog download (DEV-016 stub, FR-028): E5 then E6, each retried on transient errors, kept in memory.
 * Concurrent calls share one run. DEV-021/DEV-022 (0.3.0) replace the memory copy with the Room cache, send ETags
 * and add the startup, periodic and reconnect triggers.
 */
@Singleton
internal class DefaultCatalogSyncRepository
    @Inject
    constructor(
        private val backend: BackendApi,
    ) : CatalogSyncRepository {
        private val mutex = Mutex()

        /** The last downloaded catalog, or `null` before the first successful sync. */
        @Volatile
        var latest: CatalogSnapshot? = null
            private set

        override suspend fun sync(merchantId: String): Outcome<KioskMode> =
            mutex.withLock {
                val config =
                    when (val outcome = retryWithBackoff { backend.config(merchantId, etag = null) }) {
                        is Outcome.Failure -> return@withLock outcome.also { logFailure("E5", it.error) }
                        is Outcome.Success -> outcome.value.valueOr(latest?.config) ?: return@withLock notModified("E5")
                    }
                val menu =
                    when (val outcome = retryWithBackoff { backend.menu(merchantId, etag = null) }) {
                        is Outcome.Failure -> return@withLock outcome.also { logFailure("E6", it.error) }
                        is Outcome.Success -> outcome.value.valueOr(latest?.menu) ?: return@withLock notModified("E6")
                    }
                latest = CatalogSnapshot(config, menu)
                KioskLog.i("catalog_synced", "merchantId" to merchantId, "menuVersion" to menu.menuVersion)
                Outcome.Success(config.mode)
            }

        private fun <T> Conditional<T>.valueOr(cached: T?): T? =
            when (this) {
                is Conditional.Modified -> value
                Conditional.NotModified -> cached
            }

        /** A 304 without a cached copy cannot happen without an ETag; treat it as a bad answer. */
        private fun notModified(endpoint: String): Outcome.Failure {
            KioskLog.w("catalog_unexpected_not_modified", "endpoint" to endpoint)
            return Outcome.Failure(DomainError.Unknown)
        }

        private fun logFailure(
            endpoint: String,
            error: DomainError,
        ) = KioskLog.w("catalog_sync_failed", "endpoint" to endpoint, "error" to error)
    }
