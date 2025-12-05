package com.tori.safety.ui.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.tori.safety.R
import com.tori.safety.data.model.EmergencyContact
import com.tori.safety.databinding.ActivityContactsBinding
import com.tori.safety.ui.contacts.adapter.ContactsAdapter

/**
 * Activity for managing emergency contacts
 */
class ContactsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityContactsBinding
    private val viewModel: ContactsViewModel by viewModels()
    private lateinit var adapter: ContactsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupObservers()
    }
    
    private fun setupUI() {
        // Toolbar
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddContact.setOnClickListener { showAddContactDialog() }
        binding.btnAddFirstContact.setOnClickListener { showAddContactDialog() }
        
        // RecyclerView
        adapter = ContactsAdapter(
            onEditContact = { /* TODO: Implement edit */ },
            onDeleteContact = { contact -> showDeleteConfirmation(contact) },
            onSetPrimary = { contact -> viewModel.setPrimaryContact(contact.id) }
        )
        
        binding.recyclerContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerContacts.adapter = adapter
    }
    
    private fun setupObservers() {
        viewModel.contacts.observe(this) { contacts ->
            adapter.submitList(contacts)
            updateEmptyState(contacts.isEmpty())
        }
        
        viewModel.successMessage.observe(this) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }
        
        viewModel.errorMessage.observe(this) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.recyclerContacts.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
        } else {
            binding.recyclerContacts.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
        }
    }
    
    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_contact_name)
        val etPhone = dialogView.findViewById<EditText>(R.id.et_contact_phone)
        
        AlertDialog.Builder(this, R.style.Theme_TORI_AlertDialog)
            .setTitle("Add Emergency Contact")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    viewModel.addContact(name, phone)
                } else {
                    Toast.makeText(this, "Please enter both name and phone number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDeleteConfirmation(contact: EmergencyContact) {
        AlertDialog.Builder(this, R.style.Theme_TORI_AlertDialog)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete ${contact.name}?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteContact(contact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}