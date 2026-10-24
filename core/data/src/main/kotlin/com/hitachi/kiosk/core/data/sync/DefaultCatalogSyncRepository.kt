package com.hitachi.kiosk.core.data.sync

import android.database.SQLException
import com.hitachi.kiosk.core.data.mapper.CatalogStamp
import com.hitachi.kiosk.core.data.repository.CatalogStore
import com.hitachi.kiosk.core.data.repository.StoredCatalog
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.image.ImagePrefetcher
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.model.Conditional
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository
import com.hitachi.kiosk.core.domain.retryWithBackoff
import com.hitachi.kiosk.core.domain.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The catalog sync of FR-028 (DEV-022, technical-design §7.1): E5 then E6 with `If-None-Match`, each retried on
 * transient errors, written to the Room cache in one transaction, then the images are prefetched (NFR-013). A
 * catalog that arrives during an order waits in memory until [applyPending] (A-29). Concurrent calls share one
 * [Mutex], so they run one after the other.
 */
@Singleton
internal class DefaultCatalogSyncRepository
    @Inject
    constructor(
        private val backend: BackendApi,
        private val store: CatalogStore,
        private val orders: OrderSessionHolder,
        private val images: ImagePrefetcher,
        private val clock: Clock,
    ) : CatalogSyncRepository {
        private val mutex = Mutex()

        /** A newer catalog held back while an order runs (A-29). Guarded by [mutex]. */
        private var pending: StoredCatalog? = null

        override suspend fun sync(merchantId: String): Outcome<KioskMode> =
            download(merchantId, deferDuringOrder = true)

        /**
         * The `PRICE_CHANGED` refresh (FR-016, DEV-046): the same download, written even while an order runs. A catalog
         * held back earlier is superseded, because the download used its ETags as the base.
         */
        override suspend fun syncNow(merchantId: String): Outcome<KioskMode> =
            download(merchantId, deferDuringOrder = false)

        private suspend fun download(
            merchantId: String,
            deferDuringOrder: Boolean,
        ): Outcome<KioskMode> =
            mutex.withLock {
                // The newest copy we hold for this merchant; another merchant's cache never lends its ETags.
                val base = (pending ?: store.stored())?.takeIf { it.catalog.config.merchantId == merchantId }

                val config =
                    when (val outcome = retryWithBackoff { backend.config(merchantId, base?.stamp?.configEtag) }) {
                        is Outcome.Failure -> return@withLock outcome.also { logFailure("E5", it.error) }
                        is Outcome.Success -> outcome.value
                    }
                val menu =
                    when (val outcome = retryWithBackoff { backend.menu(merchantId, base?.stamp?.menuEtag) }) {
                        is Outcome.Failure -> return@withLock outcome.also { logFailure("E6", it.error) }
                        is Outcome.Success -> outcome.value
                    }

                val configValue = config.valueOr(base?.catalog?.config) ?: return@withLock notModified("E5")
                val menuValue = menu.valueOr(base?.catalog?.menu) ?: return@withLock notModified("E6")
                if (base != null && config is Conditional.NotModified && menu is Conditional.NotModified) {
                    return@withLock unchanged(merchantId, configValue.mode, menuValue.menuVersion, deferDuringOrder)
                }

                val next =
                    StoredCatalog(
                        catalog = Catalog(configValue, menuValue),
                        stamp =
                            CatalogStamp(
                                configEtag = config.etagOr(base?.stamp?.configEtag),
                                menuEtag = menu.etagOr(base?.stamp?.menuEtag),
                                fetchedAtMillis = clock.now().toEpochMilliseconds(),
                            ),
                    )
                landOrHold(next, deferDuringOrder)
            }

        /** Stores [next], or holds it back while an order runs and [deferDuringOrder] (A-29). Must hold [mutex]. */
        private suspend fun landOrHold(
            next: StoredCatalog,
            deferDuringOrder: Boolean,
        ): Outcome<KioskMode> {
            if (!deferDuringOrder || !orders.isOrderActive) return write(next)
            pending = next
            KioskLog.i("catalog_deferred", "menuVersion" to next.catalog.menu.menuVersion)
            return Outcome.Success(next.catalog.config.mode)
        }

        /** Nothing newer than what we hold; a copy held back during an order still has to land on [syncNow]. */
        private suspend fun unchanged(
            merchantId: String,
            mode: KioskMode,
            menuVersion: String,
            deferDuringOrder: Boolean,
        ): Outcome<KioskMode> {
            pending?.takeIf { !deferDuringOrder }?.let { return write(it) }
            KioskLog.i("catalog_unchanged", "merchantId" to merchantId, "menuVersion" to menuVersion)
            return Outcome.Success(mode)
        }

        override suspend fun applyPending() {
            mutex.withLock {
                val next = pending ?: return@withLock
                if (orders.isOrderActive) return@withLock
                write(next)
            }
        }

        /** Stores [next] and prefetches its images. Must hold [mutex]. */
        private suspend fun write(next: StoredCatalog): Outcome<KioskMode> {
            try {
                store.replace(next)
            } catch (e: SQLException) {
                // The transaction rolled back, so the previous catalog is still served (TR-REL-07).
                KioskLog.e("catalog_write_failed", "type" to e.javaClass.simpleName)
                return Outcome.Failure(DomainError.Unknown)
            }
            pending = null
            images.prefetch(next.catalog.imageUrls())
            KioskLog.i(
                "catalog_synced",
                "merchantId" to next.catalog.config.merchantId,
                "menuVersion" to next.catalog.menu.menuVersion,
            )
            return Outcome.Success(next.catalog.config.mode)
        }

        private fun <T> Conditional<T>.valueOr(cached: T?): T? =
            when (this) {
                is Conditional.Modified -> value
                Conditional.NotModified -> cached
            }

        private fun Conditional<*>.etagOr(cached: String?): String? =
            when (this) {
                is Conditional.Modified -> etag
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

/** Every image the customer screens can show, for the disk-cache prefetch (FR-028, NFR-013). */
internal fun Catalog.imageUrls(): Set<String> =
    buildSet {
        addAll(config.idlePromoImageUrls.filterNotNull())
        config.logoUrl?.let(::add)
        config.heroPhotoUrl?.let(::add)
        menu.products.mapNotNullTo(this) { it.imageUrl }
        menu.modifierGroups.flatMap { it.options }.mapNotNullTo(this) { it.imageUrl }
        menu.combos.mapNotNullTo(this) { it.imageUrl }
        (menu.comboSlots.sides + menu.comboSlots.drinks).mapNotNullTo(this) { it.imageUrl }
    }
