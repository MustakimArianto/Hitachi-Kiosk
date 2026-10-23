package com.hitachi.kiosk.core.testing.fake

import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.domain.repository.CatalogRepository
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A [CatalogRepository] a test fills directly (NFR-012). Starts with the api-spec sample catalog. */
class FakeCatalogRepository(
    initial: Catalog? = SampleData.catalog,
) : CatalogRepository {
    private val state = MutableStateFlow(initial)

    override val catalog: StateFlow<Catalog?> = state

    fun set(catalog: Catalog?) {
        state.value = catalog
    }
}
