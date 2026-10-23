package com.hitachi.kiosk.core.data.di

import com.hitachi.kiosk.core.data.api.UnauthorizedHandler
import com.hitachi.kiosk.core.data.repository.DefaultActivationRepository
import com.hitachi.kiosk.core.data.repository.DefaultMerchantRepository
import com.hitachi.kiosk.core.data.repository.SessionInvalidator
import com.hitachi.kiosk.core.data.sync.DefaultCatalogSyncRepository
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
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

    /** HTTP 401 wipes the session (TR-NET-06). */
    @Binds
    abstract fun unauthorizedHandler(invalidator: SessionInvalidator): UnauthorizedHandler
}
