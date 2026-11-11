package com.rynekryz.sniffsniff.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ConnectionEntity::class, AppUsageEntity::class], version = 1, exportSchema = false)
abstract class SniffDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
    abstract fun appUsageDao(): AppUsageDao
}
