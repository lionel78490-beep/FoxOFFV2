package com.projectfox.foxoff.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SleepSessionEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sleepSessionDao(): SleepSessionDao
}
