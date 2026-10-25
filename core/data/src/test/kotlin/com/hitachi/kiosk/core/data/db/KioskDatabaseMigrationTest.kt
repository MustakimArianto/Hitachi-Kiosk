package com.hitachi.kiosk.core.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.hitachi.kiosk.core.data.repository.RoomPendingOrderStore
import com.hitachi.kiosk.core.data.repository.SamplePendingOrder
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 1 → 2 (0.5.0) adds `pending_order` and keeps the cache tables (technical-design §6.1, TR-DAT-04, TR-REL-09); 2 → 3
 * (0.6.0) adds `modifier_group.purpose`. The helper reads the committed `schemas/…/N.json` files and validates each
 * migrated database against its target version.
 */
@RunWith(RobolectricTestRunner::class)
class KioskDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KioskDatabase::class.java)

    @Test
    fun `migrating 1 to 2 adds an empty pending_order and keeps the cache rows`() {
        helper.createDatabase(DB, 1).apply {
            insert(
                "config",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("merchantId", "mer_urbangrill")
                    put("etag", "\"cfg-42\"")
                    put("json", "{}")
                    put("fetchedAtMillis", 1_000L)
                },
            )
            insert(
                "category",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "cat_burgers")
                    put("position", 0)
                    put("sort", 1)
                    put("nameEn", "Burgers")
                },
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        migrated.query("SELECT merchantId, etag FROM config").use {
            assertThat(it.count).isEqualTo(1)
            it.moveToFirst()
            assertThat(it.getString(0)).isEqualTo("mer_urbangrill")
            assertThat(it.getString(1)).isEqualTo("\"cfg-42\"")
        }
        migrated.query("SELECT nameEn FROM category").use {
            it.moveToFirst()
            assertThat(it.getString(0)).isEqualTo("Burgers")
        }
        migrated.query("SELECT COUNT(*) FROM pending_order").use {
            it.moveToFirst()
            assertThat(it.getInt(0)).isEqualTo(0)
        }
        migrated.close()
    }

    @Test
    fun `the migrated file opens with the real database and stores a pending order`() =
        runTest {
            helper.createDatabase(DB, 1).close()

            val database =
                Room
                    .databaseBuilder(ApplicationProvider.getApplicationContext(), KioskDatabase::class.java, DB)
                    .addKioskMigrations()
                    .build()
            try {
                val store = RoomPendingOrderStore(database.pendingOrderDao())
                assertThat(store.read()).isNull()

                store.replace(SamplePendingOrder)

                assertThat(store.read()).isEqualTo(SamplePendingOrder)
                assertThat(database.catalogDao().readAll()).isNull()
            } finally {
                database.close()
            }
        }

    @Test
    fun `a pending order written at version 2 survives reopening the database`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()

            fun open() =
                Room
                    .databaseBuilder(context, KioskDatabase::class.java, DB)
                    .addKioskMigrations()
                    .build()

            open().also {
                RoomPendingOrderStore(it.pendingOrderDao()).replace(SamplePendingOrder)
                it.close()
            }

            val reopened = open()
            try {
                assertThat(RoomPendingOrderStore(reopened.pendingOrderDao()).read()).isEqualTo(SamplePendingOrder)
            } finally {
                reopened.close()
            }
        }

    @Test
    fun `migrating 2 to 3 adds a null purpose to the modifier groups and keeps the rows (DEV-114)`() {
        helper.createDatabase(DB, 2).apply {
            execSQL(
                "INSERT INTO modifier_group (id, position, nameEn, nameId, type, required, min, max) " +
                    "VALUES ('mg_meal_upgrade', 0, 'Make it a meal?', NULL, 'SINGLE', 1, 1, 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3)

        migrated.query("SELECT id, nameEn, purpose FROM modifier_group").use {
            assertThat(it.count).isEqualTo(1)
            it.moveToFirst()
            assertThat(it.getString(0)).isEqualTo("mg_meal_upgrade")
            assertThat(it.getString(1)).isEqualTo("Make it a meal?")
            assertThat(it.isNull(2)).isTrue()
        }
        migrated.close()
    }

    @Test
    fun `the registered migrations are exactly 1 to 2 and 2 to 3`() {
        assertThat(KIOSK_MIGRATIONS.map { it.startVersion to it.endVersion }).containsExactly(1 to 2, 2 to 3).inOrder()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
