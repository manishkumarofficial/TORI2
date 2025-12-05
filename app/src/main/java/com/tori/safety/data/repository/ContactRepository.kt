package com.tori.safety.data.repository

import com.tori.safety.data.database.ContactDao
import com.tori.safety.data.model.EmergencyContact
import kotlinx.coroutines.flow.Flow
/**
 * Repository for emergency contacts
 */
class ContactRepository(
    private val contactDao: ContactDao
) {
    
    fun getAllContacts(): Flow<List<EmergencyContact>> {
        return contactDao.getAllContacts()
    }
    
    suspend fun getPrimaryContact(): EmergencyContact? {
        return contactDao.getPrimaryContact()
    }
    
    suspend fun getContactById(id: Long): EmergencyContact? {
        return contactDao.getContactById(id)
    }
    
    suspend fun insertContact(contact: EmergencyContact): Long {
        return contactDao.insertContact(contact)
    }
    
    suspend fun updateContact(contact: EmergencyContact) {
        contactDao.updateContact(contact)
    }
    
    suspend fun deleteContact(contact: EmergencyContact) {
        contactDao.deleteContact(contact)
    }
    
    suspend fun deleteContactById(id: Long) {
        contactDao.deleteContactById(id)
    }
    
    suspend fun setPrimaryContact(contactId: Long) {
        // Clear all primary contacts first
        contactDao.clearPrimaryContacts()
        
        // Set the new primary contact
        val contact = contactDao.getContactById(contactId)
        contact?.let {
            val updatedContact = it.copy(isPrimary = true)
            contactDao.updateContact(updatedContact)
        }
    }
    
    suspend fun getContactCount(): Int {
        return contactDao.getContactCount()
    }
}
