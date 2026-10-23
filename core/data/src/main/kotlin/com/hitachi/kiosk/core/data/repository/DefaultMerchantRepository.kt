package com.hitachi.kiosk.core.data.repository

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.backend.BackendApi
import com.hitachi.kiosk.core.domain.error.DomainError
import com.hitachi.kiosk.core.domain.id.IdGenerator
import com.hitachi.kiosk.core.domain.log.KioskLog
import com.hitachi.kiosk.core.domain.model.BoundMerchant
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.domain.model.Merchant
import com.hitachi.kiosk.core.domain.repository.BindResult
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository
import com.hitachi.kiosk.core.domain.repository.MerchantRepository
import com.hitachi.kiosk.core.domain.retryWithBackoff
import javax.inject.Inject

/** E2 list, E3 bind with the first catalog download, and E4 Log out (FR-004, FR-005, DEV-016, DEV-017). */
internal class DefaultMerchantRepository
    @Inject
    constructor(
        private val backend: BackendApi,
        private val session: DeviceSession,
        private val catalog: CatalogSyncRepository,
        private val ids: IdGenerator,
    ) : MerchantRepository {
        override suspend fun merchants(): Outcome<List<Merchant>> {
            val deviceId = session.deviceId ?: return notActivated()
            return retryWithBackoff { backend.merchants(deviceId) }
        }

        override suspend fun bind(merchant: Merchant): BindResult {
            val deviceId = session.deviceId ?: return BindResult.BindFailed(DomainError.Unauthorized)
            // One key per bind attempt, reused by the retries (technical-design §5.3).
            val key = ids.newIdempotencyKey()
            val bound = retryWithBackoff { backend.bind(deviceId, merchant.id, key) }
            if (bound is Outcome.Failure) return BindResult.BindFailed(bound.error)

            return when (val synced = catalog.sync(merchant.id)) {
                is Outcome.Failure -> BindResult.MenuDownloadFailed(synced.error)
                is Outcome.Success -> store(merchant, mode = synced.value)
            }
        }

        /** Stores the binding once config and menu are downloaded (FR-004, FR-005). */
        private suspend fun store(
            merchant: Merchant,
            mode: KioskMode,
        ): BindResult {
            if (mode != KioskMode.SINGLE) {
                // 1.0 is single-merchant only; food-court mode is stored but not acted on (FR-005, D-07).
                KioskLog.w("kiosk_mode_unsupported", "mode" to mode)
            }
            // A 401 during the bind wiped the activation; nothing is bound then.
            val state =
                session.saveBinding(BoundMerchant(merchant.id, merchant.name), mode)
                    ?: return BindResult.BindFailed(DomainError.Unauthorized)
            KioskLog.i("merchant_bound", "merchantId" to merchant.id, "mode" to mode)
            return BindResult.Bound(state)
        }

        override suspend fun logOut() {
            session.deviceId?.let { deviceId ->
                // E4 is not retried (technical-design §5.3); the local wipe below happens whatever it answers.
                val outcome = backend.unbind(deviceId)
                if (outcome is Outcome.Failure) KioskLog.w("unbind_failed", "error" to outcome.error)
            }
            session.wipe(reason = "log_out")
        }

        private fun notActivated(): Outcome.Failure {
            KioskLog.w("merchants_without_activation")
            return Outcome.Failure(DomainError.Unauthorized)
        }
    }
