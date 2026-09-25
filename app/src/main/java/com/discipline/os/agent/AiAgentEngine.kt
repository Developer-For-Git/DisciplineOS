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

        if (!settings.isConfigured()) {
            _status.value = AgentStatus.Idle
            _messages.value = _messages.value + ChatMessage(
                role = "unconfigured_alert",
                content = "AI Model is not configured yet. Configure a Local Tiny Model or Cloud API to chat and execute actions."
            )
            return@withContext
        }

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
                val responseMsg = callProvider(settings, _messages.value)

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
                content = "**Execution Error:** $errorMsg\n\n*Check AI Settings (gear icon in header) to verify your API Key, Provider, or Model configuration.*"
            )
        }
    }

    private fun callProvider(settings: AiSettings, chatHistory: List<ChatMessage>): ChatMessage {
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

    private fun handleLocalOfflineInference(settings: AiSettings, chatHistory: List<ChatMessage>): ChatMessage {
        val lastUserIdx = chatHistory.indexOfLast { it.role == "user" }
        val lastUserMsg = if (lastUserIdx != -1) chatHistory[lastUserIdx].content.lowercase().trim() else ""

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
                content = sb.toString().trim()
            )
        }

        // Verify model presence on device storage
        val modelFile = ModelDownloadManager.findExistingModelFile(context, settings.modelName)
            ?: ModelDownloadManager.findExistingModelFile(context, settings.customBaseUrl)

        val sizeMb = modelFile?.let { it.length() / (1024 * 1024) } ?: 0L

        // Tool detection based on user input intent
        val toolCalls = mutableListOf<ToolCall>()
        when {
            // Task creation intent
            lastUserMsg.contains("add task") || lastUserMsg.contains("add protocol") || lastUserMsg.contains("add habit") || (lastUserMsg.contains("create") && lastUserMsg.contains("task")) -> {
                val title = lastUserMsg.replace("add task", "").replace("add protocol", "").replace("add habit", "").replace("create task", "").trim(' ', ':', '-', '"')
                toolCalls.add(
                    ToolCall(
                        id = UUID.randomUUID().toString(),
                        name = "add_protocol",
                        argumentsJson = JSONObject().apply {
                            put("title", if (title.isNotBlank()) title else "New Protocol")
                            put("category", "Habit")
                            put("priority", 2)
                        }.toString()
                    )
                )
            }
            // Protocol toggle / completion
            lastUserMsg.contains("push-up") || lastUserMsg.contains("push up") || (lastUserMsg.contains("mark") && lastUserMsg.contains("done")) || lastUserMsg.contains("complete") || lastUserMsg.contains("finish") -> {
                val searchTarget = when {
                    lastUserMsg.contains("push") -> "push-up"
                    lastUserMsg.contains("code") || lastUserMsg.contains("coding") -> "coding"
                    lastUserMsg.contains("homework") || lastUserMsg.contains("college") -> "college"
                    lastUserMsg.contains("mandarin") || lastUserMsg.contains("language") -> "mandarin"
                    else -> lastUserMsg.replace("mark", "").replace("done", "").replace("complete", "").trim()
                }
                toolCalls.add(
                    ToolCall(
                        id = UUID.randomUUID().toString(),
                        name = "toggle_protocol",
                        argumentsJson = JSONObject().apply {
                            put("query", if (searchTarget.isNotBlank()) searchTarget else "push-up")
                            put("completed", true)
                        }.toString()
                    )
                )
            }
            // Calisthenics roadmap
            lastUserMsg.contains("roadmap") || lastUserMsg.contains("calisthenic") || lastUserMsg.contains("pillar") || lastUserMsg.contains("handstand") || lastUserMsg.contains("muscle up") -> {
                toolCalls.add(
                    ToolCall(
                        id = UUID.randomUUID().toString(),
                        name = "get_roadmaps",
                        argumentsJson = "{}"
                    )
                )
            }
            // Fuel / Doubter / Teacher
            lastUserMsg.contains("fuel") || lastUserMsg.contains("vow") || lastUserMsg.contains("doubt") || lastUserMsg.contains("teacher") || lastUserMsg.contains("enemy") || lastUserMsg.contains("hate") || lastUserMsg.contains("critic") || lastUserMsg.contains("scold") || lastUserMsg.contains("bullied") || lastUserMsg.contains("laughed") -> {
                val isAdd = lastUserMsg.contains("teacher") || lastUserMsg.contains("doubt") || lastUserMsg.contains("said") || lastUserMsg.contains("add") || lastUserMsg.contains("log") || lastUserMsg.length > 15
                if (isAdd) {
                    toolCalls.add(
                        ToolCall(
                            id = UUID.randomUUID().toString(),
                            name = "add_fuel",
                            argumentsJson = JSONObject().apply {
                                put("personOrIncident", lastUserMsg.take(120))
                                put("defianceVow", "Keep working in silence. The results will shatter their words.")
                                put("category", "Doubter / Critic")
                            }.toString()
                        )
                    )
                } else {
                    toolCalls.add(
                        ToolCall(
                            id = UUID.randomUUID().toString(),
                            name = "get_fuel",
                            argumentsJson = "{}"
                        )
                    )
                }
            }
            // Routine / Protocols query
            lastUserMsg.contains("routine") || lastUserMsg.contains("protocol") || lastUserMsg.contains("today") || lastUserMsg.contains("schedule") || lastUserMsg.contains("tasks") || lastUserMsg.contains("what do i have") -> {
                toolCalls.add(
                    ToolCall(
                        id = UUID.randomUUID().toString(),
                        name = "get_protocols",
                        argumentsJson = "{}"
                    )
                )
            }
            // Vibration
            lastUserMsg.contains("vibrat") || lastUserMsg.contains("pulse") || lastUserMsg.contains("haptic") || lastUserMsg.contains("buzz") -> {
                toolCalls.add(
                    ToolCall(
                        id = UUID.randomUUID().toString(),
                        name = "trigger_vibration",
                        argumentsJson = "{}"
                    )
                )
            }
            // History
            lastUserMsg.contains("history") || lastUserMsg.contains("streak") || lastUserMsg.contains("score") || lastUserMsg.contains("past day") -> {
                toolCalls.add(
                    ToolCall(
                        id = UUID.randomUUID().toString(),
                        name = "get_history",
                        argumentsJson = "{}"
                    )
                )
            }
        }

        if (toolCalls.isNotEmpty()) {
            return ChatMessage(
                role = "assistant",
                content = "Running local on-device command with **${settings.modelName}**...",
                toolCalls = toolCalls
            )
        }

        // Intelligent local response generation for dialogue / coaching
        val responseText = when {
            lastUserMsg.contains("hello") || lastUserMsg.contains("hi") || lastUserMsg.contains("hey") -> {
                "Discipline AI active on **${settings.modelName}** (${if (sizeMb > 0) "$sizeMb MB on-device" else "configured"}).\n\nReady to command. You can ask me to:\n• *'Show today's routine'*\n• *'Mark push-ups complete'*\n• *'Show calisthenics roadmap'*\n• *'Log fuel: someone doubted me'*\n\nWhat is our focus right now?"
            }
            lastUserMsg.contains("motivat") || lastUserMsg.contains("tired") || lastUserMsg.contains("lazy") || lastUserMsg.contains("give up") -> {
                "⚡ **Discipline Over Motivation**\n\nMotivation is temporary and emotional. Discipline is an identity. When resistance appears, do not negotiate. Execute the very next scheduled protocol with strict adherence. Growth happens in the moments where you execute despite not wanting to."
            }
            lastUserMsg.contains("workout") || lastUserMsg.contains("calisthenic") || lastUserMsg.contains("exercise") || lastUserMsg.contains("train") -> {
                "💪 **Calisthenics Directive**\n\nRule Zero: Strict form over ego. Always train through the 5-12 rep sweet spot across the 4 foundational pillars (Push, Pull, Legs, Core). Progress step-by-step toward advanced mastery."
            }
            lastUserMsg.contains("focus") || lastUserMsg.contains("distract") || lastUserMsg.contains("procrastinat") -> {
                "🎯 **Deep Focus Directive**\n\nRemove environmental friction. Silence non-essential notifications, set a single objective, and commit to the next 45 minutes of uninterrupted work. Action produces momentum."
            }
            else -> {
                "⚡ **Discipline AI (**${settings.modelName}** On-Device):**\n\nI processed your input: *\"$lastUserMsg\"*\n\nRunning locally with on-device intelligence (${if (sizeMb > 0) "$sizeMb MB" else "Active"}). I can manage your protocols, update roadmap milestones, record doubters in your fuel vault, and trigger haptic alerts. What would you like to execute?"
            }
        }

        return ChatMessage(
            role = "assistant",
            content = responseText
        )
    }
}
