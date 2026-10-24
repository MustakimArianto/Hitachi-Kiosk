package com.hitachi.kiosk.core.domain.repository

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.model.KioskMode

/**
 * Downloads the merchant config (E5) and menu (E6) together and replaces the cached catalog that
 * [CatalogRepository] serves (FR-028, DEV-022). Runs on binding, at startup, every 15 minutes and on reconnect.
 */
interface CatalogSyncRepository {
    /**
     * E5 then E6 for [merchantId] with `If-None-Match`, each retried on transient errors (technical-design §7.1).
     * Both are stored in one transaction, so a failure keeps the previous catalog (TR-REL-07). While an order is in
     * progress the new catalog is kept aside and applied by [applyPending] when the order ends (A-29). Concurrent
     * calls run one after the other. Returns the config's operating mode (FR-005).
     */
    suspend fun sync(merchantId: String): Outcome<KioskMode>

    /**
     * [sync], but the new catalog is stored at once even while an order is in progress, together with any catalog held
     * back earlier. This is the one exception to A-29: after E7 answered `PRICE_CHANGED` the order has to be re-priced
     * from the backend's current menu (FR-016, DEV-046). A failure keeps the previous catalog.
     */
    suspend fun syncNow(merchantId: String): Outcome<KioskMode>

    /** Stores a catalog that arrived during an order, if there is one (FR-028, A-29). Call it when the order ends. */
    suspend fun applyPending()
}
