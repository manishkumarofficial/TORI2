package com.tori.safety.voice

import android.content.Context
import android.util.Log
import com.google.genai.Client
import com.google.genai.types.GenerateContentResponse
import com.tori.safety.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini AI processor for Tori voice assistant.
 *
 * Uses the NEW com.google.genai:google-genai SDK (replaces deprecated
 * com.google.ai.client.generativeai which was failing silently).
 */
class GeminiProcessor(private val context: Context) {
    
    private var client: Client? = null
    private var isInitialized = false
    private var modelName = "gemini-2.0-flash"
    
    private val apiKey = BuildConfig.GEMINI_API_KEY
    
    suspend fun initialize() {
        Log.d(TAG, "Initializing Gemini AI processor...")
        Log.d(TAG, "API key length: ${apiKey.length}, prefix: ${apiKey.take(8)}...")
        
        try {
            if (apiKey.isEmpty() || apiKey == "YOUR_API_KEY_HERE") {
                Log.e(TAG, "API key missing or placeholder — fallback mode")
                isInitialized = true
                return
            }
            
            // Create new GenAI client
            client = Client.builder()
                .apiKey(apiKey)
                .build()
            
            isInitialized = true
            Log.d(TAG, "Gemini client created successfully")
            
            // Verify with a quick test call
            try {
                val testResponse = client!!.models.generateContent(
                    modelName,
                    "Say hello in one word",
                    null
                )
                Log.d(TAG, "API verification OK — model $modelName works. Response: '${testResponse.text()}'")
            } catch (e: Exception) {
                Log.w(TAG, "Model $modelName failed: ${e.message}, trying gemini-1.5-flash...")
                modelName = "gemini-1.5-flash"
                try {
                    val testResponse = client!!.models.generateContent(
                        modelName,
                        "Say hello in one word",
                        null
                    )
                    Log.d(TAG, "Fallback model $modelName works. Response: '${testResponse.text()}'")
                } catch (e2: Exception) {
                    Log.e(TAG, "Both models failed! API error: ${e2.message}", e2)
                    client = null // Force fallback mode
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Gemini client: ${e.message}", e)
            isInitialized = true
        }
    }
    
    suspend fun processCommand(
        userInput: String,
        context: ConversationContext
    ): GeminiResponse = withContext(Dispatchers.IO) {
        
        if (!isInitialized) {
            throw IllegalStateException("Gemini processor not initialized")
        }
        
        val geminiClient = client
        if (geminiClient == null) {
            Log.w(TAG, "No Gemini client, using fallback for: $userInput")
            return@withContext getFallbackResponse(userInput)
        }
        
        try {
            Log.d(TAG, "Sending to Gemini ($modelName): '$userInput'")
            
            val prompt = buildPrompt(userInput, context)
            
            val response: GenerateContentResponse = geminiClient.models.generateContent(
                modelName,
                prompt,
                null
            )
            
            val responseText = response.text()
            
            if (responseText.isNullOrBlank()) {
                Log.w(TAG, "Gemini returned empty — using fallback")
                return@withContext getFallbackResponse(userInput)
            }
            
            // Clean markdown from response (TTS can't speak asterisks)
            val cleanText = responseText
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("\\*(.+?)\\*"), "$1")
                .replace(Regex("#+ "), "")
                .trim()
            
            Log.d(TAG, "Gemini response (${cleanText.length} chars): '$cleanText'")
            
            val intent = determineIntent(userInput)
            val data = extractData(userInput, intent)
            
            return@withContext GeminiResponse(
                message = cleanText,
                intent = intent,
                data = data
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            return@withContext getFallbackResponse(userInput)
        }
    }
    
    private fun buildPrompt(userInput: String, context: ConversationContext): String {
        val contextHistory = context.recentInteractions.takeLast(3)
            .joinToString("\n") { "User: ${it.userInput}\nTori: ${it.assistantResponse}" }
        
        val systemContext = """
            You are Tori, an AI voice assistant in a driving safety app called TOR-I.
            You are like JARVIS from Iron Man — helpful, knowledgeable, and slightly witty.
            RULES:
            - ALWAYS answer the user's question directly and accurately.
            - Keep responses concise (2-3 sentences) since they will be spoken via TTS.
            - NEVER echo back what the user said without answering.
            - Be conversational, not robotic.
            - For driving-related requests, be proactive about helping.
            - Use the driver's name (Manish) occasionally.
        """.trimIndent()
        
        return if (contextHistory.isBlank()) {
            "$systemContext\n\nUser: $userInput\nTori:"
        } else {
            "$systemContext\n\nPrevious conversation:\n$contextHistory\n\nUser: $userInput\nTori:"
        }
    }
    
    private fun getFallbackResponse(userInput: String): GeminiResponse {
        val input = userInput.lowercase()
        
        val (message, intent) = when {
            input.contains("tired") || input.contains("sleepy") -> {
                "I understand you're feeling tired. Let me help you find a rest area nearby." to Intent.WELLNESS_TIRED
            }
            input.contains("hungry") || input.contains("food") -> {
                "Got it! I'll help you find some food options nearby." to Intent.WELLNESS_HUNGRY
            }
            input.contains("navigate") || input.contains("directions") || input.contains("take me") -> {
                "Sure! I can help you navigate. Where would you like to go?" to Intent.NAVIGATION
            }
            input.contains("find") || input.contains("search") || input.contains("nearby") -> {
                "I'll help you search for that. What are you looking for?" to Intent.SEARCH
            }
            input.contains("weather") -> "Let me check the weather for you." to Intent.WEATHER
            input.contains("traffic") -> "I'll check the traffic conditions ahead." to Intent.TRAFFIC
            input.contains("emergency") || input.contains("help me") -> {
                "I'm here to help. Do you need emergency assistance?" to Intent.EMERGENCY
            }
            input.contains("name") && (input.contains("your") || input.contains("you")) -> {
                "I'm Tori, your AI driving companion! Think of me as your co-pilot, here to keep you safe on the road." to Intent.GENERAL
            }
            input.contains("hello") || input.contains("hi ") || input.startsWith("hi") -> {
                "Hey there! I'm Tori, your driving assistant. How can I help you today?" to Intent.GENERAL
            }
            input.contains("thank") -> {
                "You're welcome! Always happy to help." to Intent.GENERAL
            }
            else -> {
                "I'm sorry, I'm having trouble connecting to my AI right now. I can still help with navigation, finding places, and safety. What do you need?" to Intent.GENERAL
            }
        }
        
        val data = extractData(userInput, intent)
        return GeminiResponse(message = message, intent = intent, data = data)
    }
    
    private fun determineIntent(userInput: String): Intent {
        val input = userInput.lowercase()
        return when {
            input.contains("tired") || input.contains("sleepy") || input.contains("fatigue") -> Intent.WELLNESS_TIRED
            input.contains("hungry") || input.contains("food") || input.contains("eat") -> Intent.WELLNESS_HUNGRY
            input.contains("navigate") || input.contains("directions") || input.contains("take me") -> Intent.NAVIGATION
            input.contains("find") || input.contains("search") || input.contains("nearby") -> Intent.SEARCH
            input.contains("weather") -> Intent.WEATHER
            input.contains("traffic") -> Intent.TRAFFIC
            input.contains("emergency") || input.contains("sos") -> Intent.EMERGENCY
            input.contains("where am i") || input.contains("location") -> Intent.LOCATION
            else -> Intent.GENERAL
        }
    }
    
    private fun extractData(userInput: String, intent: Intent): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        when (intent) {
            Intent.NAVIGATION, Intent.SEARCH -> {
                data["query"] = userInput
                data["needsLocationSearch"] = true
            }
            Intent.WELLNESS_TIRED -> {
                data["wellnessType"] = "tired"
                data["needsRestAreaSearch"] = true
            }
            Intent.WELLNESS_HUNGRY -> {
                data["wellnessType"] = "hungry"
                data["needsFoodSearch"] = true
            }
            Intent.EMERGENCY -> {
                data["emergencyType"] = "general"
                data["needsSOSActivation"] = true
            }
            else -> {}
        }
        return data
    }
    
    companion object {
        private const val TAG = "GeminiProcessor"
    }
}

data class GeminiResponse(
    val message: String,
    val intent: Intent,
    val data: Map<String, Any> = emptyMap()
)

data class ParsedResponse(
    val message: String,
    val intent: Intent,
    val data: Map<String, Any>
)

enum class Intent {
    NAVIGATION, SEARCH, WELLNESS_TIRED, WELLNESS_HUNGRY,
    WEATHER, TRAFFIC, EMERGENCY, LOCATION, GENERAL, ERROR
}