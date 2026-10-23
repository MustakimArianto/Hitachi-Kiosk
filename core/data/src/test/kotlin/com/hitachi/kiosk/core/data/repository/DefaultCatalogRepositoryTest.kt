package com.hitachi.kiosk.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.api.KioskJson
import com.hitachi.kiosk.core.data.db.KioskDatabase
import com.hitachi.kiosk.core.data.mapper.CatalogStamp
import com.hitachi.kiosk.core.data.mapper.toRows
import com.hitachi.kiosk.core.domain.model.Catalog
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The cache read side (DEV-021): one Room read, then the memory copy; unreadable rows are an empty cache. */
@RunWith(RobolectricTestRunner::class)
class DefaultCatalogRepositoryTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KioskDatabase::class.java)
            .build()
    private val repository = DefaultCatalogRepository(database.catalogDao())
    private val stored = StoredCatalog(SampleData.catalog, CatalogStamp("\"cfg-42\"", "\"menu-118\"", 1_000))

    @After
    fun close() = database.close()

    @Test
    fun `an empty cache serves nothing, then the stored catalog`() =
        runTest {
            assertThat(repository.catalog.first()).isNull()

            repository.replace(stored)

            assertThat(repository.catalog.first()).isEqualTo(SampleData.catalog)
        }

    @Test
    fun `a stored catalog is read back after a restart`() =
        runTest {
            repository.replace(stored)

            val restarted = DefaultCatalogRepository(database.catalogDao())

            assertThat(restarted.stored()).isEqualTo(stored)
            assertThat(restarted.catalog.first()).isEqualTo(SampleData.catalog)
        }

    @Test
    fun `readers see a replace and a clear`() =
        runTest {
            val seen = mutableListOf<Catalog?>()
            val collector = launch { repository.catalog.take(3).toList(seen) }
            testScheduler.runCurrent()

            repository.replace(stored)
            testScheduler.runCurrent()
            repository.clear()
            collector.join()

            assertThat(seen).containsExactly(null, SampleData.catalog, null).inOrder()
            assertThat(repository.stored()).isNull()
        }

    @Test
    fun `unreadable rows read as an empty cache`() =
        runTest {
            val rows = stored.catalog.toRows(stored.stamp, KioskJson)
            database.catalogDao().replaceAll(rows.copy(config = rows.config.copy(json = "{")))

            assertThat(DefaultCatalogRepository(database.catalogDao()).stored()).isNull()
        }
}
