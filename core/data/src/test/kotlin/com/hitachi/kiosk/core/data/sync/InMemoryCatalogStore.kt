package com.hitachi.kiosk.core.data.sync

import android.database.sqlite.SQLiteException
import com.hitachi.kiosk.core.data.repository.CatalogStore
import com.hitachi.kiosk.core.data.repository.StoredCatalog

/** A [CatalogStore] in memory, for sync tests on the JVM. [failWrites] makes [replace] fail like a broken disk. */
internal class InMemoryCatalogStore(
    var stored: StoredCatalog? = null,
) : CatalogStore {
    var failWrites = false
    var writes = 0
        private set
    var clears = 0
        private set

    override suspend fun stored(): StoredCatalog? = stored

    override suspend fun replace(stored: StoredCatalog) {
        if (failWrites) throw SQLiteException("disk full")
        writes++
        this.stored = stored
    }

    override suspend fun clear() {
        clears++
        stored = null
    }
}
