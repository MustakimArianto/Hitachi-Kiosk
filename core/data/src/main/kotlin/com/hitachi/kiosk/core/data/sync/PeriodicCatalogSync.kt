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

/** Schedules [CatalogSyncWorker] every 15 minutes with a connected network (FR-028, technical-design §7.2). */
class PeriodicCatalogSync
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** Enqueues the unique periodic work; an existing schedule is updated, not restarted (policy UPDATE). */
        fun schedule() {
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
            /** Unique work name (technical-design §7.2). */
            const val WORK_NAME = "catalog-sync"
            const val INTERVAL_MINUTES = 15L
        }
    }
