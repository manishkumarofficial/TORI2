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
    private val chatHistory = mutableListOf<String>()

    init {
        // Seed history
        chatHistory.add("System: You are Tori, a helpful and friendly driving assistant. Your persona is like Iron Man's JARVIS or F.R.I.D.A.Y. Keep responses concise and natural.")
    }

    suspend fun processCommand(userText: String): GeminiResponse {
        return withContext(Dispatchers.IO) {
            chatHistory.add("User: $userText")
            
            // Limit history to last 10 turns
            if (chatHistory.size > 20) {
                 chatHistory.removeAt(1) // Keep system prompt at 0
            }

            val prompt = buildPrompt()
            try {
                val response = generativeModel.generateContent(prompt)
                val responseText = response.text ?: ""
                
                chatHistory.add("Tori: $responseText")
                
                // Parse the response to extract intent and reply
                // We expect Gemini to return a specific format, e.g., JSON or a structured string
                parseResponse(responseText)
            } catch (e: Exception) {
                // Remove prompt if failed
                if (chatHistory.last().startsWith("User:")) chatHistory.removeLast()
                GeminiResponse(IntentType.UNKNOWN, "Sorry, I'm having trouble connecting to my brain right now.", null)
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
        
        // Fallback if parsing fails but we have text (likely just a chat response)
        if (intent == IntentType.UNKNOWN && !text.contains("INTENT:")) {
             intent = IntentType.CHAT
             reply = text
        }

        return GeminiResponse(intent, reply, data)
    }

    // Proactive Companion Mode
    suspend fun generateProactivePrompt(): String {
        return withContext(Dispatchers.IO) {
            val prompt = """
                You are Tori, a driving companion. The driver has been quiet for a while.
                Generate a short, friendly, and engaging question or comment to keep them awake and attentive.
                Examples: "It's quiet. How are you feeling?", "Do you want to play a trivia game to stay alert?", "Look at the road ahead, traffic seems clear."
                Return JUST the text of the message.
            """.trimIndent()
            
            try {
                val response = generativeModel.generateContent(prompt)
                response.text ?: "Hey, just checking in. You good?"
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
