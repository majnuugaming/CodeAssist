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
        // existing implementation unchanged
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

    /**
     * Analyze a prompt and decide whether to execute a local file operation or delegate to remote
     * AI code generation. Recognizes simple natural-language commands driving file system changes
     * (create, delete, copy, move, edit) under the app's storage area. Returns a human-readable
     * result message that may also contain generated code when delegating.
     */
    suspend fun processPrompt(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        // simple parsing patterns, case insensitive but preserve original prompt when writing content
        val trimmed = prompt.trim()
        val lowered = trimmed.lowercase()

        fun successMessage(msg: String) = Result.success(msg)
        fun failureMessage(msg: String) = Result.failure(Exception(msg))

        try {
            when {
                lowered.startsWith("create file") -> {
                    // syntax: create file PATH [with CONTENT]
                    val regex = Regex("create file\\s+(.+?)(?:\\s+with\\s+([\"][\\s\\S]+[\"]|.+))?",
                        RegexOption.IGNORE_CASE)
                    val match = regex.find(trimmed)
                    if (match != null) {
                        val path = match.groupValues[1].trim().trim('"')
                        val content = match.groupValues.getOrNull(2)?.trim()?.trim('"') ?: ""
                        val file = File(context.filesDir, path)
                        file.parentFile?.mkdirs()
                        if (content.isNotEmpty()) {
                            file.writeText(content)
                        } else {
                            file.createNewFile()
                        }
                        return@withContext successMessage("Created file: ${file.absolutePath}")
                    }
                    return@withContext failureMessage("Could not parse create file command")
                }
                lowered.startsWith("delete file") -> {
                    // syntax: delete file PATH
                    val regex = Regex("delete file\\s+(.+)", RegexOption.IGNORE_CASE)
                    val match = regex.find(trimmed)
                    if (match != null) {
                        val path = match.groupValues[1].trim().trim('"')
                        val file = File(context.filesDir, path)
                        if (file.exists()) {
                            if (file.deleteRecursively()) {
                                return@withContext successMessage("Deleted file: ${file.absolutePath}")
                            } else {
                                return@withContext failureMessage("Failed to delete: ${file.absolutePath}")
                            }
                        } else {
                            return@withContext failureMessage("File does not exist: ${file.absolutePath}")
                        }
                    }
                    return@withContext failureMessage("Could not parse delete file command")
                }
                lowered.startsWith("copy file") || lowered.startsWith("paste file") -> {
                    // syntax: copy file SOURCE to DEST
                    val regex = Regex("(?:copy|paste) file\\s+(.+)\\s+to\\s+(.+)", RegexOption.IGNORE_CASE)
                    val match = regex.find(trimmed)
                    if (match != null) {
                        val srcPath = match.groupValues[1].trim().trim('"')
                        val destPath = match.groupValues[2].trim().trim('"')
                        val srcFile = File(context.filesDir, srcPath)
                        val destFile = File(context.filesDir, destPath)
                        if (!srcFile.exists()) {
                            return@withContext failureMessage("Source does not exist: ${srcFile.absolutePath}")
                        }
                        destFile.parentFile?.mkdirs()
                        srcFile.copyTo(destFile, overwrite = true)
                        return@withContext successMessage("Copied file to ${destFile.absolutePath}")
                    }
                    return@withContext failureMessage("Could not parse copy/paste command")
                }
                lowered.startsWith("move file") || lowered.startsWith("rename file") -> {
                    val regex = Regex("(?:move|rename) file\\s+(.+)\\s+to\\s+(.+)", RegexOption.IGNORE_CASE)
                    val match = regex.find(trimmed)
                    if (match != null) {
                        val srcPath = match.groupValues[1].trim().trim('"')
                        val destPath = match.groupValues[2].trim().trim('"')
                        val srcFile = File(context.filesDir, srcPath)
                        val destFile = File(context.filesDir, destPath)
                        if (!srcFile.exists()) {
                            return@withContext failureMessage("Source does not exist: ${srcFile.absolutePath}")
                        }
                        destFile.parentFile?.mkdirs()
                        if (srcFile.renameTo(destFile)) {
                            return@withContext successMessage("Moved file to ${destFile.absolutePath}")
                        } else {
                            return@withContext failureMessage("Failed to move file")
                        }
                    }
                    return@withContext failureMessage("Could not parse move command")
                }
                lowered.startsWith("edit file") -> {
                    // syntax: edit file PATH with CONTENT
                    val regex = Regex("edit file\\s+(.+?)\\s+with\\s+([\\s\\S]+)", RegexOption.IGNORE_CASE)
                    val match = regex.find(trimmed)
                    if (match != null) {
                        val path = match.groupValues[1].trim().trim('"')
                        val content = match.groupValues[2].trim().trim('"')
                        val file = File(context.filesDir, path)
                        if (!file.exists()) {
                            return@withContext failureMessage("File does not exist: ${file.absolutePath}")
                        }
                        file.writeText(content)
                        return@withContext successMessage("Edited file: ${file.absolutePath}")
                    }
                    return@withContext failureMessage("Could not parse edit file command")
                }
                else -> {
                    // default behaviour: forward to remote AI for code generation
                    return@withContext generateCode(prompt)
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
    
    data class AIConfig(
        val provider: String,
        val apiKey: String,
        val endpoint: String,
        val modelName: String
    )
}
