$ErrorActionPreference = 'Stop'
$path='F:\OneDrive\Documents\harness\WenYouTextQuest\app\src\main\java\io\wenyou\textquest\data\llm\ChatClient.kt'
$t=[System.IO.File]::ReadAllText($path)

# 1) 在 class 前插入 ChatResult / Delta
$anchor='class ChatClient(ok: OkHttpClient = defaultClient()) {'
$pre=@'
/** 一次流式/非流式调用的结果：正文 + 思考过程。 */
data class ChatResult(val content: String, val reasoning: String)

/** 内部：一个增量片段（推理 or 正文）。 */
private data class Delta(val reasoning: Boolean, val text: String)

'@
$t=$t.Replace($anchor,$pre+$anchor)

# 2) 替换 streamText 整个函数
$startMark='    suspend fun streamText('
$endMark='    // ---------------- 读取可用模型列表 ----------------'
$si=$t.IndexOf($startMark); $ei=$t.IndexOf($endMark)
if($si -lt 0 -or $ei -lt 0 -or $ei -le $si){ throw 'markers not found' }
$newFn=@'
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
        try {
            withTimeout(90_000) {
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
                                if (cont.isActive) cont.resumeWith(Result.success(ChatResult(full.toString(), reasoningFull.toString())))
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
            throw LlmException("AI 响应超时（90 秒未返回内容）。请检查模型配置、Key 与网络，或切换模型重试。")
        } finally {
            full.toString()
            reasoningFull.toString()
        }
    }

'@
$t=$t.Substring(0,$si)+$newFn+$t.Substring($ei)

# 3) 替换 extractDelta 与 extractWhole
$si2=$t.IndexOf('    private fun extractDelta(')
$ei2=$t.IndexOf('    companion object {')
if($si2 -lt 0 -or $ei2 -lt 0){ throw 'extract markers not found' }
$newExtract=@'
    private fun extractDelta(kind: ProviderKind, element: kotlinx.serialization.json.JsonElement): Delta? {
        val root = element.jsonObject
        return when (kind) {
            ProviderKind.OPENAI_COMPAT -> {
                val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
                val delta = choice["delta"]?.jsonObject
                val reason = delta?.get("reasoning_content")
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

    private fun primText(p: JsonPrimitive): String = p.content.takeUnless { it == "null" } ?: ""

    /** 非流式：从一次性 JSON 响应里提取文本（正文/推理）。 */
    private fun extractWhole(kind: ProviderKind, element: kotlinx.serialization.json.JsonElement): Delta? {
        val root = element.jsonObject
        return when (kind) {
            ProviderKind.OPENAI_COMPAT -> {
                val msg = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                val reason = msg?.get("reasoning_content")
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

'@
$t=$t.Substring(0,$si2)+$newExtract+$t.Substring($ei2)

[System.IO.File]::WriteAllText($path,$t,(New-Object System.Text.UTF8Encoding($false)))
Write-Output ("ChatResult present: "+$t.Contains('data class ChatResult'))
Write-Output ("onReasoning present: "+$t.Contains('onReasoning'))
Write-Output ("Delta present: "+$t.Contains('private data class Delta'))
