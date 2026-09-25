package com.discipline.os.agent

import android.content.Context
import java.io.File

enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val defaultHeaderAuthKey: String = "Authorization",
    val authPrefix: String = "Bearer "
) {
    OPENROUTER(
        displayName = "OpenRouter (Recommended)",
        defaultBaseUrl = "https://openrouter.ai/api/v1/chat/completions",
        defaultModel = "google/gemini-2.0-flash-exp:free"
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini"
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1/messages",
        defaultModel = "claude-3-5-haiku-20241022",
        defaultHeaderAuthKey = "x-api-key",
        authPrefix = ""
    ),
    OLLAMA(
        displayName = "Ollama (Local / Wi-Fi)",
        defaultBaseUrl = "http://10.0.2.2:11434/v1/chat/completions",
        defaultModel = "gemma2:2b"
    ),
    NVIDIA_NIM(
        displayName = "NVIDIA NIM",
        defaultBaseUrl = "https://integrate.api.nvidia.com/v1/chat/completions",
        defaultModel = "meta/llama-3.3-70b-instruct"
    ),
    CUSTOM(
        displayName = "Custom Endpoint / Local Server",
        defaultBaseUrl = "http://10.0.2.2:8000/v1/chat/completions",
        defaultModel = "local-model"
    )
}

data class TinyModelInfo(
    val id: String,
    val name: String,
    val parameters: String,
    val downloadSize: String,
    val minRam: String,
    val description: String,
    val defaultUrl: String
)

object TinyModelCatalog {
    val models = listOf(
        TinyModelInfo(
            id = "gemma-2-2b",
            name = "Google Gemma 2 2B Instruct",
            parameters = "2.6 Billion",
            downloadSize = "1.6 GB (Q4_K_M)",
            minRam = "3 GB RAM",
            description = "Google's premier lightweight model. High-precision reasoning, structured output, and fast execution on mobile.",
            defaultUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"
        ),
        TinyModelInfo(
            id = "llama-3.2-1b",
            name = "Meta Llama 3.2 1B Instruct",
            parameters = "1.23 Billion",
            downloadSize = "800 MB (Q4_K_M)",
            minRam = "2 GB RAM",
            description = "Ultra-tiny on-device model from Meta. Near-instantaneous response times with minimal battery consumption.",
            defaultUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        ),
        TinyModelInfo(
            id = "llama-3.2-3b",
            name = "Meta Llama 3.2 3B Instruct",
            parameters = "3.21 Billion",
            downloadSize = "2.0 GB (Q4_K_M)",
            minRam = "4 GB RAM",
            description = "Gold-standard mobile agent. Excels at tool execution, schedule extraction, and disciplined coaching dialogue.",
            defaultUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf"
        ),
        TinyModelInfo(
            id = "qwen-2.5-3b",
            name = "Qwen 2.5 3B Instruct",
            parameters = "3.09 Billion",
            downloadSize = "1.9 GB (Q4_K_M)",
            minRam = "4 GB RAM",
            description = "Outstanding structured task planning and multilingual coding intelligence in a compact size.",
            defaultUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf"
        ),
        TinyModelInfo(
            id = "phi-3.5-mini",
            name = "Microsoft Phi-3.5 Mini",
            parameters = "3.82 Billion",
            downloadSize = "2.2 GB (Q4_K_M)",
            minRam = "4 GB RAM",
            description = "Microsoft's state-of-the-art small language model with deep multi-step reasoning.",
            defaultUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf"
        )
    )

    fun getLocalModelsDir(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isModelDownloaded(context: Context, modelId: String): Boolean {
        val dir = getLocalModelsDir(context)
        val files = dir.listFiles() ?: return false
        return files.any { it.name.startsWith(modelId, ignoreCase = true) && it.length() > 10_000_000L }
    }
}

data class AiSettings(
    val provider: AiProvider = AiProvider.OPENROUTER,
    val apiKey: String = "",
    val modelName: String = AiProvider.OPENROUTER.defaultModel,
    val customBaseUrl: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Float = 0.3f
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """You are Discipline Copilot, the autonomous in-app AI assistant of DisciplineOS.
You are sharp, focused, supportive, and dedicated to helping the user achieve unwavering discipline, conquer daily coding and study habits, and beat their yesterday's version of self.
You have direct access to execute tools inside the app on the user's behalf:
- Viewing today's protocols and completion status (get_protocols)
- Adding new habits/protocols with exact times, priorities, and sound (add_protocol)
- Toggling habits as completed or uncompleted (toggle_protocol)
- Rescheduling habit times (update_protocol_time)
- Deleting habits (delete_protocol)
- Recording doubter fuel vows in the Prove Them Wrong vault (add_fuel)
- Viewing fuel vows (get_fuel)
- Adding videos with study reminders (add_video)
- Inspecting past days' streaks and historical discipline scores (get_history)
- Triggering high-potential rapid vibration haptics (trigger_vibration)
- Resetting protocols for the day (reset_today)

Always be concise, disciplined, proactive, and confirm the specific actions you took."""

        private const val PREFS_NAME = "discipline_ai_prefs"
        private const val KEY_PROVIDER = "ai_provider"
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_BASE_URL = "ai_base_url"
        private const val KEY_TEMPERATURE = "ai_temperature"

        fun load(context: Context): AiSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val providerStr = prefs.getString(KEY_PROVIDER, AiProvider.OPENROUTER.name) ?: AiProvider.OPENROUTER.name
            val provider = try { AiProvider.valueOf(providerStr) } catch (_: Exception) { AiProvider.OPENROUTER }
            return AiSettings(
                provider = provider,
                apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
                modelName = prefs.getString(KEY_MODEL, provider.defaultModel) ?: provider.defaultModel,
                customBaseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
                temperature = prefs.getFloat(KEY_TEMPERATURE, 0.3f)
            )
        }

        fun save(context: Context, settings: AiSettings) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_PROVIDER, settings.provider.name)
                .putString(KEY_API_KEY, settings.apiKey)
                .putString(KEY_MODEL, settings.modelName)
                .putString(KEY_BASE_URL, settings.customBaseUrl)
                .putFloat(KEY_TEMPERATURE, settings.temperature)
                .apply()
        }
    }

    fun getEffectiveBaseUrl(): String {
        return if (customBaseUrl.isNotBlank()) customBaseUrl.trim() else provider.defaultBaseUrl
    }
}
