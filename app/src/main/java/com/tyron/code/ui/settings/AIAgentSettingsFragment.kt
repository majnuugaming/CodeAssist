package com.tyron.code.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.tyron.code.R
import com.tyron.code.service.AIAgentService

class AIAgentSettingsFragment : PreferenceFragmentCompat() {
    
    private lateinit var aiService: AIAgentService
    private var providerPreference: ListPreference? = null
    private var apiKeyPreference: EditTextPreference? = null
    private var endpointPreference: EditTextPreference? = null
    private var modelNamePreference: EditTextPreference? = null
    private var saveButtonPreference: Preference? = null
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_ai_agent, rootKey)
        aiService = AIAgentService(requireContext())
        setupPreferences()
    }
    
    private fun setupPreferences() {
        providerPreference = findPreference("ai_provider")
        apiKeyPreference = findPreference("ai_api_key")
        endpointPreference = findPreference("ai_api_endpoint")
        modelNamePreference = findPreference("ai_model_name")
        saveButtonPreference = findPreference("ai_save_config")
        
        // Set up provider preference
        providerPreference?.setOnPreferenceChangeListener { _, newValue ->
            val provider = newValue.toString()
            updateEndpointHint(provider)
            true
        }
        
        // Set up save button
        saveButtonPreference?.setOnPreferenceClickListener {
            saveAIConfig()
            true
        }
        
        // Load existing config
        loadAIConfig()
    }
    
    private fun updateEndpointHint(provider: String) {
        when (provider) {
            "openrouter" -> {
                endpointPreference?.summary = "https://openrouter.ai/api/v1/chat/completions"
            }
            "gemini" -> {
                endpointPreference?.summary = "https://generativelanguage.googleapis.com/v1"
            }
            else -> {
                endpointPreference?.summary = "Enter your API endpoint"
            }
        }
    }
    
    private fun loadAIConfig() {
        val config = aiService.getAPIConfig()
        providerPreference?.value = config.provider
        apiKeyPreference?.text = config.apiKey
        endpointPreference?.text = config.endpoint
        modelNamePreference?.text = config.modelName
        updateEndpointHint(config.provider)
    }
    
    private fun saveAIConfig() {
        val provider = providerPreference?.value ?: "openrouter"
        val apiKey = apiKeyPreference?.text ?: ""
        val endpoint = endpointPreference?.text ?: ""
        val modelName = modelNamePreference?.text ?: ""
        
        if (apiKey.isEmpty() || endpoint.isEmpty() || modelName.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Pehle sab fields bharo",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        aiService.saveAPIConfig(provider, apiKey, endpoint, modelName)
        Toast.makeText(
            requireContext(),
            "AI Agent configuration saved",
            Toast.LENGTH_SHORT
        ).show()
    }
}
