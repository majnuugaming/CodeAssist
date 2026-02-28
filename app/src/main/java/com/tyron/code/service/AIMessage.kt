package com.tyron.code.service

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data class for OpenRouter/Gemini API communication
 */
@JsonClass(generateAdapter = true)
data class AIMessage(
    @Json(name = "role")
    val role: String,
    @Json(name = "content")
    val content: String
)

@JsonClass(generateAdapter = true)
data class AIRequest(
    @Json(name = "model")
    val model: String,
    @Json(name = "messages")
    val messages: List<AIMessage>,
    @Json(name = "temperature")
    val temperature: Float = 0.7f,
    @Json(name = "max_tokens")
    val maxTokens: Int = 4096
)

@JsonClass(generateAdapter = true)
data class AIChoice(
    @Json(name = "message")
    val message: AIMessage
)

@JsonClass(generateAdapter = true)
data class AIResponse(
    @Json(name = "choices")
    val choices: List<AIChoice>,
    @Json(name = "error")
    val error: AIError? = null
)

@JsonClass(generateAdapter = true)
data class AIError(
    @Json(name = "message")
    val message: String,
    @Json(name = "type")
    val type: String? = null
)
