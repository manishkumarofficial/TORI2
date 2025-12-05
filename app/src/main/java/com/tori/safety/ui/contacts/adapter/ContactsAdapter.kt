package com.tori.safety.ui.contacts.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tori.safety.data.model.EmergencyContact
import com.tori.safety.databinding.ItemContactBinding

/**
 * RecyclerView adapter for emergency contacts
 */
class ContactsAdapter(
    private val onEditContact: (EmergencyContact) -> Unit,
    private val onDeleteContact: (EmergencyContact) -> Unit,
    private val onSetPrimary: (EmergencyContact) -> Unit
) : ListAdapter<EmergencyContact, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(contact: EmergencyContact) {
            binding.apply {
                tvContactName.text = contact.name
                tvContactPhone.text = contact.phoneNumber
                tvContactRelationship.text = contact.relationship
                
                // Show primary badge if this is the primary contact
                tvPrimaryBadge.visibility = if (contact.isPrimary) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                
                // Set up click listeners
                btnEdit.setOnClickListener {
                    onEditContact(contact)
                }
                
                btnDelete.setOnClickListener {
                    onDeleteContact(contact)
                }
                
                // Make the entire card clickable for setting primary
                root.setOnClickListener {
                    if (!contact.isPrimary) {
                        onSetPrimary(contact)
                    }
                }
            }
        }
    }
    
    private class ContactDiffCallback : DiffUtil.ItemCallback<EmergencyContact>() {
        override fun areItemsTheSame(oldItem: EmergencyContact, newItem: EmergencyContact): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: EmergencyContact, newItem: EmergencyContact): Boolean {
            return oldItem == newItem
        }
    }
}
