package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository

/**
 * A scriptable [CatalogSyncRepository] (NFR-012). Merchant IDs passed to [sync] are recorded in [synced], and
 * [applyPending] calls are counted in [pendingApplied].
 */
class FakeCatalogSyncRepository : CatalogSyncRepository {
    val script = Script<KioskMode>(Outcome.Success(KioskMode.SINGLE))

    private val recorded = mutableListOf<String>()

    @Volatile
    var pendingApplied: Int = 0
        private set

    val synced: List<String> get() = synchronized(recorded) { recorded.toList() }

    override suspend fun sync(merchantId: String): Outcome<KioskMode> {
        synchronized(recorded) { recorded += merchantId }
        return script.next()
    }

    override suspend fun applyPending() {
        pendingApplied++
    }
}
