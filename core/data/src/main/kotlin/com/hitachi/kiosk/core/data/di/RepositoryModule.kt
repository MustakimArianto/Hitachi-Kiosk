package com.hitachi.kiosk.core.data.di

import com.hitachi.kiosk.core.data.api.UnauthorizedHandler
import com.hitachi.kiosk.core.data.repository.CatalogCache
import com.hitachi.kiosk.core.data.repository.CatalogStore
import com.hitachi.kiosk.core.data.repository.DefaultActivationRepository
import com.hitachi.kiosk.core.data.repository.DefaultCatalogRepository
import com.hitachi.kiosk.core.data.repository.DefaultMerchantRepository
import com.hitachi.kiosk.core.data.repository.DefaultOrderRepository
import com.hitachi.kiosk.core.data.repository.DefaultSettingsRepository
import com.hitachi.kiosk.core.data.repository.PendingOrderCache
import com.hitachi.kiosk.core.data.repository.PendingOrderStore
import com.hitachi.kiosk.core.data.repository.RoomPendingOrderStore
import com.hitachi.kiosk.core.data.repository.SessionInvalidator
import com.hitachi.kiosk.core.data.sync.CatalogSyncScheduler
import com.hitachi.kiosk.core.data.sync.DefaultCatalogSyncRepository
import com.hitachi.kiosk.core.data.sync.WorkManagerCatalogSyncScheduler
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.core.domain.repository.OrderRepository
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Domain repository ports → their `:core:data` implementations (architecture §10). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun activationRepository(repository: DefaultActivationRepository): ActivationRepository

    @Binds
    abstract fun merchantRepository(repository: DefaultMerchantRepository): MerchantRepository

    @Binds
    abstract fun catalogSyncRepository(repository: DefaultCatalogSyncRepository): CatalogSyncRepository

    /** The Room catalog cache the customer screens read (DEV-021). */
    @Binds
    abstract fun catalogRepository(repository: DefaultCatalogRepository): CatalogRepository

    /** The sync reads and replaces the same cache (DEV-022). */
    @Binds
    abstract fun catalogStore(repository: DefaultCatalogRepository): CatalogStore

    /** A session wipe empties the same cache (FR-004 AC3). */
    @Binds
    abstract fun catalogCache(repository: DefaultCatalogRepository): CatalogCache

    /** E7, E8 and the pending order (DEV-045). */
    @Binds
    abstract fun orderRepository(repository: DefaultOrderRepository): OrderRepository

    /** The Room `pending_order` table; the order repository reads and writes it (DEV-045). */
    @Binds
    abstract fun pendingOrderStore(store: RoomPendingOrderStore): PendingOrderStore

    /** A session wipe forgets the pending order too (FR-004 AC3). */
    @Binds
    abstract fun pendingOrderCache(store: RoomPendingOrderStore): PendingOrderCache

    /** The device settings in `settings.preferences_pb` (FR-025, FR-026). */
    @Binds
    abstract fun settingsRepository(repository: DefaultSettingsRepository): SettingsRepository

    /** The 15-minute periodic sync (DEV-023). */
    @Binds
    abstract fun catalogSyncScheduler(scheduler: WorkManagerCatalogSyncScheduler): CatalogSyncScheduler

    /** HTTP 401 wipes the session (TR-NET-06). */
    @Binds
    abstract fun unauthorizedHandler(invalidator: SessionInvalidator): UnauthorizedHandler
}
