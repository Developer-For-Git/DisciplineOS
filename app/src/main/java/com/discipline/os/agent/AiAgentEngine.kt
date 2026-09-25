package com.discipline.os.agent

import android.content.Context
import com.discipline.os.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResult: ToolExecutionResult? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

sealed class AgentStatus {
    object Idle : AgentStatus()
    data class Thinking(val step: String = "Thinking...") : AgentStatus()
    data class ExecutingTool(val toolName: String) : AgentStatus()
    data class Error(val message: String) : AgentStatus()
}

class AiAgentEngine(
    private val context: Context,
    private val db: AppDatabase
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _status = MutableStateFlow<AgentStatus>(AgentStatus.Idle)
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    init {
        initGreeting()
    }

    fun initGreeting() {
        if (_messages.value.isEmpty()) {
            _messages.value = listOf(
                ChatMessage(
                    role = "assistant",
                    content = "**Discipline AI Initialized.**\n\nDirect application integration active. Ready to manage routine protocols, update schedules, toggle completion, record fuel vows, and review historical scores.\n\nSelect a prompt below or type an instruction."
                )
            )
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        _status.value = AgentStatus.Idle
        initGreeting()
    }

    fun resetStatus() {
        _status.value = AgentStatus.Idle
    }

    suspend fun sendMessage(userText: String) = withContext(Dispatchers.IO) {
        if (userText.isBlank()) return@withContext

        val settings = AiSettings.load(context)
        val userMsg = ChatMessage(role = "user", content = userText.trim())
        _messages.value = _messages.value + userMsg

        _status.value = AgentStatus.Thinking("Contacting ${settings.provider.displayName}...")

        try {
            var iterations = 0
            val maxIterations = 5

            while (iterations < maxIterations) {
                iterations++
                val responseMsg = callProvider(settings, _messages.value)

                if (responseMsg.toolCalls.isNotEmpty()) {
                    // Assistant requested tool execution
                    _messages.value = _messages.value + responseMsg

                    for (toolCall in responseMsg.toolCalls) {
                        _status.value = AgentStatus.ExecutingTool(toolCall.name)

                        val argsObj = try {
                            JSONObject(toolCall.argumentsJson)
                        } catch (_: Exception) {
                            JSONObject()
                        }

                        val result = AgentTools.executeTool(
                            context = context,
                            db = db,
                            toolName = toolCall.name,
                            args = argsObj
                        )

                        val toolMsg = ChatMessage(
                            id = toolCall.id,
                            role = "tool",
                            content = result.outputJson,
                            toolResult = result
                        )
                        _messages.value = _messages.value + toolMsg
                    }

                    // Loop again to allow the LLM to process tool results and respond
                    _status.value = AgentStatus.Thinking("Synthesizing actions...")
                } else {
                    // Regular text response
                    _messages.value = _messages.value + responseMsg
                    _status.value = AgentStatus.Idle
                    return@withContext
                }
            }

            _status.value = AgentStatus.Idle
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Unknown error occurred"
            _status.value = AgentStatus.Error(errorMsg)
            _messages.value = _messages.value + ChatMessage(
                role = "assistant",
                content = "**Execution Error:** $errorMsg\n\n*Check AI Settings (gear icon in header) to verify your API Key, Provider, or Model configuration.*"
            )
        }
    }

    private fun callProvider(settings: AiSettings, chatHistory: List<ChatMessage>): ChatMessage {
        val endpoint = settings.getEffectiveBaseUrl()
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection

        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.doOutput = true

        // Configure Authentication Headers
        when (settings.provider) {
            AiProvider.ANTHROPIC -> {
                conn.setRequestProperty("x-api-key", settings.apiKey.trim())
                conn.setRequestProperty("anthropic-version", "2023-06-01")
            }
            AiProvider.OPENROUTER -> {
                if (settings.apiKey.isNotBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer ${settings.apiKey.trim()}")
                }
                conn.setRequestProperty("HTTP-Referer", "https://discipline.os")
                conn.setRequestProperty("X-Title", "DisciplineOS AI")
            }
            else -> {
                if (settings.apiKey.isNotBlank()) {
                    conn.setRequestProperty(
                        settings.provider.defaultHeaderAuthKey,
                        "${settings.provider.authPrefix}${settings.apiKey.trim()}"
                    )
                }
            }
        }

        // Build Payload
        val payload = JSONObject()
        if (settings.provider == AiProvider.ANTHROPIC) {
            buildAnthropicPayload(payload, settings, chatHistory)
        } else {
            buildOpenAiPayload(payload, settings, chatHistory)
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errBody = try {
                BufferedReader(InputStreamReader(conn.errorStream, "UTF-8")).use { it.readText() }
            } catch (_: Exception) {
                conn.responseMessage
            }
            val parsedError = try {
                val errObj = JSONObject(errBody)
                if (errObj.has("error")) {
                    val errField = errObj.get("error")
                    if (errField is JSONObject) {
                        errField.optString("message", errBody)
                    } else {
                        errField.toString()
                    }
                } else {
                    errBody
                }
            } catch (_: Exception) {
                errBody
            }
            throw IllegalStateException("API HTTP $responseCode: $parsedError")
        }

        val respBody = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
        val jsonResp = JSONObject(respBody)

        return if (settings.provider == AiProvider.ANTHROPIC) {
            parseAnthropicResponse(jsonResp)
        } else {
            parseOpenAiResponse(jsonResp)
        }
    }

    private fun buildOpenAiPayload(payload: JSONObject, settings: AiSettings, chatHistory: List<ChatMessage>) {
        payload.put("model", settings.modelName)
        payload.put("temperature", settings.temperature.toDouble())

        val msgsArray = JSONArray()

        // 1. System Prompt
        msgsArray.put(JSONObject().apply {
            put("role", "system")
            put("content", settings.systemPrompt)
        })

        // 2. Chat history
        for (m in chatHistory) {
            when (m.role) {
                "user" -> {
                    msgsArray.put(JSONObject().apply {
                        put("role", "user")
                        put("content", m.content)
                    })
                }
                "assistant" -> {
                    val aObj = JSONObject().apply {
                        put("role", "assistant")
                        put("content", m.content.ifBlank { " " })
                    }
                    if (m.toolCalls.isNotEmpty()) {
                        val tcArr = JSONArray()
                        for (tc in m.toolCalls) {
                            tcArr.put(JSONObject().apply {
                                put("id", tc.id)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", tc.name)
                                    put("arguments", tc.argumentsJson)
                                })
                            })
                        }
                        aObj.put("tool_calls", tcArr)
                    }
                    msgsArray.put(aObj)
                }
                "tool" -> {
                    msgsArray.put(JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", m.id)
                        put("content", m.content)
                    })
                }
            }
        }

        payload.put("messages", msgsArray)

        // Attach Tool Calling Definitions
        payload.put("tools", AgentTools.toolsJsonArray)
        payload.put("tool_choice", "auto")
    }

    private fun parseOpenAiResponse(json: JSONObject): ChatMessage {
        val choices = json.optJSONArray("choices")
            ?: throw IllegalStateException("Invalid response format: 'choices' missing")
        if (choices.length() == 0) {
            throw IllegalStateException("Model returned empty choices")
        }

        val choice = choices.getJSONObject(0)
        val msgObj = choice.optJSONObject("message")
            ?: throw IllegalStateException("Invalid choice: 'message' missing")

        val rawContent = msgObj.optString("content", "").trim()
        val toolCalls = mutableListOf<ToolCall>()

        // 1. Parse standard OpenAI tool_calls
        if (msgObj.has("tool_calls")) {
            val tcArr = msgObj.getJSONArray("tool_calls")
            for (i in 0 until tcArr.length()) {
                val tc = tcArr.getJSONObject(i)
                val id = tc.optString("id", UUID.randomUUID().toString())
                val fn = tc.optJSONObject("function") ?: JSONObject()
                val name = fn.optString("name", "")
                val args = fn.optString("arguments", "{}")
                if (name.isNotBlank()) {
                    toolCalls.add(ToolCall(id = id, name = name, argumentsJson = args))
                }
            }
        }

        // 2. Fallback parser for Tiny Models / Ollama models that output tool calls in plain text
        if (toolCalls.isEmpty() && rawContent.isNotBlank()) {
            // Pattern A: Action: tool_name({"arg": "val"})
            val actionRegex = Regex("""Action:\s*([a-zA-Z0-9_]+)\s*\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
            val match = actionRegex.find(rawContent)
            if (match != null) {
                val name = match.groupValues[1]
                val args = match.groupValues[2].trim().ifBlank { "{}" }
                toolCalls.add(ToolCall(id = UUID.randomUUID().toString(), name = name, argumentsJson = args))
            } else {
                // Pattern B: ```json { "tool": "tool_name", "args": {...} } ```
                val jsonBlockRegex = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""")
                val blockMatch = jsonBlockRegex.find(rawContent)
                if (blockMatch != null) {
                    try {
                        val parsed = JSONObject(blockMatch.groupValues[1])
                        val toolName = parsed.optString("tool").ifBlank { parsed.optString("name") }
                        val toolArgs = if (parsed.has("arguments")) parsed.get("arguments").toString()
                                       else if (parsed.has("args")) parsed.get("args").toString()
                                       else parsed.toString()
                        if (toolName.isNotBlank()) {
                            toolCalls.add(ToolCall(id = UUID.randomUUID().toString(), name = toolName, argumentsJson = toolArgs))
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        return ChatMessage(
            role = "assistant",
            content = rawContent,
            toolCalls = toolCalls
        )
    }

    private fun buildAnthropicPayload(payload: JSONObject, settings: AiSettings, chatHistory: List<ChatMessage>) {
        payload.put("model", settings.modelName)
        payload.put("max_tokens", 2048)
        payload.put("system", settings.systemPrompt)

        val msgsArray = JSONArray()
        for (m in chatHistory) {
            when (m.role) {
                "user" -> {
                    msgsArray.put(JSONObject().apply {
                        put("role", "user")
                        put("content", m.content)
                    })
                }
                "assistant" -> {
                    msgsArray.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", m.content)
                    })
                }
            }
        }
        payload.put("messages", msgsArray)
    }

    private fun parseAnthropicResponse(json: JSONObject): ChatMessage {
        val contentArr = json.optJSONArray("content")
        val sb = StringBuilder()
        if (contentArr != null) {
            for (i in 0 until contentArr.length()) {
                val item = contentArr.getJSONObject(i)
                if (item.optString("type") == "text") {
                    sb.append(item.optString("text"))
                }
            }
        }
        return ChatMessage(role = "assistant", content = sb.toString())
    }

    suspend fun testConnection(settings: AiSettings): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val testHistory = listOf(ChatMessage(role = "user", content = "Reply with 'OK' if you can read this."))
            val result = callProvider(settings, testHistory)
            Pair(true, "Connected successfully!\nResponse: ${result.content.take(120)}")
        } catch (e: Exception) {
            Pair(false, "Connection Failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }
}
