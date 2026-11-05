package com.aviharez.stamp.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(entities = [AlbumEntity::class, StampEntity::class], version = 3, exportSchema = true)
abstract class StampDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun stampDao(): StampDao

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
    }
}
