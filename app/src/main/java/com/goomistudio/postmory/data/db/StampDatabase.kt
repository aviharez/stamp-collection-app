package com.goomistudio.postmory.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(entities = [AlbumEntity::class, StampEntity::class, ScrapPageEntity::class], version = 4, exportSchema = true)
abstract class StampDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun stampDao(): StampDao
    abstract fun scrapPageDao(): ScrapPageDao

    companion object {
        const val NAME = "stamps.db"

        /** v2: stamps.created_day. Existing stamps get their own day, so they keep counting for the streak. */
        val Migration1To2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE stamps ADD COLUMN created_day TEXT NOT NULL DEFAULT ''")
                connection.execSQL("UPDATE stamps SET created_day = day")
            }
        }

        /** v3: stamps.filter + stamps.render_spec (older stamps: Original, no spec until reframed). */
        val Migration2To3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE stamps ADD COLUMN filter TEXT NOT NULL DEFAULT 'Original'")
                connection.execSQL("ALTER TABLE stamps ADD COLUMN render_spec TEXT")
            }
        }

        /** v4: scrapbook pages (same SQL as the generated schema 4.json). */
        val Migration3To4 = object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scrap_pages` (`id` TEXT NOT NULL, `album_id` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `items` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`album_id`) REFERENCES `albums`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_scrap_pages_album_id` ON `scrap_pages` (`album_id`)")
            }
        }
    }
}
