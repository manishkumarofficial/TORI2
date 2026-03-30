package com.tori.safety.voice

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.tori.safety.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini AI processor for Tori voice assistant
 *
 * FIX v2:
 * - Try multiple model names (gemini-2.0-flash, then gemini-1.5-flash as fallback)
 * - Log the FULL exception when API calls fail (was being swallowed silently)
 * - Improved fallback responses for general questions
 * - Better system prompt that explicitly tells Gemini to answer questions directly
 */
class GeminiProcessor(private val context: Context) {
    
    private var generativeModel: GenerativeModel? = null
    private var isInitialized = false
    
    private val apiKey = BuildConfig.GEMINI_API_KEY
    
    suspend fun initialize() {
        Log.d(TAG, "Initializing Gemini AI processor...")
        Log.d(TAG, "API key length: ${apiKey.length}, starts with: ${apiKey.take(10)}...")
        
        try {
            if (apiKey.isEmpty() || apiKey == "YOUR_API_KEY_HERE") {
                Log.e(TAG, "Gemini API key is missing or placeholder. Fallback mode enabled.")
                isInitialized = true
                return
            }
            
            // Try gemini-2.0-flash first, fall back to 1.5-flash
            generativeModel = tryCreateModel("gemini-2.0-flash")
                ?: tryCreateModel("gemini-1.5-flash")

            if (generativeModel != null) {
                Log.d(TAG, "Gemini AI processor initialized successfully")
            } else {
                Log.e(TAG, "Could not create any Gemini model — fallback mode enabled")
            }
            
            isInitialized = true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemini AI: ${e.javaClass.simpleName}: ${e.message}", e)
            isInitialized = true // Allow fallback mode
        }
    }

    private fun tryCreateModel(modelName: String): GenerativeModel? {
        return try {
            val model = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                systemInstruction = content {
                    text(TORI_SYSTEM_PROMPT)
                }
            )
            Log.d(TAG, "Created model: $modelName")
            model
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create model $modelName: ${e.message}")
            null
        }
    }
    
    suspend fun processCommand(
        userInput: String,
        context: ConversationContext
    ): GeminiResponse = withContext(Dispatchers.IO) {
        
        if (!isInitialized) {
            throw IllegalStateException("Gemini processor not initialized")
        }
        
        val model = generativeModel
        if (model == null) {
            Log.w(TAG, "Gemini model not available, using fallback for: $userInput")
            return@withContext getFallbackResponse(userInput)
        }
        
        try {
            Log.d(TAG, "Processing command with Gemini: '$userInput'")
            
            val prompt = buildPrompt(userInput, context)
            Log.d(TAG, "Sending prompt to Gemini API...")
            
            val response = model.generateContent(prompt)
            val responseText = response.text
            
            if (responseText.isNullOrBlank()) {
                Log.w(TAG, "Gemini returned empty response, using fallback")
                return@withContext getFallbackResponse(userInput)
            }
            
            Log.d(TAG, "Gemini API response (${responseText.length} chars): '$responseText'")
            
            val parsedResponse = parseResponse(responseText, userInput)
            
            return@withContext GeminiResponse(
                message = parsedResponse.message,
                intent = parsedResponse.intent,
                data = parsedResponse.data
            )
            
        } catch (e: Exception) {
            // LOG THE FULL ERROR — this was being silently swallowed before
            Log.e(TAG, "Gemini API call FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            Log.e(TAG, "Full stack trace:", e)
            
            val fallback = getFallbackResponse(userInput)
            return@withContext fallback
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
            input.contains("weather") -> {
                "Let me check the weather for you." to Intent.WEATHER
            }
            input.contains("traffic") -> {
                "I'll check the traffic conditions ahead." to Intent.TRAFFIC
            }
            input.contains("emergency") || input.contains("help me") -> {
                "I'm here to help. Do you need emergency assistance?" to Intent.EMERGENCY
            }
            input.contains("name") && (input.contains("your") || input.contains("you")) -> {
                "I'm Tori, your AI driving companion! I'm here to keep you safe and help you on the road. Think of me like your personal co-pilot." to Intent.GENERAL
            }
            input.contains("hello") || input.contains("hi ") || input.startsWith("hi") -> {
                "Hey there! I'm Tori, your driving assistant. How can I help you today?" to Intent.GENERAL
            }
            input.contains("how are you") -> {
                "I'm doing great, thanks for asking! All systems running smoothly. How about you?" to Intent.GENERAL
            }
            input.contains("thank") -> {
                "You're welcome! Always happy to help. Let me know if you need anything else." to Intent.GENERAL
            }
            else -> {
                "I'm sorry, I'm having trouble connecting to my AI brain right now. I can still help with navigation, finding places, and safety features. What do you need?" to Intent.GENERAL
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
        
        return if (contextHistory.isBlank()) {
            "User says: $userInput\n\nRespond directly and helpfully as Tori."
        } else {
            """
                Previous conversation:
                $contextHistory
                
                User says: $userInput
                
                Respond directly and helpfully as Tori. Answer the question if it's a question.
            """.trimIndent()
        }
    }
    
    private fun parseResponse(responseText: String, userInput: String): ParsedResponse {
        val intent = determineIntent(userInput, responseText)
        val data = extractData(userInput, responseText, intent)
        
        // Clean up the response — remove any markdown or formatting
        val cleanResponse = responseText
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")  // Remove bold markdown
            .replace(Regex("\\*(.+?)\\*"), "$1")          // Remove italic markdown
            .trim()
        
        return ParsedResponse(
            message = cleanResponse,
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
            input.contains("emergency") || input.contains("sos") -> Intent.EMERGENCY
            input.contains("where am i") || input.contains("location") -> Intent.LOCATION
            else -> Intent.GENERAL
        }
    }
    
    private fun extractData(userInput: String, responseText: String, intent: Intent): Map<String, Any> {
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
        
        private const val TORI_SYSTEM_PROMPT = """
            You are Tori, an intelligent voice assistant inside a driving safety app called TOR-I.
            You are like JARVIS from Iron Man — helpful, knowledgeable, and slightly witty.
            
            CRITICAL RULES:
            1. ALWAYS answer the user's question directly. If they ask "what is Python", explain Python.
            2. If they ask your name, say you're Tori.
            3. Keep responses concise (2-3 sentences max) since they will be spoken aloud via TTS.
            4. Be conversational and friendly, not robotic.
            5. Never echo back what the user said without answering.
            6. For driving-related requests (navigation, rest areas, food), be proactive about helping.
            7. Use the driver's name (Manish) occasionally.
            
            You can help with: navigation, finding nearby places, wellness advice (if driver is tired/hungry),
            weather, traffic info, emergency assistance, and general knowledge questions.
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