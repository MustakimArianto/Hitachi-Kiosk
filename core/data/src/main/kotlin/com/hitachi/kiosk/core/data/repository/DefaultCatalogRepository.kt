package com.hitachi.kiosk.core.data.repository

import android.database.SQLException
import com.hitachi.kiosk.core.data.api.KioskJson
import com.hitachi.kiosk.core.data.db.dao.CatalogDao
import com.hitachi.kiosk.core.data.mapper.CatalogStamp
import com.hitachi.kiosk.core.data.mapper.toCatalog
import com.hitachi.kiosk.core.data.mapper.toRows
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** The part of the catalog cache the device session needs: forgetting it (FR-004 AC3, TR-NET-06). */
internal fun interface CatalogCache {
    /** Empties the cache. Never throws. */
    suspend fun clear()
}

/** The catalog cache as the sync sees it: read the stored copy, replace it (technical-design §7.1). */
internal interface CatalogStore : CatalogCache {
    /** The cached catalog with its ETags, or `null` when the cache is empty. Never throws (TR-REL-07). */
    suspend fun stored(): StoredCatalog?

    /** Replaces the cache in one transaction (TR-DAT-03). On failure the old catalog stays and this throws. */
    suspend fun replace(stored: StoredCatalog)
}

/** A cached catalog with the ETags it was fetched with (TR-NET-09). */
internal data class StoredCatalog(
    val catalog: Catalog,
    val stamp: CatalogStamp,
)

/**
 * The Room catalog cache (DEV-021, technical-design §6.1) and its in-memory copy. The first read loads Room once;
 * after that every reader shares the memory copy, so a category switch never touches the disk (TR-PRF-04). Only
 * [DefaultCatalogSyncRepository] and the session wipe write it.
 */
@Singleton
internal class DefaultCatalogRepository
    @Inject
    constructor(
        private val dao: CatalogDao,
    ) : CatalogRepository,
        CatalogStore {
        private sealed interface CacheState {
            data object Unloaded : CacheState

            data class Loaded(
                val stored: StoredCatalog?,
            ) : CacheState
        }

        private val mutex = Mutex()
        private val cache = MutableStateFlow<CacheState>(CacheState.Unloaded)

        override val catalog: Flow<Catalog?> =
            flow {
                stored()
                emitAll(cache.filterIsInstance<CacheState.Loaded>().map { it.stored?.catalog })
            }.distinctUntilChanged()

        /** Loads the cache from Room on first use. */
        override suspend fun stored(): StoredCatalog? =
            mutex.withLock {
                when (val current = cache.value) {
                    is CacheState.Loaded -> current.stored
                    CacheState.Unloaded -> load().also { cache.value = CacheState.Loaded(it) }
                }
            }

        override suspend fun replace(stored: StoredCatalog) {
            mutex.withLock {
                dao.replaceAll(stored.catalog.toRows(stored.stamp, KioskJson))
                cache.value = CacheState.Loaded(stored)
            }
        }

        /** Empties the cache: Log out and HTTP 401 forget the merchant's catalog (FR-004 AC3). Never throws. */
        override suspend fun clear() {
            mutex.withLock {
                try {
                    dao.deleteAll()
                } catch (e: SQLException) {
                    KioskLog.e("catalog_clear_failed", "type" to e.javaClass.simpleName)
                }
                cache.value = CacheState.Loaded(null)
            }
        }

        private suspend fun load(): StoredCatalog? =
            try {
                dao.readAll()?.let { rows ->
                    StoredCatalog(
                        catalog = rows.toCatalog(KioskJson),
                        stamp = CatalogStamp(rows.config.etag, rows.meta.etag, rows.meta.fetchedAtMillis),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLException) {
                unreadable(e)
            } catch (e: IllegalArgumentException) {
                // Covers a config JSON or enum name that no longer parses.
                unreadable(e)
            }

        private fun unreadable(e: Exception): StoredCatalog? {
            KioskLog.e("catalog_cache_unreadable", "type" to e.javaClass.simpleName)
            return null
        }
    }
