package com.hitachi.kiosk.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.model.DeviceState
import com.hitachi.kiosk.core.domain.repository.ActivationRepository
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The periodic catalog sync (FR-028, DEV-023, technical-design §7.2): every 15 minutes while a network is connected.
 * The process may have been started by WorkManager alone, so the device state is loaded when nothing has read it yet.
 */
@HiltWorker
internal class CatalogSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val activation: ActivationRepository,
        private val catalog: CatalogSyncRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val state = activation.state.value.takeIf { it != DeviceState.NotActivated } ?: activation.load()
            val merchantId = (state as? DeviceState.Bound)?.merchant?.id ?: return Result.success()
            return when (val outcome = catalog.sync(merchantId)) {
                is Outcome.Success -> Result.success()

                // A dropped connection is worth a retry with WorkManager's backoff; anything else waits 15 minutes.
                is Outcome.Failure -> if (outcome.error is DomainError.Network) Result.retry() else Result.success()
            }
        }
    }
