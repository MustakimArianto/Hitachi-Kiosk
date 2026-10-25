package com.hitachi.kiosk.core.data.db

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 → 2 (0.5.0): adds `pending_order` (technical-design §6.1). The catalog cache tables are untouched, so an
 * in-place upgrade keeps the cache and the next launch needs no download (TR-REL-09, TR-DAT-04). The SQL is the
 * `createSql` of `schemas/…/2.json`; `KioskDatabaseMigrationTest` checks that Room accepts the migrated database.
 */
internal val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_order` (`orderId` TEXT NOT NULL, `orderNumber` TEXT NOT NULL, " +
                    "`paymentId` TEXT, `method` TEXT, `idempotencyKey` TEXT NOT NULL, `totalsJson` TEXT NOT NULL, " +
                    "`createdAt` TEXT NOT NULL, PRIMARY KEY(`orderId`))",
            )
        }
    }

/**
 * Version 2 → 3 (0.6.0): adds the optional `modifier_group.purpose` (api-spec E6, OQ-17, DEV-114). Existing rows get
 * `NULL`, a plain group, so a cached menu keeps working and only a refreshed menu turns the meal card on. The SQL is
 * the one Room writes into `schemas/…/3.json`.
 */
internal val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `modifier_group` ADD COLUMN `purpose` TEXT")
        }
    }

/** Every migration of [KioskDatabase], oldest first. */
internal val KIOSK_MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3)

/**
 * Registers [KIOSK_MIGRATIONS]. `DatabaseModule` and the tests use it, so they open the database the same way; no
 * destructive fallback exists (TR-DAT-04).
 */
internal fun RoomDatabase.Builder<KioskDatabase>.addKioskMigrations(): RoomDatabase.Builder<KioskDatabase> =
    apply { KIOSK_MIGRATIONS.forEach { addMigrations(it) } }
