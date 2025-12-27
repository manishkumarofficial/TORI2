package com.tori.safety.voice

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini AI processor for Tori voice assistant
 * Handles natural language processing and response generation
 */
class GeminiProcessor(private val context: Context) {
    
    private lateinit var generativeModel: GenerativeModel
    private var isInitialized = false
    
    // You need to add your Gemini API key here
    // Get your API key from: https://makersuite.google.com/app/apikey
    private val apiKey = "AIzaSyAG9VdNAUhmY3b-qmrCQ-hCfXcoXjsHrtE" // Your actual API key
    
    suspend fun initialize() {
        Log.d(TAG, "Initializing Gemini AI processor...")
        
        try {
            generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey,
                systemInstruction = content {
                    text(TORI_SYSTEM_PROMPT)
                }
            )
            
            isInitialized = true
            Log.d(TAG, "Gemini AI processor initialized successfully with API key")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemini AI", e)
            isInitialized = true // Allow fallback mode
        }
    }
    
    suspend fun processCommand(
        userInput: String,
        context: ConversationContext
    ): GeminiResponse = withContext(Dispatchers.IO) {
        
        if (!isInitialized) {
            throw IllegalStateException("Gemini processor not initialized")
        }
        
        try {
            Log.d(TAG, "Processing command with Gemini: $userInput")
            
            // Build context-aware prompt
            val prompt = buildPrompt(userInput, context)
            
            // Generate response using Gemini
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: "I'm sorry, I couldn't process that request."
            
            Log.d(TAG, "Gemini response received: $responseText")
            
            // Parse response for any special actions
            val parsedResponse = parseResponse(responseText, userInput)
            
            return@withContext GeminiResponse(
                message = parsedResponse.message,
                intent = parsedResponse.intent,
                data = parsedResponse.data
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing command with Gemini", e)
            
            // Use fallback response
            return@withContext getFallbackResponse(userInput)
        }
    }
    
    private fun getFallbackResponse(userInput: String): GeminiResponse {
        val input = userInput.lowercase()
        
        val (message, intent) = when {
            input.contains("tired") || input.contains("sleepy") -> {
                "I understand you're feeling tired, Manish. Let me help you find a rest area nearby." to Intent.WELLNESS_TIRED
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
            input.contains("weather") -> {
                "Let me check the weather for you." to Intent.WEATHER
            }
            input.contains("traffic") -> {
                "I'll check the traffic conditions ahead." to Intent.TRAFFIC
            }
            input.contains("emergency") || input.contains("help") -> {
                "I'm here to help. Do you need emergency assistance?" to Intent.EMERGENCY
            }
            input.contains("where am i") || input.contains("location") -> {
                "Let me get your current location for you." to Intent.LOCATION
            }
            else -> {
                "I heard you say '$userInput'. How can I help you with that?" to Intent.GENERAL
            }
        }
        
        val data = when (intent) {
            Intent.WELLNESS_TIRED -> mapOf("wellnessType" to "tired", "needsRestAreaSearch" to true)
            Intent.WELLNESS_HUNGRY -> mapOf("wellnessType" to "hungry", "needsFoodSearch" to true)
            Intent.NAVIGATION, Intent.SEARCH -> mapOf("query" to userInput, "needsLocationSearch" to true)
            Intent.EMERGENCY -> mapOf("emergencyType" to "general", "needsSOSActivation" to true)
            else -> emptyMap()
        }
        
        return GeminiResponse(message = message, intent = intent, data = data)
    }
    
    private fun buildPrompt(userInput: String, context: ConversationContext): String {
        val contextHistory = context.recentInteractions.takeLast(3)
            .joinToString("\n") { "User: ${it.userInput}\nTori: ${it.assistantResponse}" }
        
        return """
            Previous conversation:
            $contextHistory
            
            Current user input: $userInput
            
            Respond as Tori, keeping the conversation natural and helpful.
        """.trimIndent()
    }
    
    private fun parseResponse(responseText: String, userInput: String): ParsedResponse {
        // Analyze the user input and response to determine intent
        val intent = determineIntent(userInput, responseText)
        val data = extractData(userInput, responseText, intent)
        
        return ParsedResponse(
            message = responseText,
            intent = intent,
            data = data
        )
    }
    
    private fun determineIntent(userInput: String, responseText: String): Intent {
        val input = userInput.lowercase()
        
        return when {
            input.contains("tired") || input.contains("sleepy") || input.contains("fatigue") -> Intent.WELLNESS_TIRED
            input.contains("hungry") || input.contains("food") || input.contains("eat") -> Intent.WELLNESS_HUNGRY
            input.contains("navigate") || input.contains("directions") || input.contains("take me") -> Intent.NAVIGATION
            input.contains("find") || input.contains("search") || input.contains("nearby") -> Intent.SEARCH
            input.contains("weather") -> Intent.WEATHER
            input.contains("traffic") -> Intent.TRAFFIC
            input.contains("emergency") || input.contains("help") || input.contains("sos") -> Intent.EMERGENCY
            input.contains("where am i") || input.contains("location") -> Intent.LOCATION
            else -> Intent.GENERAL
        }
    }
    
    private fun extractData(userInput: String, responseText: String, intent: Intent): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        
        when (intent) {
            Intent.NAVIGATION, Intent.SEARCH -> {
                // Extract location/place information
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
            else -> {
                // General conversation
            }
        }
        
        return data
    }
    
    companion object {
        private const val TAG = "GeminiProcessor"
        
        private const val TORI_SYSTEM_PROMPT = """
            You are Tori, an intelligent voice assistant integrated into a driving safety app called TOR-I. 
            You are similar to JARVIS from Iron Man - helpful, intelligent, and slightly witty.
            
            Your primary role is to assist drivers with:
            - Navigation and directions
            - Finding nearby places (gas stations, restaurants, rest areas)
            - Wellness checks (when drivers are tired, hungry, or sleepy)
            - Weather and traffic information
            - Emergency assistance
            - General conversation to keep drivers alert and engaged
            
            Key personality traits:
            - Friendly and conversational
            - Safety-focused (always prioritize driver safety)
            - Slightly witty but professional
            - Proactive in suggesting breaks when needed
            - Use the driver's name (Manish) when appropriate
            
            Response guidelines:
            - Keep responses concise but helpful
            - Always acknowledge the user's request
            - For safety-related requests (tired, sleepy), be proactive about suggesting rest
            - For navigation requests, confirm the destination
            - Use natural, conversational language
            - Don't be overly formal
            
            Example responses:
            - "No worries, Manish. Let me find the nearest rest area for you."
            - "Got it. Searching for gas stations nearby."
            - "I can help with that. What's your destination?"
            - "Sounds like you need a break. Let me find some good rest stops ahead."
        """
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
    NAVIGATION,
    SEARCH,
    WELLNESS_TIRED,
    WELLNESS_HUNGRY,
    WEATHER,
    TRAFFIC,
    EMERGENCY,
    LOCATION,
    GENERAL,
    ERROR
}