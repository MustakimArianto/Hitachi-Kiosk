package com.hitachi.kiosk.di

import com.hitachi.kiosk.core.domain.id.IdGenerator
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.core.domain.order.SubmitOrder
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository
import com.hitachi.kiosk.core.domain.repository.OrderRepository
import com.hitachi.kiosk.core.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Order submission (FR-016, DEV-045, DEV-046): the `:core:domain` [SubmitOrder] use case over the session, the cached
 * catalog and its sync, the settings and the order repository. Plain Kotlin in `:core:domain`, so `:app` provides it.
 */
@Module
@InstallIn(SingletonComponent::class)
object OrderModule {
    @Provides
    fun submitOrder(
        sessions: OrderSessionHolder,
        catalogs: CatalogRepository,
        catalogSync: CatalogSyncRepository,
        settings: SettingsRepository,
        orders: OrderRepository,
        ids: IdGenerator,
    ): SubmitOrder = SubmitOrder(sessions, catalogs, catalogSync, settings, orders, ids)
}
