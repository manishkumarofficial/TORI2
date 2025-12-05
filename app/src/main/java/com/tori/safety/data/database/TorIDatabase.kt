package com.tori.safety.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tori.safety.data.model.AlertEvent
import com.tori.safety.data.model.EmergencyContact
import com.tori.safety.data.model.TripLog

/**
 * Room database for TOR-I app
 */
@Database(
    entities = [
        EmergencyContact::class,
        TripLog::class,
        AlertEvent::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TorIDatabase : RoomDatabase() {
    
    abstract fun contactDao(): ContactDao
    abstract fun tripLogDao(): TripLogDao
    abstract fun alertEventDao(): AlertEventDao
    
    companion object {
        @Volatile
        private var INSTANCE: TorIDatabase? = null
        
        fun getDatabase(context: Context): TorIDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TorIDatabase::class.java,
                    "tori_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
