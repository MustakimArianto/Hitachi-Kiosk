package com.hitachi.kiosk.core.domain.repository

import com.hitachi.kiosk.core.domain.Outcome
import com.hitachi.kiosk.core.domain.model.KioskMode

/**
 * Downloads the merchant config (E5) and menu (E6) together (FR-028). In 0.2.0 it only makes the calls and keeps
 * the result in memory, plus the operating mode (FR-005); DEV-021/DEV-022 (0.3.0) add the Room cache, ETags and
 * the sync triggers.
 */
fun interface CatalogSyncRepository {
    /** E5 then E6 for [merchantId], each retried on transient errors. Returns the config's operating mode. */
    suspend fun sync(merchantId: String): Outcome<KioskMode>
}
