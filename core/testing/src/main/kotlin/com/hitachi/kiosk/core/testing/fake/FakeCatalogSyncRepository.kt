package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.model.KioskMode
import com.hitachi.kiosk.core.domain.repository.CatalogSyncRepository

/**
 * A scriptable [CatalogSyncRepository] (NFR-012). Merchant IDs passed to [sync] are recorded in [synced], and
 * [applyPending] calls are counted in [pendingApplied]. [syncNow] (the `PRICE_CHANGED` refresh, DEV-046) answers from
 * its own [nowScript] and records its merchant IDs in [syncedNow]; [onSyncNow] lets a test change the catalog the way a
 * real refresh would.
 */
class FakeCatalogSyncRepository : CatalogSyncRepository {
    val script = Script<KioskMode>(Outcome.Success(KioskMode.SINGLE))

    /** The answers of [syncNow]. */
    val nowScript = Script<KioskMode>(Outcome.Success(KioskMode.SINGLE))

    /** Runs when [syncNow] is called, before it answers, for example to put a new menu into a fake catalog. */
    var onSyncNow: () -> Unit = {}

    private val recorded = mutableListOf<String>()
    private val recordedNow = mutableListOf<String>()

    @Volatile
    var pendingApplied: Int = 0
        private set

    val synced: List<String> get() = synchronized(recorded) { recorded.toList() }

    val syncedNow: List<String> get() = synchronized(recordedNow) { recordedNow.toList() }

    override suspend fun sync(merchantId: String): Outcome<KioskMode> {
        synchronized(recorded) { recorded += merchantId }
        return script.next()
    }

    override suspend fun syncNow(merchantId: String): Outcome<KioskMode> {
        synchronized(recordedNow) { recordedNow += merchantId }
        val outcome = nowScript.next()
        if (outcome is Outcome.Success) onSyncNow()
        return outcome
    }

    override suspend fun applyPending() {
        pendingApplied++
    }
}
