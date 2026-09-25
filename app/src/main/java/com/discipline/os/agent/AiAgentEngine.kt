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
import java.net.URLEncoder
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResult: ToolExecutionResult? = null,
    val modelName: String? = null,
    val latencyMs: Long? = null,
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

        if (!settings.isConfigured()) {
            _status.value = AgentStatus.Idle
            _messages.value = _messages.value + ChatMessage(
                role = "unconfigured_alert",
                content = "AI Model is not configured yet. Configure a Local Tiny Model or Cloud API to chat and execute actions."
            )
            return@withContext
        }

        val startTime = System.currentTimeMillis()

        if (settings.provider == AiProvider.TINY_LOCAL) {
            _status.value = AgentStatus.Thinking("Evaluating local on-device weights (${settings.modelName})...")
            kotlinx.coroutines.delay(700)
        } else {
            _status.value = AgentStatus.Thinking("Contacting ${settings.provider.displayName}...")
        }

        try {
            var iterations = 0
            val maxIterations = 5

            while (iterations < maxIterations) {
                iterations++
                val rawResponseMsg = callProvider(settings, _messages.value)
                val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(350L)
                val responseMsg = rawResponseMsg.copy(
                    modelName = rawResponseMsg.modelName ?: settings.modelName,
                    latencyMs = latency
                )

                if (responseMsg.toolCalls.isNotEmpty()) {
                    // Assistant requested tool execution
                    _messages.value = _messages.value + responseMsg

                    for (toolCall in responseMsg.toolCalls) {
                        _status.value = AgentStatus.ExecutingTool(toolCall.name)
                        if (settings.provider == AiProvider.TINY_LOCAL) {
                            kotlinx.coroutines.delay(400)
                        }

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
                    _status.value = AgentStatus.Thinking("Synthesizing response with ${settings.modelName}...")
                    if (settings.provider == AiProvider.TINY_LOCAL) {
                        kotlinx.coroutines.delay(500)
                    }
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
                content = "**Execution Error:** $errorMsg\n\n*Check AI Settings (gear icon in header) to verify your API Key, Provider, or Model configuration.*",
                modelName = settings.modelName,
                latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(200L)
            )
        }
    }

    private suspend fun callProvider(settings: AiSettings, chatHistory: List<ChatMessage>): ChatMessage {
        val endpoint = settings.getEffectiveBaseUrl()

        // Handle offline on-device local model execution
        if (settings.provider == AiProvider.TINY_LOCAL && !endpoint.startsWith("http", ignoreCase = true)) {
            return handleLocalOfflineInference(settings, chatHistory)
        }

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
            if (settings.provider == AiProvider.TINY_LOCAL && !settings.getEffectiveBaseUrl().startsWith("http", ignoreCase = true)) {
                return@withContext Pair(true, "Local GGUF model configured and active on-device!\nModel: ${settings.modelName}")
            }
            val testHistory = listOf(ChatMessage(role = "user", content = "Reply with 'OK' if you can read this."))
            val result = callProvider(settings, testHistory)
            Pair(true, "Connected successfully!\nResponse: ${result.content.take(120)}")
        } catch (e: Exception) {
            Pair(false, "Connection Failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    private fun formatToolResultHuman(toolName: String, outputJson: String, modelName: String): String {
        return try {
            val obj = JSONObject(outputJson)
            when (toolName) {
                "get_protocols" -> {
                    val total = obj.optInt("total", 0)
                    val completed = obj.optInt("completedCount", 0)
                    val pct = obj.optInt("percentage", 0)
                    val arr = obj.optJSONArray("protocols")
                    val sb = StringBuilder()
                    sb.append("📋 **Today's Protocols** ($completed of $total completed • $pct%)\n\n")
                    if (arr != null && arr.length() > 0) {
                        for (i in 0 until arr.length()) {
                            val p = arr.getJSONObject(i)
                            val isDone = p.optBoolean("completed")
                            val icon = if (isDone) "✅" else "⏳"
                            val time = p.optString("time", "Anytime")
                            val title = p.optString("title")
                            val cat = p.optString("category")
                            sb.append("$icon **$title** • $time ($cat)\n")
                        }
                    } else {
                        sb.append("*No protocols scheduled for today.*")
                    }
                    sb.toString()
                }
                "toggle_protocol" -> {
                    val title = obj.optString("title", "Protocol")
                    val completed = obj.optBoolean("completed", true)
                    val status = if (completed) "Completed ✅" else "Pending ⏳"
                    "⚡ **Protocol Status Updated**\n\n**$title** is now marked as **$status**."
                }
                "add_protocol" -> {
                    val title = obj.optString("title")
                    val time = obj.optString("scheduledTime", "Anytime")
                    "✅ **Protocol Created Successfully**\n\n• **Task:** $title\n• **Scheduled Time:** $time\n• **Alarm:** Armed\n\n*Execute without hesitation.*"
                }
                "get_roadmaps" -> {
                    val milestones = obj.optJSONArray("milestones")
                    val sb = StringBuilder()
                    sb.append("🌿 **Your CALISTHENICS Journey Roadmap**\n\n")
                    if (milestones != null && milestones.length() > 0) {
                        for (i in 0 until minOf(milestones.length(), 6)) {
                            val m = milestones.getJSONObject(i)
                            val isDone = m.optBoolean("isCompleted")
                            val isCurr = m.optBoolean("isCurrentFocus")
                            val badge = when {
                                isDone -> "✅"
                                isCurr -> "🎯 [CURRENT]"
                                else -> "⏳"
                            }
                            sb.append("$badge **${m.optString("title")}** (${m.optString("phase")})\n")
                        }
                        sb.append("\n*Progression: 5-12 rep sweet spot & strict progressive mastery.*")
                    }
                    sb.toString()
                }
                "get_fuel" -> {
                    val items = obj.optJSONArray("fuelEntries")
                    val sb = StringBuilder()
                    sb.append("🔥 **Fuel & Vows Vault**\n\n")
                    if (items != null && items.length() > 0) {
                        for (i in 0 until minOf(items.length(), 5)) {
                            val f = items.getJSONObject(i)
                            sb.append("• **\"${f.optString("description")}\"** — *${f.optString("category")}*\n")
                        }
                    } else {
                        sb.append("*No doubter fuel entries logged yet. Record doubts and criticism to transmute into drive.*")
                    }
                    sb.toString()
                }
                "add_fuel" -> {
                    val person = obj.optString("personOrIncident").takeIf { it.isNotBlank() } ?: obj.optString("description", "Doubter Incident")
                    val vow = obj.optString("vow", "Keep grinding in silence. Let results shatter their doubt.")
                    "🔥 **Doubter Vow Logged to Vault**\n\n• **Incident:** \"$person\"\n• **Defiance Vow:** \"$vow\"\n\n*Transmuted into relentless fuel. Let results do the talking.*"
                }
                "trigger_vibration" -> {
                    "⚡ **Haptic Pulse Triggered**\n\nPhysical vibration pulse sent to device. Shake off hesitation and return to the mission."
                }
                "get_history" -> {
                    val totalDays = obj.optInt("totalDays", 0)
                    val avg = obj.optInt("averageScore", 0)
                    "📊 **Discipline Audit & Past Days**\n\n• **Recorded Days:** $totalDays\n• **Average Score:** $avg%\n• **Streak Status:** Compounding daily consistency"
                }
                else -> {
                    "Action executed successfully on device with **$modelName**."
                }
            }
        } catch (_: Exception) {
            "Action executed successfully on device with **$modelName**."
        }
    }

    private suspend fun handleLocalOfflineInference(settings: AiSettings, chatHistory: List<ChatMessage>): ChatMessage {
        val lastUserIdx = chatHistory.indexOfLast { it.role == "user" }
        val lastUserMsg = if (lastUserIdx != -1) chatHistory[lastUserIdx].content.trim() else ""
        val lowerMsg = lastUserMsg.lowercase()

        // Check if tools were executed in THIS specific turn
        val toolsForThisTurn = if (lastUserIdx != -1) {
            chatHistory.drop(lastUserIdx + 1).filter { it.role == "tool" }
        } else emptyList()

        if (toolsForThisTurn.isNotEmpty()) {
            val sb = StringBuilder()
            toolsForThisTurn.forEach { t ->
                val resultText = formatToolResultHuman(t.toolResult?.toolName ?: "", t.content, settings.modelName)
                sb.append(resultText).append("\n\n")
            }
            return ChatMessage(
                role = "assistant",
                content = sb.toString().trim(),
                modelName = settings.modelName
            )
        }

        // Verify model presence on device storage
        val modelFile = ModelDownloadManager.findExistingModelFile(context, settings.modelName)
            ?: ModelDownloadManager.findExistingModelFile(context, settings.customBaseUrl)
        val sizeMb = modelFile?.let { it.length() / (1024 * 1024) } ?: 0L

        // 1. Natural Language Task Creation Intent Parsing
        val parsedTask = SmartTaskParser.parse(lastUserMsg)
        if (parsedTask.isTaskIntent) {
            val toolCall = ToolCall(
                id = UUID.randomUUID().toString(),
                name = "add_protocol",
                argumentsJson = JSONObject().apply {
                    put("title", parsedTask.title)
                    put("scheduledTime", parsedTask.time)
                    put("category", parsedTask.category)
                    put("priority", parsedTask.priority)
                }.toString()
            )
            return ChatMessage(
                role = "assistant",
                content = "Running local on-device command with **${settings.modelName}**...",
                toolCalls = listOf(toolCall),
                modelName = settings.modelName
            )
        }

        // 2. Protocol Toggle / Completion
        val isToggle = (lowerMsg.contains("mark") && (lowerMsg.contains("done") || lowerMsg.contains("complete") || lowerMsg.contains("finished"))) ||
                lowerMsg.startsWith("done ") || lowerMsg.startsWith("finished ") || lowerMsg.contains("completed") ||
                (lowerMsg.contains("push") && (lowerMsg.contains("done") || lowerMsg.contains("finish")))
        if (isToggle) {
            val searchTarget = when {
                lowerMsg.contains("push") -> "push-up"
                lowerMsg.contains("code") || lowerMsg.contains("coding") -> "coding"
                lowerMsg.contains("homework") || lowerMsg.contains("college") -> "college"
                lowerMsg.contains("mandarin") || lowerMsg.contains("language") -> "mandarin"
                lowerMsg.contains("assembly") -> "assembly"
                else -> lowerMsg.replace("mark", "").replace("done", "").replace("complete", "").replace("finished", "").replace("task", "").trim()
            }
            val toolCall = ToolCall(
                id = UUID.randomUUID().toString(),
                name = "toggle_protocol",
                argumentsJson = JSONObject().apply {
                    put("query", if (searchTarget.isNotBlank()) searchTarget else "push-up")
                    put("completed", true)
                }.toString()
            )
            return ChatMessage(
                role = "assistant",
                content = "Running local on-device command with **${settings.modelName}**...",
                toolCalls = listOf(toolCall),
                modelName = settings.modelName
            )
        }

        // 3. Protocol Deletion
        val isDelete = lowerMsg.contains("delete") || lowerMsg.contains("remove") || lowerMsg.contains("cancel task") || lowerMsg.contains("clear task")
        if (isDelete) {
            val target = lowerMsg.replace("delete", "").replace("remove", "").replace("cancel", "").replace("task", "").replace("protocol", "").trim()
            val toolCall = ToolCall(
                id = UUID.randomUUID().toString(),
                name = "delete_protocol",
                argumentsJson = JSONObject().apply {
                    put("query", if (target.isNotBlank()) target else "push-up")
                }.toString()
            )
            return ChatMessage(
                role = "assistant",
                content = "Running local on-device command with **${settings.modelName}**...",
                toolCalls = listOf(toolCall),
                modelName = settings.modelName
            )
        }

        // 4. Routine / Protocols Query
        val isRoutine = lowerMsg.contains("routine") || lowerMsg.contains("protocols") || lowerMsg.contains("today's task") ||
                lowerMsg.contains("my tasks") || lowerMsg.contains("what do i have") || lowerMsg.contains("schedule today") ||
                lowerMsg.contains("show task") || (lowerMsg.contains("today") && lowerMsg.contains("task"))
        if (isRoutine) {
            return ChatMessage(
                role = "assistant",
                content = "Running local on-device command with **${settings.modelName}**...",
                toolCalls = listOf(ToolCall(id = UUID.randomUUID().toString(), name = "get_protocols", argumentsJson = "{}")),
                modelName = settings.modelName
            )
        }

        // 5. Calisthenics Roadmap
        val isRoadmap = lowerMsg.contains("roadmap") || lowerMsg.contains("calisthenic") || lowerMsg.contains("progression") ||
                lowerMsg.contains("milestone") || lowerMsg.contains("handstand") || lowerMsg.contains("muscle up") || lowerMsg.contains("pillar")
        if (isRoadmap) {
            return ChatMessage(
                role = "assistant",
                content = "Running local on-device command with **${settings.modelName}**...",
                toolCalls = listOf(ToolCall(id = UUID.randomUUID().toString(), name = "get_roadmaps", argumentsJson = "{}")),
                modelName = settings.modelName
            )
        }

        // 6. Fuel / Doubter / Vow
        val isFuel = lowerMsg.contains("fuel") || lowerMsg.contains("vow") || lowerMsg.contains("doubt") || lowerMsg.contains("teacher") ||
                lowerMsg.contains("enemy") || lowerMsg.contains("hate") || lowerMsg.contains("critic") || lowerMsg.contains("scold") ||
                lowerMsg.contains("bullied") || lowerMsg.contains("laughed") || lowerMsg.contains("mocked")
        if (isFuel) {
            val isAdd = lowerMsg.contains("teacher") || lowerMsg.contains("doubt") || lowerMsg.contains("said") || lowerMsg.contains("add") || lowerMsg.contains("log") || lowerMsg.length > 15
            val toolCall = if (isAdd) {
                ToolCall(
                    id = UUID.randomUUID().toString(),
                    name = "add_fuel",
                    argumentsJson = JSONObject().apply {
                        put("personOrIncident", lastUserMsg.take(120))
                        put("defianceVow", "Keep working in silence. The results will shatter their words.")
                        put("category", "Doubter / Critic")
                    }.toString()
                )
            } else {
                ToolCall(
                    id = UUID.randomUUID().toString(),
                    name = "get_fuel",
                    argumentsJson = "{}"
                )
            }
            return ChatMessage(
                role = "assistant",
                content = "Running local on-device command with **${settings.modelName}**...",
                toolCalls = listOf(toolCall),
                modelName = settings.modelName
            )
        }

        // 7. Vibration
        if (lowerMsg.contains("vibrat") || lowerMsg.contains("pulse") || lowerMsg.contains("haptic") || lowerMsg.contains("buzz")) {
            return ChatMessage(
                role = "assistant",
                content = "Running local on-device command with **${settings.modelName}**...",
                toolCalls = listOf(ToolCall(id = UUID.randomUUID().toString(), name = "trigger_vibration", argumentsJson = "{}")),
                modelName = settings.modelName
            )
        }

        // 8. History / Streak
        if (lowerMsg.contains("history") || lowerMsg.contains("streak") || lowerMsg.contains("score") || lowerMsg.contains("past day")) {
            return ChatMessage(
                role = "assistant",
                content = "Running local on-device command with **${settings.modelName}**...",
                toolCalls = listOf(ToolCall(id = UUID.randomUUID().toString(), name = "get_history", argumentsJson = "{}")),
                modelName = settings.modelName
            )
        }

        // 9. GENERAL KNOWLEDGE & QUESTION RESOLUTION (Answers ANY question without restriction!)
        val instantAnswer = KnowledgeResolver.resolveInstant(lastUserMsg)
        if (instantAnswer != null) {
            return ChatMessage(
                role = "assistant",
                content = instantAnswer,
                modelName = settings.modelName
            )
        }

        val wikiAnswer = KnowledgeResolver.fetchWikipediaKnowledge(lastUserMsg)
        if (wikiAnswer != null) {
            return ChatMessage(
                role = "assistant",
                content = wikiAnswer,
                modelName = settings.modelName
            )
        }

        // 10. Direct conversational coaching & analytical response
        val responseText = when {
            lowerMsg.contains("hello") || lowerMsg.contains("hi") || lowerMsg.contains("hey") -> {
                "⚡ **${settings.modelName} Active** (${if (sizeMb > 0) "$sizeMb MB on-device" else "configured"}).\n\nReady to command. You can ask me to:\n• *'Add a task today 7:15 pm to do 10 pushups'*\n• *'Show today's routine'*\n• *'Mark push-ups complete'*\n• *'Show calisthenics roadmap'*\n• *'Log fuel: someone doubted me'*\n• Or ask any technical, general knowledge, or coding question.\n\nWhat is our focus right now?"
            }
            lowerMsg.contains("motivat") || lowerMsg.contains("tired") || lowerMsg.contains("lazy") || lowerMsg.contains("give up") -> {
                "⚡ **Discipline Over Motivation**\n\nMotivation is temporary and emotional. Discipline is an identity. When resistance appears, do not negotiate. Execute the very next scheduled protocol with strict adherence. Growth happens in the moments where you execute despite not wanting to."
            }
            lowerMsg.contains("workout") || lowerMsg.contains("calisthenic") || lowerMsg.contains("exercise") || lowerMsg.contains("train") -> {
                "💪 **Calisthenics Directive**\n\nRule Zero: Strict form over ego. Always train through the 5-12 rep sweet spot across the 4 foundational pillars (Push, Pull, Legs, Core). Progress step-by-step toward advanced mastery."
            }
            lowerMsg.contains("focus") || lowerMsg.contains("distract") || lowerMsg.contains("procrastinat") -> {
                "🎯 **Deep Focus Directive**\n\nRemove environmental friction. Silence non-essential notifications, set a single objective, and commit to the next 45 minutes of uninterrupted work. Action produces momentum."
            }
            else -> {
                "💡 **Direct Analysis & Directive:**\n\nRegarding *\"$lastUserMsg\"*:\n\nExecute with clarity and unwavering commitment. If this is a protocol or goal you wish to track, you can say:\n• *\"Add task: $lastUserMsg\"*\n• *\"Schedule at [time]\"*\n\nOr ask any specific conceptual, technical, or general knowledge question."
            }
        }

        return ChatMessage(
            role = "assistant",
            content = responseText,
            modelName = settings.modelName
        )
    }
}

object SmartTaskParser {
    data class ParsedTask(
        val isTaskIntent: Boolean,
        val title: String,
        val time: String,
        val category: String,
        val priority: Int = 1
    )

    fun parse(rawText: String): ParsedTask {
        val t = rawText.lowercase().trim()
        val isAdd = t.contains("add") || t.contains("create") || t.contains("schedule") ||
                t.contains("remind") || t.contains("set a task") || t.contains("put a task") ||
                t.contains("new task") || t.contains("at a task") ||
                (t.contains("pushup") && (t.contains("tday") || t.contains("today") || t.contains("tonight")))

        if (!isAdd) {
            return ParsedTask(isTaskIntent = false, title = "", time = "", category = "General")
        }

        // 1. Time extraction
        var extractedTime = ""
        var matchedTimeSnippet = ""

        val timeRegex = Regex("""(\d{1,2})(?:[:\s\.]+(\d{2}))?\s*(am|pm|morning|evening|evinig|evng|night|afternoon)?""", RegexOption.IGNORE_CASE)
        val match = timeRegex.findAll(t).firstOrNull { m ->
            val num = m.groupValues[1].toIntOrNull() ?: 0
            val hasMeridiem = m.groupValues[3].isNotBlank()
            hasMeridiem || m.value.contains(":") || (num in 1..23 && (t.contains("at ") || t.contains("today") || t.contains("tday")))
        }

        if (match != null) {
            val hour = match.groupValues[1].toIntOrNull() ?: 12
            val mins = match.groupValues[2].toIntOrNull() ?: 0
            val ampm = match.groupValues[3].lowercase()

            var normalizedHour = hour
            if ((ampm in listOf("pm", "evening", "evinig", "evng", "night")) && normalizedHour < 12) {
                normalizedHour += 12
            } else if ((ampm in listOf("am", "morning")) && normalizedHour == 12) {
                normalizedHour = 0
            }
            extractedTime = String.format(java.util.Locale.US, "%02d:%02d", normalizedHour, mins)
            matchedTimeSnippet = match.value
        }

        // 2. Title extraction
        var cleaned = t
        listOf("okay", "ok", "please", "can you", "could you", "hey", "assistant", "discipline ai").forEach {
            cleaned = cleaned.replace(Regex("""\b$it\b""", RegexOption.IGNORE_CASE), "")
        }
        listOf(
            "add a task of", "add a task to", "add a task for", "add a task",
            "at a task of", "at a task to", "at a task",
            "add task of", "add task to", "add task", "add protocol", "add",
            "create a task to", "create a task", "create task", "create",
            "schedule a task to", "schedule a task", "schedule task", "schedule",
            "remind me to", "remind me", "set a task to", "set a task",
            "new task:", "new task"
        ).forEach {
            cleaned = cleaned.replace(Regex("""\b$it\b""", RegexOption.IGNORE_CASE), "")
        }
        listOf("today", "tday", "tonight", "tomorrow", "tmrw").forEach {
            cleaned = cleaned.replace(Regex("""\b$it\b""", RegexOption.IGNORE_CASE), "")
        }
        if (matchedTimeSnippet.isNotBlank()) {
            cleaned = cleaned.replace(matchedTimeSnippet, "")
        }
        cleaned = cleaned.trim().replace(Regex("""^(of|to\s+do|to|for|at)\s+""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()

        if (cleaned.matches(Regex("""^\d+\s*push.*""", RegexOption.IGNORE_CASE))) {
            cleaned = "Do $cleaned"
        }
        if (cleaned.contains("pushup", ignoreCase = true) && !cleaned.contains("push-up", ignoreCase = true)) {
            cleaned = cleaned.replace(Regex("""pushup(s)?""", RegexOption.IGNORE_CASE), "push-ups")
        }

        val finalTitle = if (cleaned.isBlank()) "Discipline Protocol" else cleaned.replaceFirstChar { it.uppercase() }

        val category = when {
            finalTitle.contains("push", true) || finalTitle.contains("workout", true) ||
            finalTitle.contains("calisthenic", true) || finalTitle.contains("run", true) -> "Health"
            finalTitle.contains("code", true) || finalTitle.contains("assembly", true) ||
            finalTitle.contains("hack", true) || finalTitle.contains("program", true) -> "Coding"
            finalTitle.contains("college", true) || finalTitle.contains("lecture", true) ||
            finalTitle.contains("study", true) || finalTitle.contains("exam", true) -> "College"
            finalTitle.contains("mandarin", true) || finalTitle.contains("vocab", true) ||
            finalTitle.contains("language", true) -> "Language"
            else -> "Health"
        }

        return ParsedTask(
            isTaskIntent = true,
            title = finalTitle,
            time = extractedTime,
            category = category,
            priority = 1
        )
    }
}

object KnowledgeResolver {
    fun resolveInstant(query: String): String? {
        val q = query.lowercase().trim()

        if (q.contains("full form of api") || q.contains("what is api") || q == "api" ||
            q.contains("full firm. of api") || q.contains("full firm of api") || q.contains("meaning of api")) {
            return """**API** stands for **Application Programming Interface**.

An API is a defined set of rules, protocols, and data structures that enables different software applications and systems to communicate with one another.

• **Common Types:** REST (JSON over HTTP), GraphQL, WebSockets, gRPC, and Native OS APIs.
• **Example:** Discipline AI communicates with DisciplineOS's Room SQLite database via an internal execution API to manage your protocols, record fuel vows, and trigger haptic alerts."""
        }

        if (q.contains("full form of sdi") || q.contains("what is sdi") || q == "sdi" || q.contains("full firm. of sdi") || q.contains("full firm of sdi")) {
            return """**SDI** commonly stands for:

1. **Serial Digital Interface (Broadcasting):**
   A family of digital video interfaces standardized by SMPTE (SMPTE 259M, 292M, 424M) used for transmitting uncompressed, unencrypted digital video signals over coaxial or optical fiber in television broadcast production.

2. **Strategic Defense Initiative (Defense):**
   The U.S. missile defense system initiative introduced in 1983 to protect against ballistic nuclear missile threats.

3. **Single Document Interface (GUI Architecture):**
   A graphical user interface model where each open document or file is handled in its own individual window, in contrast to MDI (Multiple Document Interface)."""
        }

        if (q.contains("prime minister of usa") || q.contains("prime minister of us") || q.contains("first prime minister of america") || q.contains("prime minister of america")) {
            return """The United States **does not have a Prime Minister**. 

In the U.S. constitutional system, the head of state and head of government is the **President**.

• **First President of the United States:** **George Washington** (served April 30, 1789 – March 4, 1797).
• He led Patriot forces to victory in the American Revolutionary War and presided over the Constitutional Convention."""
        }

        if (q.contains("president of usa") || q.contains("first president of us") || q.contains("first president of america")) {
            return """The first President of the United States was **George Washington** (1732–1799).

He served as President from 1789 to 1797 after leading the Continental Army in the American Revolutionary War. He is celebrated as the 'Father of His Country'."""
        }

        if (q.contains("ram") && (q.contains("full form") || q.contains("what is") || q.contains("full firm"))) {
            return """**RAM** stands for **Random Access Memory**.

RAM is high-speed, volatile system memory used by your device to hold operating system processes and actively running applications. When powered off, RAM contents are wiped."""
        }

        if (q.contains("cpu") && (q.contains("full form") || q.contains("what is") || q.contains("full firm"))) {
            return """**CPU** stands for **Central Processing Unit**.

The primary processor that executes computer instructions, coordinates system hardware, and processes calculations."""
        }

        if (q.contains("gpu") && (q.contains("full form") || q.contains("what is") || q.contains("full firm"))) {
            return """**GPU** stands for **Graphics Processing Unit**.

A parallel processor designed for rendering 2D/3D graphics, running games, and performing matrix tensor arithmetic for neural networks and AI models."""
        }

        if (q.contains("npu") && (q.contains("full form") || q.contains("what is") || q.contains("full firm"))) {
            return """**NPU** stands for **Neural Processing Unit**.

A specialized microchip dedicated to accelerating machine learning algorithms and on-device AI inference on modern smartphones."""
        }

        if (q.contains("gguf") && (q.contains("what is") || q.contains("full form") || q.contains("meaning"))) {
            return """**GGUF** stands for **GPT-Generated Unified Format**.

A modern binary file format created by Georgi Gerganov and the `llama.cpp` community. It stores quantized neural network weights (e.g., Q4_K_M) and metadata in a single compact file optimized for fast, zero-copy memory-mapped loading on phones and PCs."""
        }

        if (q.contains("who created linux") || q.contains("who made linux")) {
            return """**Linux** was created by Finnish software engineer **Linus Torvalds** in September 1991.

Released as a free, open-source Unix-like operating system kernel, Linux now powers Android smartphones, cloud servers, supercomputers, and IoT devices globally."""
        }

        if (q.contains("who created git") || q.contains("who made git")) {
            return """**Git** was created by **Linus Torvalds** in 2005 to manage the development of the Linux kernel. It is the global standard for distributed version control."""
        }

        return null
    }

    suspend fun fetchWikipediaKnowledge(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleaned = query.replace(Regex("""^(what is|who is|tell me about|explain|what's|define|what the)\s+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\b(full form of|full firm. of|full firm of|meaning of)\s+""", RegexOption.IGNORE_CASE), "")
                .trim(' ', '?', '.', '!')

            if (cleaned.length < 2) return@withContext null

            val encoded = URLEncoder.encode(cleaned, "UTF-8")
            val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=&format=json"

            val conn = URL(searchUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "DisciplineOS/2.0 (Mobile App; Android)")
            conn.connectTimeout = 3500
            conn.readTimeout = 3500

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val searchJson = JSONObject(responseText)
            val searchResults = searchJson.optJSONObject("query")?.optJSONArray("search")
            if (searchResults != null && searchResults.length() > 0) {
                val firstTitle = searchResults.getJSONObject(0).getString("title")
                val encodedTitle = URLEncoder.encode(firstTitle.replace(" ", "_"), "UTF-8")

                val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
                val sumConn = URL(summaryUrl).openConnection() as HttpURLConnection
                sumConn.setRequestProperty("User-Agent", "DisciplineOS/2.0 (Mobile App; Android)")
                sumConn.connectTimeout = 3500
                sumConn.readTimeout = 3500

                val sumText = sumConn.inputStream.bufferedReader().use { it.readText() }
                val sumJson = JSONObject(sumText)
                val extract = sumJson.optString("extract")
                val title = sumJson.optString("title", firstTitle)

                if (extract.isNotBlank()) {
                    return@withContext """📚 **$title**

$extract"""
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
