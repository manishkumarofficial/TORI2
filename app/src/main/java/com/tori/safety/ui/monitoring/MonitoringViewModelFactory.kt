package com.tori.safety.ui.monitoring

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MonitoringViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MonitoringViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MonitoringViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}