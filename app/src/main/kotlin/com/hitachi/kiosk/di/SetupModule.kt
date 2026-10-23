package com.hitachi.kiosk.di

import com.hitachi.kiosk.BuildConfig
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.core.domain.setup.ActivateKiosk
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId

/** Device setup (FR-001–FR-005): domain use cases and the app facts the setup screens show. */
@Module
@InstallIn(SingletonComponent::class)
object SetupModule {
    @Provides
    fun activateKiosk(
        activation: ActivationRepository,
        merchants: MerchantRepository,
    ): ActivateKiosk = ActivateKiosk(activation, merchants)

    /** The real `versionName` for the Sign in pill (PRD §4.1 C7). */
    @Provides
    @VersionName
    fun versionName(): String = BuildConfig.VERSION_NAME

    /** The device time zone, for local times such as "opens 10:00" (FR-004). */
    @Provides
    fun zone(): ZoneId = ZoneId.systemDefault()
}
