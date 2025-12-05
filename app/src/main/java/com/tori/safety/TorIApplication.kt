package com.tori.safety

import android.app.Application
import com.tori.safety.data.database.TorIDatabase
import com.tori.safety.data.repository.ContactRepository
import com.tori.safety.data.repository.TripLogRepository

/**
 * TOR-I Application class for dependency injection and initialization
 */
class TorIApplication : Application() {
    
    // Database instance
    val database: TorIDatabase by lazy { TorIDatabase.getDatabase(this) }
    
    // Repository instances
    val contactRepository: ContactRepository by lazy { 
        ContactRepository(database.contactDao()) 
    }
    
    val tripLogRepository: TripLogRepository by lazy { 
        TripLogRepository(database.tripLogDao()) 
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: TorIApplication
            private set
    }
}
