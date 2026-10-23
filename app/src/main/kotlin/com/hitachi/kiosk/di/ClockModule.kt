package com.hitachi.kiosk.di

import com.hitachi.kiosk.core.domain.id.IdGenerator
import com.hitachi.kiosk.core.domain.id.UuidIdGenerator
import com.hitachi.kiosk.core.domain.time.Clock
import com.hitachi.kiosk.time.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Time and identifiers, injected so tests control them (TR-INT-10, TR-INT-11). */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun clock(): Clock = SystemClock()

    @Provides
    fun idGenerator(): IdGenerator = UuidIdGenerator()
}
