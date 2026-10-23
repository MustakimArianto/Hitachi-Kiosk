package com.hitachi.kiosk.core.domain.repository

import com.hitachi.kiosk.core.domain.model.Catalog
import kotlinx.coroutines.flow.Flow

/**
 * The cached catalog the customer screens browse (FR-009–FR-011, FR-028). It works offline (D-11) and is held in
 * memory after the first read, so a category switch never touches the disk (TR-PRF-04).
 */
interface CatalogRepository {
    /**
     * The current catalog, or `null` when nothing is cached (before the first sync, or after Log out). It changes
     * only when a sync is applied, which never happens during an order (A-29).
     */
    val catalog: Flow<Catalog?>
}
