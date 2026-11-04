package com.aviharez.stamp.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(entities = [AlbumEntity::class, StampEntity::class], version = 2, exportSchema = true)
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
    }
}
