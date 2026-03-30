package com.tori.safety.voice

import com.google.genai.Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper for standalone Gemini calls (used by VoiceManager/VoiceAssistantFragment).
 * Uses the new com.google.genai SDK.
 */
class GeminiHelper(private val apiKey: String) {

    private val client: Client = Client.builder()
        .apiKey(apiKey)
        .build()
    
    private val modelName = "gemini-2.0-flash"

    // Simple chat history
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

            val prompt = buildPrompt()
            try {
                val response = client.models.generateContent(modelName, prompt, null)
                val responseText = response.text() ?: ""
                
                chatHistory.add("Tori: $responseText")
                parseResponse(responseText)
            } catch (e: Exception) {
                if (chatHistory.last().startsWith("User:")) chatHistory.removeLast()
                GeminiResponse(IntentType.UNKNOWN, "Sorry, I'm having trouble connecting right now.", null)
            }
        }
    }

    private fun buildPrompt(): String {
        return chatHistory.joinToString("\n") + "\n\nAnalyze the last user command and respond in this format:\nINTENT: [NAVIGATE | SEARCH | WEATHER | TRAFFIC | CHAT | UNKNOWN]\nDATA: [Extraction or NULL]\nREPLY: [Your conversational response]"
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
        
        if (intent == IntentType.UNKNOWN && !text.contains("INTENT:")) {
            intent = IntentType.CHAT
            reply = text
        }

        return GeminiResponse(intent, reply, data)
    }

    suspend fun generateProactivePrompt(): String {
        return withContext(Dispatchers.IO) {
            val prompt = """
                You are Tori, a driving companion. The driver has been quiet for a while.
                Generate a short, friendly question or comment to keep them alert.
                Return JUST the text of the message.
            """.trimIndent()
            
            try {
                val response = client.models.generateContent(modelName, prompt, null)
                response.text() ?: "Hey, just checking in. You good?"
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
