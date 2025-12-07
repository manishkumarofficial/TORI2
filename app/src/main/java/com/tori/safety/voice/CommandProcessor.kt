package com.tori.safety.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
        data class Result(val reply: String, val data: Any? = null) : ProcessorState()
        data class Error(val message: String) : ProcessorState()
    }

    fun process(text: String) {
        _processorState.value = ProcessorState.Processing(text)
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = geminiHelper.processCommand(text)
                
                // Speak the reply
                tts.speak(response.reply)
                
                // Handle the intent
                when (response.intent) {
                    GeminiHelper.IntentType.NAVIGATE -> {
                        response.data?.let { location ->
                            launchNavigation(location)
                        }
                    }
                    GeminiHelper.IntentType.SEARCH -> {
                        // For now, just show the reply or mock a list
                        // In a real app, we'd query Places API here
                        _processorState.value = ProcessorState.Result(response.reply, response.data)
                    }
                    else -> {
                        _processorState.value = ProcessorState.Result(response.reply)
                    }
                }
                
            } catch (e: Exception) {
                Log.e("CommandProcessor", "Error processing command", e)
                val errorMsg = "Sorry, something went wrong."
                tts.speak(errorMsg)
                _processorState.value = ProcessorState.Error(errorMsg)
            }
        }
    }

    private fun launchNavigation(location: String) {
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
