package com.tyron.code.service

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit API interface for AI service providers
 */
interface AIApi {
    
    @POST("[provider_url]")
    suspend fun generateCode(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: AIRequest
    ): AIResponse
}
