package com.aviharez.stamp.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(entities = [AlbumEntity::class, StampEntity::class], version = 1, exportSchema = true)
abstract class StampDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun stampDao(): StampDao

    companion object {
        const val NAME = "stamps.db"
    }
}
