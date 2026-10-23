package com.hitachi.kiosk.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/** DEV-023: one unique periodic sync every 15 minutes, only with a connected network (technical-design §7.2). */
@RunWith(RobolectricTestRunner::class)
class WorkManagerCatalogSyncSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun initWorkManager() = WorkManagerTestInitHelper.initializeTestWorkManager(context)

    @Test
    fun `scheduling twice keeps one periodic work with a network constraint`() {
        val scheduler = WorkManagerCatalogSyncScheduler(context)

        scheduler.schedulePeriodic()
        scheduler.schedulePeriodic()

        val work =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(WorkManagerCatalogSyncScheduler.WORK_NAME)
                .get()
        val info = work.single()
        assertThat(info.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(info.periodicityInfo?.repeatIntervalMillis).isEqualTo(TimeUnit.MINUTES.toMillis(15))
        assertThat(info.constraints.requiredNetworkType).isEqualTo(androidx.work.NetworkType.CONNECTED)
    }
}
