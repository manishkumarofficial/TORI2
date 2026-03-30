package com.tori.safety.voice

import android.content.Context
import android.util.Log
import com.tori.safety.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini AI processor — Direct REST API via OkHttp.
 *
 * CRITICAL FIX: Do NOT verify/test during init. The httpClient must stay alive
 * so real user requests actually reach the API. Previous bug was setting
 * httpClient=null during init because the test call failed.
 */
class GeminiProcessor(private val context: Context) {

    private lateinit var httpClient: OkHttpClient
    private var isInitialized = false
    private var workingModel: String? = null // Tracks which model works

    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    // Models to try in order
    private val MODEL_PRIMARY = "gemini-2.0-flash"
    private val MODEL_FALLBACK = "gemini-1.5-flash"

    suspend fun initialize() {
        Log.d(TAG, "Initializing Gemini AI processor (OkHttp REST API)...")
        Log.d(TAG, "API key length: ${apiKey.length}, prefix: ${apiKey.take(8)}...")

        if (apiKey.isEmpty() || apiKey == "YOUR_API_KEY_HERE") {
            Log.e(TAG, "API key missing — fallback mode only")
            isInitialized = true
            return
        }

        // Create HTTP client with generous timeouts for long responses
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)  // Long timeout for story-length responses
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        isInitialized = true
        Log.d(TAG, "Gemini processor ready (will use live API calls)")

        // NOTE: We intentionally do NOT verify the API key here.
        // Previous bug: verification failed → httpClient set to null → all calls used fallback.
        // Now: httpClient is always alive, actual user requests will succeed or fail with real errors.
    }

    /**
     * Makes a direct HTTP call to the Gemini REST API.
     * Tries primary model first, falls back to secondary if needed.
     */
    private fun callGeminiAPI(prompt: String, systemPrompt: String): String {
        // Try the working model first (if we know which one works)
        val modelsToTry = if (workingModel != null) {
            listOf(workingModel!!)
        } else {
            listOf(MODEL_PRIMARY, MODEL_FALLBACK)
        }

        var lastError: Exception? = null

        for (model in modelsToTry) {
            try {
                Log.d(TAG, "Calling Gemini API — model: $model")
                val result = makeAPICall(prompt, systemPrompt, model)
                workingModel = model // Remember which model works
                return result
            } catch (e: Exception) {
                Log.w(TAG, "Model $model failed: ${e.javaClass.simpleName}: ${e.message}")
                lastError = e
            }
        }

        throw lastError ?: RuntimeException("All models failed")
    }

    private fun makeAPICall(prompt: String, systemPrompt: String, model: String): String {
        val requestJson = JSONObject().apply {
            // Content
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            // System instruction
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemPrompt)
                    })
                })
            })
            // Generation config — generous for creative responses
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 1024)  // Enough for stories
                put("temperature", 0.8)       // Creative but not wild
                put("topK", 40)
                put("topP", 0.95)
            })
        }

        val url = "$BASE_URL/$model:generateContent?key=$apiKey"
        Log.d(TAG, "POST $url")

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e(TAG, "HTTP ${response.code}: ${responseBody.take(500)}")
            throw RuntimeException("HTTP ${response.code}: ${responseBody.take(200)}")
        }

        // Parse response
        val jsonResponse = JSONObject(responseBody)

        // Check for API-level errors
        val error = jsonResponse.optJSONObject("error")
        if (error != null) {
            val errorMsg = error.optString("message", "Unknown API error")
            Log.e(TAG, "API error: $errorMsg")
            throw RuntimeException("API error: $errorMsg")
        }

        val candidates = jsonResponse.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            Log.w(TAG, "No candidates: ${responseBody.take(300)}")
            throw RuntimeException("No candidates in response")
        }

        val content = candidates.getJSONObject(0).optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = parts?.getJSONObject(0)?.optString("text", "") ?: ""

        if (text.isBlank()) {
            throw RuntimeException("Empty text in response")
        }

        return text.trim()
    }

    suspend fun processCommand(
        userInput: String,
        context: ConversationContext
    ): GeminiResponse = withContext(Dispatchers.IO) {

        if (!isInitialized) {
            throw IllegalStateException("Gemini processor not initialized")
        }

        if (!::httpClient.isInitialized) {
            Log.w(TAG, "HTTP client not created (no API key), using fallback")
            return@withContext getFallbackResponse(userInput)
        }

        try {
            Log.d(TAG, "Processing: '$userInput'")

            val prompt = buildPrompt(userInput, context)
            val responseText = callGeminiAPI(prompt, TORI_SYSTEM_PROMPT)

            // Clean markdown for TTS
            val cleanText = responseText
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("\\*(.+?)\\*"), "$1")
                .replace(Regex("#+ "), "")
                .replace(Regex("```[\\s\\S]*?```"), "")  // Remove code blocks
                .replace("\n\n", ". ")
                .replace("\n", " ")
                .trim()

            Log.d(TAG, "✓ Gemini response (${cleanText.length} chars): '${cleanText.take(100)}...'")

            val intent = determineIntent(userInput)
            val data = extractData(userInput, intent)

            return@withContext GeminiResponse(
                message = cleanText,
                intent = intent,
                data = data
            )

        } catch (e: Exception) {
            Log.e(TAG, "⚠ Gemini API failed: ${e.javaClass.simpleName}: ${e.message}")
            // Log the FULL error so the user can see what's actually going wrong
            Log.e(TAG, "Full error details:", e)
            return@withContext getFallbackResponse(userInput)
        }
    }

    private fun buildPrompt(userInput: String, context: ConversationContext): String {
        val contextHistory = context.recentInteractions.takeLast(3)
            .filter { it.assistantResponse.isNotBlank() }
            .joinToString("\n") { "User: ${it.userInput}\nTori: ${it.assistantResponse}" }

        return if (contextHistory.isBlank()) {
            userInput
        } else {
            "$contextHistory\n\nUser: $userInput"
        }
    }

    private fun getFallbackResponse(userInput: String): GeminiResponse {
        Log.w(TAG, "Using LOCAL fallback for: '$userInput'")
        val input = userInput.lowercase()

        val (message, intent) = when {
            input.contains("tired") || input.contains("sleepy") ->
                "I understand you're feeling tired. Let me help you find a rest area nearby." to Intent.WELLNESS_TIRED
            input.contains("hungry") || input.contains("food") ->
                "Got it! I'll help you find some food options nearby." to Intent.WELLNESS_HUNGRY
            input.contains("navigate") || input.contains("directions") || input.contains("take me") ->
                "Sure! I can help you navigate. Where would you like to go?" to Intent.NAVIGATION
            input.contains("weather") ->
                "Let me check the weather for you." to Intent.WEATHER
            input.contains("traffic") ->
                "I'll check the traffic conditions ahead." to Intent.TRAFFIC
            input.contains("emergency") || input.contains("help me") ->
                "I'm here to help. Do you need emergency assistance?" to Intent.EMERGENCY
            input.contains("name") && (input.contains("your") || input.contains("you")) ->
                "I'm Tori, your AI driving companion!" to Intent.GENERAL
            else ->
                "Sorry, I couldn't reach my AI engine. Please check your internet connection and try again." to Intent.GENERAL
        }

        return GeminiResponse(message = message, intent = intent, data = extractData(userInput, intent))
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
                data["query"] = userInput; data["needsLocationSearch"] = true
            }
            Intent.WELLNESS_TIRED -> {
                data["wellnessType"] = "tired"; data["needsRestAreaSearch"] = true
            }
            Intent.WELLNESS_HUNGRY -> {
                data["wellnessType"] = "hungry"; data["needsFoodSearch"] = true
            }
            Intent.EMERGENCY -> {
                data["emergencyType"] = "general"; data["needsSOSActivation"] = true
            }
            else -> {}
        }
        return data
    }

    companion object {
        private const val TAG = "GeminiProcessor"

        private const val TORI_SYSTEM_PROMPT = """You are Tori, an AI voice assistant inside a driving safety app called TOR-I.
You are like JARVIS from Iron Man — helpful, knowledgeable, and witty.

CRITICAL RULES:
1. ALWAYS answer the user's question directly and completely.
2. If they ask for a story, tell a short engaging story (3-5 sentences).
3. If they ask your name, say you're Tori.
4. Keep responses concise (2-4 sentences) since they will be spoken aloud.
5. Be conversational and friendly, not robotic.
6. NEVER echo back what the user said without answering.
7. NEVER say you can't connect or have trouble — just answer the question.
8. For driving requests, be proactive about helping.
9. Use the driver's name (Manish) occasionally.

You help with: navigation, nearby places, wellness, weather, traffic, emergencies, stories, jokes, and general knowledge."""
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