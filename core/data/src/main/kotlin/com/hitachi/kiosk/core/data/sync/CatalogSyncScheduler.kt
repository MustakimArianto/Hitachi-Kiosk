package com.hitachi.kiosk.core.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hitachi.kiosk.core.domain.log.KioskLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Schedules the periodic catalog sync (FR-028, DEV-023). `:app` calls it once at startup. */
fun interface CatalogSyncScheduler {
    /** Enqueues the unique periodic work; an existing schedule is updated, not restarted. */
    fun schedulePeriodic()
}

/**
 * [CatalogSyncWorker] every 15 minutes while a network is connected, unique work [WORK_NAME] with policy UPDATE
 * (technical-design §7.2).
 */
internal class WorkManagerCatalogSyncScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CatalogSyncScheduler {
        override fun schedulePeriodic() {
            val request =
                PeriodicWorkRequestBuilder<CatalogSyncWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            KioskLog.i("catalog_sync_scheduled", "intervalMin" to INTERVAL_MINUTES)
        }

        companion object {
            const val WORK_NAME = "catalog-sync"
            const val INTERVAL_MINUTES = 15L
        }
    }
