package com.hitachi.kiosk.di

import com.hitachi.kiosk.core.domain.image.ImagePrefetcher
import com.hitachi.kiosk.core.domain.order.DefaultOrderSessionHolder
import com.hitachi.kiosk.core.domain.order.OrderSessionHolder
import com.hitachi.kiosk.image.CoilImagePrefetcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The customer order and the image cache (architecture §5.1, §10): one [OrderSessionHolder] for the whole app, so
 * Order type, Menu, Item and Combo share the cart, and the Coil-backed [ImagePrefetcher] the sync uses.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    abstract fun imagePrefetcher(prefetcher: CoilImagePrefetcher): ImagePrefetcher

    companion object {
        @Provides
        @Singleton
        fun orderSessionHolder(): OrderSessionHolder = DefaultOrderSessionHolder()
    }
}
