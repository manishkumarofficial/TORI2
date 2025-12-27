package com.tori.safety.voice

import android.util.Log
import java.util.*

/**
 * Manages conversation context for Tori voice assistant
 * Maintains the last 3 interactions for context-aware responses
 */
class ConversationContextManager {
    
    private val interactions = mutableListOf<Interaction>()
    private val maxInteractions = 3
    
    fun addUserInput(input: String) {
        Log.d(TAG, "Adding user input: $input")
        
        // If the last interaction doesn't have a response yet, update it
        if (interactions.isNotEmpty() && interactions.last().assistantResponse.isEmpty()) {
            interactions[interactions.lastIndex] = interactions.last().copy(userInput = input)
        } else {
            // Create new interaction
            val interaction = Interaction(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                userInput = input,
                assistantResponse = ""
            )
            
            interactions.add(interaction)
        }
        
        // Keep only the last 3 interactions
        while (interactions.size > maxInteractions) {
            interactions.removeAt(0)
        }
    }
    
    fun addAssistantResponse(response: String) {
        Log.d(TAG, "Adding assistant response: $response")
        
        if (interactions.isNotEmpty()) {
            val lastInteraction = interactions.last()
            interactions[interactions.lastIndex] = lastInteraction.copy(assistantResponse = response)
        }
    }
    
    fun getContext(): ConversationContext {
        return ConversationContext(
            recentInteractions = interactions.toList(),
            sessionStartTime = getSessionStartTime(),
            interactionCount = interactions.size
        )
    }
    
    fun clearContext() {
        Log.d(TAG, "Clearing conversation context")
        interactions.clear()
    }
    
    private fun getSessionStartTime(): Long {
        return if (interactions.isNotEmpty()) {
            interactions.first().timestamp
        } else {
            System.currentTimeMillis()
        }
    }
    
    fun getLastUserInput(): String? {
        return interactions.lastOrNull()?.userInput
    }
    
    fun getLastAssistantResponse(): String? {
        return interactions.lastOrNull()?.assistantResponse
    }
    
    companion object {
        private const val TAG = "ConversationContextManager"
    }
}

data class Interaction(
    val id: String,
    val timestamp: Long,
    val userInput: String,
    val assistantResponse: String
)

data class ConversationContext(
    val recentInteractions: List<Interaction>,
    val sessionStartTime: Long,
    val interactionCount: Int
)