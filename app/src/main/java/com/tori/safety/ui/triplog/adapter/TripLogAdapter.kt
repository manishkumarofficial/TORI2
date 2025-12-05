package com.tori.safety.ui.triplog.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tori.safety.data.model.TripLog
import com.tori.safety.databinding.ItemTripLogBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView adapter for trip logs
 */
class TripLogAdapter(
    private val onTripClick: (TripLog) -> Unit
) : ListAdapter<TripLog, TripLogAdapter.TripLogViewHolder>(TripLogDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripLogViewHolder {
        val binding = ItemTripLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TripLogViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TripLogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class TripLogViewHolder(
        private val binding: ItemTripLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(tripLog: TripLog) {
            binding.apply {
                // Format and display trip information
                tvTripDate.text = formatDate(tripLog.startTime)
                tvStartTime.text = formatTime(tripLog.startTime)
                tvDuration.text = formatDuration(tripLog.duration)
                tvAlertCount.text = tripLog.alertCount.toString()
                tvSosCount.text = tripLog.sosCount.toString()
                
                // Set status
                val status = if (tripLog.isCompleted) "Completed" else "In Progress"
                tvTripStatus.text = status
                
                // Set click listener
                root.setOnClickListener {
                    onTripClick(tripLog)
                }
            }
        }
        
        private fun formatDate(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
        
        private fun formatTime(timestamp: Long): String {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            return timeFormat.format(Date(timestamp))
        }
        
        private fun formatDuration(durationMs: Long): String {
            val hours = durationMs / (1000 * 60 * 60)
            val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
            return "${hours}h ${minutes}m"
        }
    }
    
    private class TripLogDiffCallback : DiffUtil.ItemCallback<TripLog>() {
        override fun areItemsTheSame(oldItem: TripLog, newItem: TripLog): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: TripLog, newItem: TripLog): Boolean {
            return oldItem == newItem
        }
    }
}
