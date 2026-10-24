package com.hitachi.kiosk.debug

import com.hitachi.kiosk.devtools.FrameDiagnostics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the optional [FrameDiagnostics] (technical-design §4.3 TD-11) in the debug and `benchmarkRelease` builds, which
 * add `src/frameDiagnostics` to their sources. Release has none of this module.
 */
@Module
@InstallIn(SingletonComponent::class)
object FrameDiagnosticsModule {
    @Provides
    fun frameDiagnostics(): FrameDiagnostics = JankStatsFrameDiagnostics()
}
