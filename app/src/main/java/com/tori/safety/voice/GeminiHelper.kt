package com.tori.safety.voice

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiHelper(private val apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    // Maintain a simple chat history
    private val chatHistory = mutableListOf<Pair<String, String>>()

    suspend fun processCommand(userText: String): GeminiResponse {
        return withContext(Dispatchers.IO) {
            val prompt = buildPrompt(userText)
            try {
                val response = generativeModel.generateContent(prompt)
                val responseText = response.text ?: ""
                
                // Parse the response to extract intent and reply
                // We expect Gemini to return a specific format, e.g., JSON or a structured string
                parseResponse(responseText)
            } catch (e: Exception) {
                GeminiResponse(IntentType.UNKNOWN, "Sorry, I'm having trouble connecting to my brain right now.", null)
            }
        }
    }

    private fun buildPrompt(userText: String): String {
        // Construct a prompt that instructs Gemini to act as Tori
        // and return a structured response.
        return """
            You are Tori, a helpful and friendly driving assistant.
            Your persona is like Iron Man's JARVIS or F.R.I.D.A.Y.
            Keep responses concise (suitable for driving) but friendly.
            
            Analyze the following user command and respond in this format:
            INTENT: [NAVIGATE | SEARCH | WEATHER | TRAFFIC | CHAT | UNKNOWN]
            DATA: [Extraction of location, query, etc. or NULL]
            REPLY: [Your conversational response to the user]
            
            User Command: "$userText"
        """.trimIndent()
    }

    private fun parseResponse(text: String): GeminiResponse {
        var intent = IntentType.UNKNOWN
        var data: String? = null
        var reply = "I'm not sure how to help with that."

        val lines = text.lines()
        for (line in lines) {
            when {
                line.startsWith("INTENT:") -> {
                    val intentStr = line.removePrefix("INTENT:").trim()
                    intent = try {
                        IntentType.valueOf(intentStr)
                    } catch (e: IllegalArgumentException) {
                        IntentType.UNKNOWN
                    }
                }
                line.startsWith("DATA:") -> {
                    val d = line.removePrefix("DATA:").trim()
                    if (d != "NULL") data = d
                }
                line.startsWith("REPLY:") -> {
                    reply = line.removePrefix("REPLY:").trim()
                }
            }
        }
        
        // Fallback if parsing fails but we have text (likely just a chat response)
        if (intent == IntentType.UNKNOWN && !text.contains("INTENT:")) {
             intent = IntentType.CHAT
             reply = text
        }

        return GeminiResponse(intent, reply, data)
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
