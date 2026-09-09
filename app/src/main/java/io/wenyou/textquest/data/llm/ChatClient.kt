package io.wenyou.textquest.data.llm

import io.wenyou.textquest.data.model.ApiProfile
import io.wenyou.textquest.data.model.AppJson
import io.wenyou.textquest.data.model.ProviderKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 统一的对话消息。 */
enum class LlmRole(val wire: String) { SYSTEM("system"), USER("user"), ASSISTANT("assistant") }

data class LlmMessage(val role: LlmRole, val content: String)

data class ChatOptions(val temperature: Double = 0.85, val maxTokens: Int = 1024)

/** 调用失败（网络 / HTTP / 解析）时向用户展示的可读错误。 */
class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 多品牌流式聊天客户端。OpenAI 兼容、Anthropic、Gemini 三种协议收敛到 [streamText]。 */
/** 一次流式/非流式调用的结果：正文 + 思考过程。 */
data class ChatResult(val content: String, val reasoning: String)

/** 内部：一个增量片段（推理 or 正文）。 */
private data class Delta(val reasoning: Boolean, val text: String)
class ChatClient(ok: OkHttpClient = defaultClient()) {

    private val client = ok

    suspend fun streamText(
        profile: ApiProfile,
        system: String,
        user: String,
        options: ChatOptions = ChatOptions(profile.temperature, profile.maxTokens),
        onDelta: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {}
    ): ChatResult = withContext(Dispatchers.IO) {
        val full = StringBuilder()
        val reasoningFull = StringBuilder()
        val call = buildCall(profile, system, user, options)
        // 推理模型（如 deepseek-reasoner）思考耗时更长，放宽超时
        val timeoutMs = if (profile.model.contains("reasoner", ignoreCase = true)) 150_000L else 90_000L
        try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine<ChatResult> { cont ->
                    cont.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (cont.isCancelled) return
                            cont.resumeWith(Result.failure(LlmException("网络错误：${e.message}", e)))
                        }

                        override fun onResponse(call: Call, response: Response) {
                            try {
                                if (!response.isSuccessful) {
                                    val body = response.body?.string()?.take(400) ?: ""
                                    cont.resumeWith(Result.failure(LlmException("HTTP ${response.code} 服务返回错误：${body.trim().ifBlank { "（无详情）" }}")))
                                    return
                                }
                                val src = response.body?.source() ?: run {
                                    cont.resumeWith(Result.failure(LlmException("空响应")))
                                    return
                                }
                                var sawData = false
                                val raw = StringBuilder()
                                while (true) {
                                    val line = src.readUtf8Line() ?: break
                                    if (line.isBlank()) continue
                                    if (line.startsWith("data:")) {
                                        sawData = true
                                        val payload = line.removePrefix("data:").trim()
                                        if (payload == "[DONE]") break
                                        if (payload.isEmpty()) continue
                                        val d = try { extractDelta(profile.kind, AppJson.parseToJsonElement(payload)) } catch (_: Throwable) { null }
                                        if (d != null && d.text.isNotEmpty() && d.text != "null") {
                                            if (d.reasoning) { reasoningFull.append(d.text); onReasoning(d.text) }
                                            else { full.append(d.text); onDelta(d.text) }
                                        }
                                    } else if (!sawData) {
                                        raw.append(line).append('\n')
                                    }
                                }
                                if (!sawData && raw.isNotBlank()) {
                                    val d = try { extractWhole(profile.kind, AppJson.parseToJsonElement(raw.toString())) } catch (_: Throwable) { null }
                                    if (d != null && d.text.isNotEmpty() && d.text != "null") {
                                        if (d.reasoning) { reasoningFull.append(d.text); onReasoning(d.text) }
                                        else { full.append(d.text); onDelta(d.text) }
                                    }
                                }
                                // 流已结束（[DONE] 或响应流结束）但正文仍为空：视为失败，避免用户看到无提示的空白
                                if (full.isBlank() && reasoningFull.isBlank()) {
                                    if (cont.isActive) cont.resumeWith(Result.failure(LlmException("AI 未返回任何文本（可能被内容安全拦截或模型静默），请重试或换条提示。")))
                                } else if (cont.isActive) cont.resumeWith(Result.success(ChatResult(full.toString(), reasoningFull.toString())))
                            } catch (e: CancellationException) {
                                cont.resumeWith(Result.failure(e))
                            } catch (t: Throwable) {
                                if (cont.isActive) cont.resumeWith(Result.failure(LlmException("读取响应失败：${t.message}", t)))
                            } finally {
                                try { response.close() } catch (_: Throwable) {}
                            }
                        }
                    })
                }
            }
        } catch (e: TimeoutCancellationException) {
            val secs = timeoutMs / 1000
            throw LlmException("AI 响应超时（${secs} 秒未返回内容）。请检查模型配置、Key 与网络，或切换模型重试。")
        } finally {
            full.toString()
            reasoningFull.toString()
        }
    }
    // ---------------- 读取可用模型列表 ----------------

    /**
     * 拉取该服务支持的模型 id 列表：
     * OpenAI 兼容 → GET {base}/models（data[].id）；Anthropic → GET /v1/models；
     * Gemini → GET /models?pageSize=1000（models[].name 去掉 models/ 前缀）。
     */
    suspend fun listModels(profile: ApiProfile): List<String> = withContext(Dispatchers.IO) {
        val call = listModelsCall(profile)
        val response = call.execute()
        try {
            if (!response.isSuccessful) {
                val body = response.body?.string()?.take(300) ?: ""
                throw LlmException("HTTP ${response.code} 读取模型失败：${body.trim().ifBlank { "（无详情）" }}")
            }
            val text = response.body?.string().orEmpty()
            val element = try {
                if (text.isBlank()) null else AppJson.parseToJsonElement(text)
            } catch (_: Throwable) {
                null
            } ?: return@withContext emptyList()
            when (profile.kind) {
                ProviderKind.OPENAI_COMPAT, ProviderKind.ANTHROPIC -> {
                    element.jsonObject["data"]?.jsonArray?.mapNotNull { item ->
                        (item.jsonObject["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    } ?: emptyList()
                }
                ProviderKind.GEMINI -> {
                    element.jsonObject["models"]?.jsonArray?.mapNotNull { item ->
                        (item.jsonObject["name"] as? JsonPrimitive)?.content
                            ?.removePrefix("models/")?.takeIf { it.isNotBlank() }
                    } ?: emptyList()
                }
            }
        } finally {
            try { response.close() } catch (_: Throwable) {}
        }
    }

    private fun listModelsCall(profile: ApiProfile): Call {
        if (profile.baseUrl.isBlank()) throw LlmException("先填好接口地址（baseUrl）")
        val base = profile.baseUrl.trim().trimEnd('/')
        val builder = Request.Builder()
        when (profile.kind) {
            ProviderKind.OPENAI_COMPAT -> {
                val path = when {
                    base.endsWith("/chat/completions") -> base.removeSuffix("/chat/completions")
                    else -> base
                }
                val url = if (path.endsWith("/models")) path else "$path/models"
                builder.url(url)
                if (profile.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${profile.apiKey}")
            }
            ProviderKind.ANTHROPIC -> {
                val url = if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
                builder.url(url)
                builder.header("anthropic-version", "2023-06-01")
                if (profile.apiKey.isNotBlank()) builder.header("x-api-key", profile.apiKey)
            }
            ProviderKind.GEMINI -> {
                builder.url("$base/models?pageSize=1000")
                if (profile.apiKey.isNotBlank()) builder.header("x-goog-api-key", profile.apiKey)
            }
        }
        return client.newCall(builder.build())
    }

    // ---------------- 请求构建 ----------------

    private fun buildCall(profile: ApiProfile, system: String, user: String, options: ChatOptions): Call {
        if (profile.model.isBlank()) throw LlmException("尚未填写模型名（model）")
        val base = profile.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) throw LlmException("尚未填写接口地址（baseUrl）")
        return when (profile.kind) {
            ProviderKind.OPENAI_COMPAT -> openAiCall(profile, base, system, user, options)
            ProviderKind.ANTHROPIC -> anthropicCall(profile, base, system, user, options)
            ProviderKind.GEMINI -> geminiCall(profile, base, system, user, options)
        }
    }

    private fun openAiCall(profile: ApiProfile, base: String, system: String, user: String, options: ChatOptions): Call {
        val url = (if (base.endsWith("/chat/completions")) base else "$base/chat/completions")
        // deepseek-reasoner 为推理模型：temperature 固定不可调（传了通常被忽略），
        // 思考需要更充裕的 max_tokens，且更慢；这里归一化处理以贴合 DeepSeek 行为。
        val isReasoner = profile.model.contains("reasoner", ignoreCase = true)
        val body = buildJsonObject {
            put("model", profile.model)
            put("stream", true)
            if (!isReasoner) put("temperature", options.temperature)
            put("max_tokens", if (isReasoner) maxOf(options.maxTokens, 2048) else options.maxTokens)
            putJsonArray("messages") {
                if (system.isNotBlank()) addJsonObject {
                    put("role", "system"); put("content", system)
                }
                addJsonObject { put("role", "user"); put("content", user) }
            }
        }.toString()
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .apply {
                if (profile.apiKey.isNotBlank()) header("Authorization", "Bearer ${profile.apiKey}")
            }
            .post(body.toRequestBody(JSON))
            .build()
        return client.newCall(request)
    }

    private fun anthropicCall(profile: ApiProfile, base: String, system: String, user: String, options: ChatOptions): Call {
        val url = when {
            base.endsWith("/messages") -> base
            base.endsWith("/v1") -> "$base/messages"
            else -> "$base/v1/messages"
        }
        val body = buildJsonObject {
            put("model", profile.model)
            put("stream", true)
            put("temperature", options.temperature)
            put("max_tokens", options.maxTokens)
            if (system.isNotBlank()) put("system", system)
            putJsonArray("messages") {
                addJsonObject { put("role", "user"); put("content", user) }
            }
        }.toString()
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("anthropic-version", "2023-06-01")
            .apply {
                if (profile.apiKey.isNotBlank()) header("x-api-key", profile.apiKey)
            }
            .post(body.toRequestBody(JSON))
            .build()
        return client.newCall(request)
    }

    private fun geminiCall(profile: ApiProfile, base: String, system: String, user: String, options: ChatOptions): Call {
        val url = "$base/models/${profile.model}:streamGenerateContent?alt=sse"
        val body = buildJsonObject {
            if (system.isNotBlank()) putJsonObject("system_instruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", user) } }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", options.temperature)
                put("maxOutputTokens", options.maxTokens)
            }
        }.toString()
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .apply {
                if (profile.apiKey.isNotBlank()) header("x-goog-api-key", profile.apiKey)
            }
            .post(body.toRequestBody(JSON))
            .build()
        return client.newCall(request)
    }

    private fun extractDelta(kind: ProviderKind, element: kotlinx.serialization.json.JsonElement): Delta? {
        val root = element.jsonObject
        return when (kind) {
            ProviderKind.OPENAI_COMPAT -> {
                val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
                val delta = choice["delta"]?.jsonObject
                val reason = delta?.get("reasoning_content") ?: delta?.get("reasoning")
                val content = delta?.get("content") ?: choice["message"]?.jsonObject?.get("content")
                when {
                    reason != null && reason !is JsonNull -> Delta(true, primText(reason))
                    content != null && content !is JsonNull -> Delta(false, primText(content))
                    else -> null
                }
            }
            ProviderKind.ANTHROPIC -> {
                if (root["type"]?.jsonPrimitive?.content != "content_block_delta") return null
                val t = root["delta"]?.jsonObject?.get("text")
                if (t == null || t is JsonNull) return null
                Delta(false, primText(t))
            }
            ProviderKind.GEMINI -> {
                val candidates = root["candidates"]?.jsonArray ?: return null
                if (candidates.isEmpty()) return null
                val parts = candidates[0].jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray ?: return null
                val el = parts.firstOrNull()?.jsonObject?.get("text") ?: return null
                if (el is JsonNull) return null
                Delta(false, primText(el))
            }
        }
    }

    private fun primText(p: kotlinx.serialization.json.JsonElement): String = when (p) {
        is JsonPrimitive -> p.content.takeUnless { it == "null" } ?: ""
        is JsonArray -> p.joinToString("") { (it as? JsonPrimitive)?.content.orEmpty() }
        else -> ""
    }

    /** 非流式：从一次性 JSON 响应里提取文本（正文/推理）。 */
    private fun extractWhole(kind: ProviderKind, element: kotlinx.serialization.json.JsonElement): Delta? {
        val root = element.jsonObject
        return when (kind) {
            ProviderKind.OPENAI_COMPAT -> {
                val msg = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                val reason = msg?.get("reasoning_content") ?: msg?.get("reasoning")
                val content = msg?.get("content")
                when {
                    reason != null && reason !is JsonNull -> Delta(true, primText(reason))
                    content != null && content !is JsonNull -> Delta(false, primText(content))
                    else -> null
                }
            }
            ProviderKind.ANTHROPIC -> {
                val t = root["content"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")
                if (t == null || t is JsonNull) return null
                Delta(false, primText(t))
            }
            ProviderKind.GEMINI -> {
                val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return null
                val el = parts.firstOrNull()?.jsonObject?.get("text") ?: return null
                if (el is JsonNull) return null
                Delta(false, primText(el))
            }
        }
    }
    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
