package com.hitachi.kiosk

import android.app.Application
import android.os.StrictMode
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.log.LogLevel
import com.hitachi.kiosk.log.TimberLogSink
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/** Application entry point (technical-design §4.1). No synchronous disk or network work here (TR-PRF-01). */
@HiltAndroidApp
class KioskApp : Application() {
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
    }
}
