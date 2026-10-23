package com.hitachi.kiosk.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.testing.fake.FakeActivationRepository
import com.hitachi.kiosk.core.testing.fake.FakeCatalogSyncRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** DEV-023: the periodic worker syncs the bound merchant and retries only a dropped connection. */
@RunWith(RobolectricTestRunner::class)
class CatalogSyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val activation = FakeActivationRepository()
    private val sync = FakeCatalogSyncRepository()

    private fun worker(): CatalogSyncWorker =
        TestListenableWorkerBuilder<CatalogSyncWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = CatalogSyncWorker(appContext, workerParameters, activation, sync)
                },
            ).build()

    @Test
    fun `a bound kiosk syncs its merchant`() =
        runTest {
            activation.emit(SampleData.boundState)

            assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.success())
            assertThat(sync.synced).containsExactly(SampleData.MERCHANT_ID)
        }

    @Test
    fun `a kiosk without a merchant does nothing`() =
        runTest {
            activation.emit(SampleData.activatedState)

            assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.success())
            assertThat(sync.synced).isEmpty()
        }

    @Test
    fun `a process started by WorkManager loads the device state first`() =
        runTest {
            activation.stored = SampleData.boundState

            worker().doWork()

            assertThat(sync.synced).containsExactly(SampleData.MERCHANT_ID)
        }

    @Test
    fun `a network failure retries, any other failure waits for the next period`() =
        runTest {
            activation.emit(SampleData.boundState)

            sync.script.fail(DomainError.Network())
            assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.retry())

            sync.script.always(Outcome.Failure(DomainError.Server("SERVER_ERROR", 503)))
            assertThat(worker().doWork()).isEqualTo(ListenableWorker.Result.success())
        }
}
