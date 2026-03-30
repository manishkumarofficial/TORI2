package com.tori.safety.voice

import android.util.Log
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
 * Helper for standalone Gemini calls (used by VoiceManager/VoiceAssistantFragment).
 * Uses direct REST API via OkHttp (no SDK dependencies).
 */
class GeminiHelper(private val apiKey: String) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val modelName = "gemini-2.0-flash"
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val chatHistory = mutableListOf<String>()

    init {
        chatHistory.add("System: You are Tori, a helpful and friendly driving assistant. Keep responses concise and natural.")
    }

    suspend fun processCommand(userText: String): GeminiResponse {
        return withContext(Dispatchers.IO) {
            chatHistory.add("User: $userText")

            if (chatHistory.size > 20) {
                chatHistory.removeAt(1)
            }

            try {
                val prompt = chatHistory.joinToString("\n") +
                    "\n\nRespond naturally as Tori. Answer the question directly."

                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 200)
                        put("temperature", 0.7)
                    })
                }

                val url = "$BASE_URL/$modelName:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody(JSON_TYPE))
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    throw RuntimeException("API error ${response.code}")
                }

                val text = JSONObject(body)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                chatHistory.add("Tori: $text")
                GeminiResponse(IntentType.CHAT, text, null)

            } catch (e: Exception) {
                Log.e("GeminiHelper", "API call failed: ${e.message}", e)
                if (chatHistory.last().startsWith("User:")) chatHistory.removeLast()
                GeminiResponse(IntentType.UNKNOWN, "Sorry, I'm having trouble connecting right now.", null)
            }
        }
    }

    suspend fun generateProactivePrompt(): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = "You are Tori, a driving companion. Generate a short, friendly question to keep the driver alert. Return JUST the text."
                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
                            })
                        })
                    })
                }

                val url = "$BASE_URL/$modelName:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody(JSON_TYPE))
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    JSONObject(body)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()
                } else {
                    "Hey, just checking in. You good?"
                }
            } catch (e: Exception) {
                "Stay alert, Manish."
            }
        }
    }

    enum class IntentType {
        NAVIGATE, SEARCH, WEATHER, TRAFFIC, CHAT, UNKNOWN
    }

    data class GeminiResponse(
        val intent: IntentType,
        val reply: String,
        val data: String?
    )
}
