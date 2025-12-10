package com.tori.safety.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CommandProcessor(
    private val context: Context,
    private val tts: ToriTTS,
    private val geminiHelper: GeminiHelper
) {

    private val _processorState = MutableStateFlow<ProcessorState>(ProcessorState.Idle)
    val processorState: StateFlow<ProcessorState> = _processorState
    
    sealed class ProcessorState {
        object Idle : ProcessorState()
        data class Processing(val text: String) : ProcessorState()
        data class Result(val reply: String) : ProcessorState()
        data class Navigation(val destination: String) : ProcessorState()
        data class Error(val error: String) : ProcessorState()
    }

// ...

    private fun handleLocalCommand(text: String): Boolean {
        val lower = text.lowercase()
        return when {
            lower.contains("settings") -> {
                _processorState.value = ProcessorState.Navigation("SETTINGS")
                true
            }
            lower.contains("log") || lower.contains("history") -> {
                _processorState.value = ProcessorState.Navigation("TRIP_LOG")
                true
            }
            lower.contains("start monitoring") || lower.contains("drive mode") -> {
                 _processorState.value = ProcessorState.Navigation("MONITORING")
                true
            }
            lower.contains("contacts") -> {
                _processorState.value = ProcessorState.Navigation("CONTACTS")
                true
            }
             lower.contains("home") && !lower.contains("take me") -> { 
                _processorState.value = ProcessorState.Navigation("HOME")
                true
            }
            else -> false
        }
    }

    private fun handleNavigation(location: String?) {
        launchExternalNavigation(location)
    }

    private fun launchExternalNavigation(location: String?) {
        if (location.isNullOrBlank()) {
             // Maybe speak error or just return
             return
        }
        val gmmIntentUri = Uri.parse("google.navigation:q=$location")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            tts.speak("I couldn't find Google Maps on your device.")
        }
    }
}
