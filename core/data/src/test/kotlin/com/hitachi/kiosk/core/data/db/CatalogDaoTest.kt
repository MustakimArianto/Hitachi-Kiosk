package com.hitachi.kiosk.core.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.api.KioskJson
import com.hitachi.kiosk.core.data.mapper.CatalogStamp
import com.hitachi.kiosk.core.data.mapper.toCatalog
import com.hitachi.kiosk.core.data.mapper.toRows
import com.hitachi.kiosk.core.domain.model.Rupiah
import com.hitachi.kiosk.core.testing.fixture.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** DEV-021: the catalog tables round-trip the design menu and replace it atomically (TR-DAT-03, TR-REL-07). */
@RunWith(RobolectricTestRunner::class)
class CatalogDaoTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KioskDatabase::class.java)
            .build()
    private val dao = database.catalogDao()
    private val stamp = CatalogStamp("\"cfg-42\"", "\"menu-118\"", 1_000)

    @After
    fun close() = database.close()

    @Test
    fun `the sample catalog round-trips through every table`() =
        runTest {
            dao.replaceAll(SampleData.catalog.toRows(stamp, KioskJson))

            val rows = dao.readAll()!!

            assertThat(rows.toCatalog(KioskJson)).isEqualTo(SampleData.catalog)
            assertThat(dao.configEtag(SampleData.MERCHANT_ID)).isEqualTo("\"cfg-42\"")
            assertThat(dao.menuEtag(SampleData.MERCHANT_ID)).isEqualTo("\"menu-118\"")
            assertThat(dao.configEtag("mer_other")).isNull()
        }

    @Test
    fun `a replace swaps the whole menu`() =
        runTest {
            dao.replaceAll(SampleData.catalog.toRows(stamp, KioskJson))
            val smaller =
                SampleData.catalog.copy(
                    menu =
                        SampleData.menu.copy(
                            menuVersion = "menu-119",
                            products =
                                SampleData.menu.products
                                    .take(2)
                                    .map { it.copy(price = Rupiah(1)) },
                        ),
                )

            dao.replaceAll(smaller.toRows(stamp, KioskJson))

            assertThat(dao.readAll()!!.toCatalog(KioskJson)).isEqualTo(smaller)
        }

    @Test
    fun `a failed replace leaves the old version intact`() =
        runTest {
            dao.replaceAll(SampleData.catalog.toRows(stamp, KioskJson))
            val good = SampleData.catalog.copy(menu = SampleData.menu.copy(menuVersion = "menu-119"))
            val broken = good.toRows(stamp, KioskJson).let { it.copy(products = it.products + it.products.first()) }

            val error = runCatching { dao.replaceAll(broken) }.exceptionOrNull()

            assertThat(error).isInstanceOf(SQLiteConstraintException::class.java)

            assertThat(dao.readAll()!!.toCatalog(KioskJson)).isEqualTo(SampleData.catalog)
        }

    @Test
    fun `an empty or half-written cache reads as nothing`() =
        runTest {
            assertThat(dao.readAll()).isNull()

            dao.replaceAll(SampleData.catalog.toRows(stamp, KioskJson))
            dao.deleteAll()

            assertThat(dao.readAll()).isNull()
        }
}
