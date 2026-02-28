package com.tyron.code.ui.editor

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tyron.code.R
import com.tyron.code.service.AIAgentService
import kotlinx.coroutines.launch
import java.io.File

class AIAgentActivity : AppCompatActivity() {
    
    private lateinit var aiService: AIAgentService
    private lateinit var promptEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var resultRecyclerView: RecyclerView
    private lateinit var resultAdapter: AIResultAdapter
    private val results = mutableListOf<AIResult>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_agent)
        
        aiService = AIAgentService(this)
        
        promptEditText = findViewById(R.id.prompt_input)
        sendButton = findViewById(R.id.send_button)
        progressBar = findViewById(R.id.progress_bar)
        resultRecyclerView = findViewById(R.id.result_recycler_view)
        
        resultAdapter = AIResultAdapter(results)
        resultRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AIAgentActivity)
            adapter = resultAdapter
        }
        
        sendButton.setOnClickListener {
            val prompt = promptEditText.text.toString().trim()
            if (prompt.isEmpty()) {
                Toast.makeText(this, "Please enter a prompt", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            generateCode(prompt)
        }
    }
    
    private fun generateCode(prompt: String) {
        progressBar.visibility = android.view.View.VISIBLE
        sendButton.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val result = aiService.generateCode(prompt)
                result.onSuccess { code ->
                    val aiResult = AIResult(
                        prompt = prompt,
                        code = code,
                        timestamp = System.currentTimeMillis()
                    )
                    results.add(0, aiResult)
                    resultAdapter.notifyItemInserted(0)
                    resultRecyclerView.scrollToPosition(0)
                    promptEditText.text.clear()
                    Toast.makeText(this@AIAgentActivity, "Code generated successfully", Toast.LENGTH_SHORT).show()
                }
                result.onFailure { error ->
                    Toast.makeText(this@AIAgentActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                progressBar.visibility = android.view.View.GONE
                sendButton.isEnabled = true
            }
        }
    }
}

data class AIResult(
    val prompt: String,
    val code: String,
    val timestamp: Long = System.currentTimeMillis()
)

class AIResultAdapter(private val results: List<AIResult>) : 
    RecyclerView.Adapter<AIResultAdapter.AIResultViewHolder>() {
    
    inner class AIResultViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val promptTextView: TextView = itemView.findViewById(R.id.prompt_text)
        private val codeTextView: TextView = itemView.findViewById(R.id.code_text)
        private val saveButton: Button = itemView.findViewById(R.id.save_code_button)
        
        fun bind(result: AIResult) {
            promptTextView.text = "Q: ${result.prompt}"
            codeTextView.text = result.code
            
            saveButton.setOnClickListener {
                val fileName = "AIGenerated_${result.timestamp}.kt"
                val file = File(itemView.context.filesDir, "generated/$fileName")
                file.parentFile?.mkdirs()
                file.writeText(result.code)
                Toast.makeText(itemView.context, "Code saved to $fileName", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): AIResultViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_result, parent, false)
        return AIResultViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: AIResultViewHolder, position: Int) {
        holder.bind(results[position])
    }
    
    override fun getItemCount() = results.size
}
