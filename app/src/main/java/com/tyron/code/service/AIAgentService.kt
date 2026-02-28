package com.tyron.code.service

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.squareup.moshi.Moshi
import com.squareup.okhttp3.OkHttpClient
import com.squareup.okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * AI Agent Service for generating code using OpenRouter or Gemini API
 */
class AIAgentService(private val context: Context) {
    
    companion object {
        private const val PREF_NAME = "ai_agent_prefs"
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_API_ENDPOINT = "ai_api_endpoint"
        private const val KEY_MODEL_NAME = "ai_model_name"
        private const val KEY_PROVIDER = "ai_provider"  // "openrouter" or "gemini"
    }
    
    private val encryptedSharedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    private val moshi = Moshi.Builder().build()
    
    private fun createHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    
    fun saveAPIConfig(provider: String, apiKey: String, endpoint: String, modelName: String) {
        encryptedSharedPrefs.edit().apply {
            putString(KEY_PROVIDER, provider)
            putString(KEY_API_KEY, apiKey)
            putString(KEY_API_ENDPOINT, endpoint)
            putString(KEY_MODEL_NAME, modelName)
            apply()
        }
    }
    
    fun getAPIConfig(): AIConfig {
        return AIConfig(
            provider = encryptedSharedPrefs.getString(KEY_PROVIDER, "openrouter") ?: "openrouter",
            apiKey = encryptedSharedPrefs.getString(KEY_API_KEY, "") ?: "",
            endpoint = encryptedSharedPrefs.getString(KEY_API_ENDPOINT, "") ?: "",
            modelName = encryptedSharedPrefs.getString(KEY_MODEL_NAME, "") ?: ""
        )
    }
    
    suspend fun generateCode(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val config = getAPIConfig()
            
            if (config.apiKey.isEmpty() || config.endpoint.isEmpty() || config.modelName.isEmpty()) {
                return@withContext Result.failure(
                    Exception("AI API not configured. Please configure in Settings first.")
                )
            }
            
            val retrofit = Retrofit.Builder()
                .baseUrl(config.endpoint)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(createHttpClient())
                .build()
            
            val apiService = retrofit.create(AIApi::class.java)
            
            val request = AIRequest(
                model = config.modelName,
                messages = listOf(
                    AIMessage(
                        role = "system",
                        content = "You are an expert Android developer and code generator. Generate clean, well-structured, and production-ready code."
                    ),
                    AIMessage(
                        role = "user",
                        content = prompt
                    )
                )
            )
            
            val authHeader = when {
                config.provider.equals("openrouter", ignoreCase = true) -> 
                    "Bearer ${config.apiKey}"
                else -> 
                    "Bearer ${config.apiKey}"
            }
            
            val response = apiService.generateCode(authHeader, "application/json", request)
            
            if (response.error != null) {
                return@withContext Result.failure(
                    Exception("API Error: ${response.error.message}")
                )
            }
            
            val generatedCode = response.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(
                    Exception("No response from AI API")
                )
            
            Result.success(generatedCode)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    data class AIConfig(
        val provider: String,
        val apiKey: String,
        val endpoint: String,
        val modelName: String
    )
}
