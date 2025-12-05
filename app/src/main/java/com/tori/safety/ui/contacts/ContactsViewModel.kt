package com.tori.safety.ui.contacts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tori.safety.TorIApplication
import com.tori.safety.data.model.EmergencyContact
import com.tori.safety.data.repository.ContactRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for ContactsActivity
 */
class ContactsViewModel : ViewModel() {
    
    private val application = TorIApplication.instance
    private val contactRepository: ContactRepository = application.contactRepository
    
    private val _contacts = MutableLiveData<List<EmergencyContact>>()
    val contacts: LiveData<List<EmergencyContact>> = _contacts
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private val _successMessage = MutableLiveData<String>()
    val successMessage: LiveData<String> = _successMessage
    
    init {
        loadContacts()
    }
    
    fun loadContacts() {
        viewModelScope.launch {
            try {
                contactRepository.getAllContacts().collect { contactList ->
                    _contacts.value = contactList
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load contacts: ${e.message}"
            }
        }
    }
    
    fun deleteContact(contact: EmergencyContact) {
        viewModelScope.launch {
            try {
                contactRepository.deleteContact(contact)
                _successMessage.value = "Contact deleted successfully"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete contact: ${e.message}"
            }
        }
    }
    
    fun setPrimaryContact(contactId: Long) {
        viewModelScope.launch {
            try {
                contactRepository.setPrimaryContact(contactId)
                _successMessage.value = "Primary contact updated"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to set primary contact: ${e.message}"
            }
        }
    }
    
    fun addContact(name: String, phoneNumber: String) {
        viewModelScope.launch {
            try {
                val contact = EmergencyContact(name = name, phoneNumber = phoneNumber, relationship = "Other")
                contactRepository.insertContact(contact)
                _successMessage.value = "Contact added successfully"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add contact: ${e.message}"
            }
        }
    }
    
    fun clearMessages() {
        _errorMessage.value = ""
        _successMessage.value = ""
    }
}
