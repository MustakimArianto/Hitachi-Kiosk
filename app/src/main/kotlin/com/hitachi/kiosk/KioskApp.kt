package com.hitachi.kiosk

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.log.LogLevel
import com.hitachi.kiosk.image.kioskImageLoader
import com.hitachi.kiosk.log.TimberLogSink
import com.hitachi.kiosk.sync.CatalogSyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Application entry point (technical-design §4.1). No synchronous disk or network work here (TR-PRF-01): WorkManager
 * starts on demand with the Hilt worker factory, Coil builds its loader on first use, and the sync triggers start in
 * the application scope.
 */
@HiltAndroidApp
class KioskApp :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var catalogSync: CatalogSyncCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy
                    .Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy
                    .Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
        }
        KioskLog.install(TimberLogSink(minLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO))
        KioskLog.i("app_start", "version" to BuildConfig.VERSION_NAME, "env" to BuildConfig.KIOSK_ENV)
        catalogSync.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = kioskImageLoader(context)
}
