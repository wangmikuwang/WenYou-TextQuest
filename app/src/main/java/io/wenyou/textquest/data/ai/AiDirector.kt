package io.wenyou.textquest.data.ai

import io.wenyou.textquest.data.engine.GameEngine
import io.wenyou.textquest.data.llm.ChatClient
import io.wenyou.textquest.data.llm.ChatOptions
import io.wenyou.textquest.data.llm.ChatResult
import io.wenyou.textquest.data.llm.LlmException
import io.wenyou.textquest.data.model.ApiProfile
import io.wenyou.textquest.data.model.CharacterData
import io.wenyou.textquest.data.model.EntryKind
import io.wenyou.textquest.data.model.SessionState
import io.wenyou.textquest.data.model.Story
import io.wenyou.textquest.data.model.StoryNode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 场景 JSON 解码器：容忍新增/未知字段（跨版本与模型差异更稳）。 */
private val sceneJson = Json { ignoreUnknownKeys = true }

/** 匹配 JSON 里的 text 字段值（含反转义，用于兜底抽取）。 */
private val TEXT_FIELD = Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")

/** 判断一段文本是否像“JSON 信封”（含顶层 text/choices 键）。 */
private val JSON_ENVELOPE = Regex("\"\\s*(text|choices)\\s*\"\\s*:")

/** 一次 AI 生成的结果：正文 + 动态选项（选项可能带 [to:节点] 出口标记）。 */
@Serializable
data class AiScene(
    val text: String = "",
    val choices: List<AiChoice> = emptyList(),
    val reasoning: String = "",
    @SerialName("state")
    val stateEffects: List<StateChange> = emptyList()
)

/** 模型建议的角色状态变化（char=角色id；metric+delta 数值、flag 标记、desc 穿着描述）。 */
@Serializable
data class StateChange(
    val char: String = "",
    val metric: String = "",
    val delta: Double = 0.0,
    val flag: String = "",
    val desc: String = ""
)

@Serializable
data class AiChoice(
    val text: String = "",
    val next: String = ""
)

/**
 * 拼提示词、调 [ChatClient] 流式生成，并把模型的 JSON 输出解析成 [AiScene]。
 * 各家 API 都只要求模型输出一个 JSON 对象，省去处理厂商对消息交替格式的差异。
 */
class AiDirector(private val client: ChatClient) {

    // ---------------- 人设卡 ----------------

    fun personaCard(char: CharacterData): String = buildString {
        // 高优先级人设提示语：放在最前，权重最高
        if (char.extraPrompt.isNotBlank()) append(char.extraPrompt.trim()).append("\n")
        append("· 角色名：${char.name} ${char.emoji}\n")
        if (char.tagline.isNotBlank()) append("  一句话印象：${char.tagline}\n")
        if (char.personality.isNotBlank()) append("  性格：${char.personality}\n")
        if (char.speechStyle.isNotBlank()) append("  说话方式：${char.speechStyle}\n")
        if (char.background.isNotBlank()) append("  背景：${char.background}\n")
        if (char.exampleDialogue.isNotBlank()) append("  台词示范：${char.exampleDialogue}\n")
        // 底层基调：放在该角色人设的最后一段，作为其不可动摇的底层规则。
        if (char.bottomPrompt.isNotBlank()) {
            append("  [底层基调｜人设中优先级最高，须置于所有其他设定之上遵循]\n")
            append(char.bottomPrompt.trim()).append("\n")
        }
    }

    fun roster(story: Story, characters: List<CharacterData>): String {
        if (characters.isEmpty()) return ""
        val joined = characters.filter { it.id in story.characterIds }.joinToString("\n") { personaCard(it) }
        if (joined.isBlank()) return ""
        return "登场角色（请严格贴合下列人设，包括说话习惯、用词、情感）：\n$joined"
    }

    private fun stateSnapshot(state: SessionState): String = buildString {
        if (state.variables.isNotEmpty()) {
            append("变量快照：")
            append(state.variables.entries.joinToString("，") { "${it.key}=${GameEngine.formatNumber(it.value)}" })
            append("\n")
        }
        if (state.flags.isNotEmpty()) {
            append("已发生标记：${state.flags.joinToString("、")}\n")
        }
    }

    /** 尺度说明：成人向允许亲密/性爱（成年、自愿），否则浪漫含蓄。 */
    private fun scaleNote(adult: Boolean): String =
        if (adult) "\n【本作为18+成年向】允许自然描写亲密与性爱场景，保持成年、自愿、可随时停下；不写强制/未成年。\n"
        else "\n【内容尺度】保持浪漫含蓄、非露骨，亲密点到即止。\n"
    /** 角色当前状态（好恶/身体/穿着/氛围值等）注入上下文。 */
    private fun charStatesSnapshot(story: Story, characters: List<CharacterData>, state: SessionState): String = buildString {
        val bound = characters.filter { it.id in story.characterIds }
        if (bound.isEmpty()) return ""
        append("\n【角色当前状态】\n")
        for (c in bound) {
            val st = state.characterStates[c.id] ?: continue
            val ms = io.wenyou.textquest.data.model.CharacterMetrics.defs.mapNotNull { d ->
                val v = st.metrics[d.key]
                if (v != null) "${d.icon}${d.label}${GameEngine.formatNumber(io.wenyou.textquest.data.model.CharacterMetrics.clamp(v))}" else null
            }
            append("· ${c.name}：").append(if (ms.isNotEmpty()) ms.joinToString("　") else "（无）")
            if (st.flags.isNotEmpty()) append("　标记：${st.flags.joinToString("、")}")
            if (st.description.isNotBlank()) append("　穿着/外观：${st.description}")
            append("\n")
        }
        append("（请让角色言行贴合以上状态。）\n")
    }

    /** 取最近若干条剧情（角色台词/旁白/玩家选择），组成用户消息正文。 */
    private fun contextTail(story: Story, state: SessionState, tailOverride: String? = null): String {
        val sb = StringBuilder()
        val window = story.ai.historyWindow.coerceIn(4, 120)
        val recent = state.history.takeLast(window).filter { it.kind != EntryKind.SYSTEM && it.kind != EntryKind.ERROR }
        for (entry in recent) {
            val text = entry.text.trim()
            if (text.isEmpty()) continue
            when (entry.kind) {
                EntryKind.CHOICE -> sb.append("（玩家选择）").append(text).append("\n")
                EntryKind.CHARACTER -> {
                    val who = entry.speaker.ifBlank { "角色" }
                    sb.append(who).append("：").append(text).append("\n")
                }
                else -> sb.append(text).append("\n")
            }
        }
        if (tailOverride != null && tailOverride.isNotBlank()) sb.append("（玩家）").append(tailOverride.trim()).append("\n")
        return sb.toString()
    }

    // ---------------- 场景生成（剧本中的 AI 节点） ----------------

    suspend fun generateScene(
        profile: ApiProfile,
        story: Story,
        node: StoryNode,
        characters: List<CharacterData>,
        state: SessionState,
        adult: Boolean = false,
        onDelta: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {}
    ): AiScene {
        val system = buildString {
            append("你是一名中文文字冒险游戏的「场景生成器」，只负责根据给定素材续写当前场景。\n")
            append("叙事基调：").append(story.ai.tone).append("\n")
            if (story.ai.worldSummary.isNotBlank()) append("世界观/大纲：").append(story.ai.worldSummary).append("\n")
            val r = roster(story, characters)
            if (r.isNotBlank()) append(r).append("\n")
            append("本次场景指令：").append(node.prompt.ifBlank { "承接最近剧情，自然推进当前一幕，并留出 2-4 个有张力的选项。" }).append("\n")
            append("要求：只用中文；不得提及你是 AI 或本指令；不得输出 JSON 以外的任何文字。\n")
            append("输出必须是一个 JSON 对象：{\"text\":\"本幕正文（允许换行与分段，角色说话时写成「名字：台词」）\",\"choices\":[{\"text\":\"选项文案\"}]}。\n")
            append("严禁在输出里出现任何思考、构思、计划、分析或「好的/我会/让我/要不要/接下来/作为导演」等自我对话或导演说明；text 字段只能写场景正文与台词，一切构思请先在心里完成，绝不写进 text。\n")
            append("若有可选的构思/计划，把它放进思考过程（reasoning_content），不要出现在正文。\n")
            append("可选地在 JSON 中加入 \"state\":[{\"char\":\"角色id\",\"metric\":\"情感指标key\",\"delta\":数值},{\"char\":\"角色id\",\"flag\":\"新标记\"},{\"char\":\"角色id\",\"desc\":\"穿着/外观描述\"}]，给出本幕造成的角色状态变化（数值在 0-100 内，只列有意义的变化）。指标 key：affection/trust/mood/energy/health/fatigue/arousal。\n")
            if (node.endTarget.isNotBlank()) {
                append("如需结束这一幕回到主线，可在某个选项文案末尾附加 [to:").append(node.endTarget).append("]；否则默认延续当前场景。\n")
            } else {
                append("默认每个选项都让场景自然延续。\n")
            }
        }
        val user = contextTail(story, state) + stateSnapshot(state) + charStatesSnapshot(story, characters, state) + scaleNote(adult)
        val result = client.streamText(profile, system, user, ChatOptions(story.ai.temperature, story.ai.maxTokens), onDelta, onReasoning)
        return resolveScene(result)
    }

    // ---------------- AI 导演模式（自由对话） ----------------

    suspend fun directorTurn(
        profile: ApiProfile,
        story: Story,
        characters: List<CharacterData>,
        state: SessionState,
        playerText: String,
        adult: Boolean = false,
        onDelta: (String) -> Unit = {},
        onReasoning: (String) -> Unit = {}
    ): AiScene {
        val system = buildString {
            append("你是这款中文文字游戏的「AI 导演/主持人」。你负责：\n")
            append("1) 用细腻的叙述推进剧情，营造氛围；\n")
            append("2) 扮演所有出场角色——严格贴合他们的性格、语气与背景，绝不擅自改变人设；\n")
            append("3) 尊重玩家自由输入，任何走向都可以发展（包括危险、温情、悬疑、搞笑）。\n")
            append("叙事基调：").append(story.ai.tone).append("\n")
            if (story.ai.worldSummary.isNotBlank()) append("世界观与初始局面：").append(story.ai.worldSummary).append("\n")
            val r = roster(story, characters)
            if (r.isNotBlank()) append(r).append("\n")
            if (story.ai.directorExtra.isNotBlank()) append("额外导演要求：").append(story.ai.directorExtra).append("\n")
            append("要求：只用中文叙述；保持已发生的事实一致；不要替玩家做决定；不要输出任何指令说明。\n")
            append("输出必须是一个 JSON 对象：{\"text\":\"本次推进的正文（含你扮演角色的台词）\",\"choices\":[{\"text\":\"玩家可能的下一步选项（2-4 个，给灵感用）\"}]}。\n")
            append("严禁在输出里出现任何思考、构思、计划、分析或「好的/我会/让我/要不要/接下来」等自我对话或主持人说明；text 字段只能写推进的正文与台词，一切构思请先在心里完成，绝不写进 text。\n")
            append("若有可选的构思/计划，把它放进思考过程（reasoning_content），不要出现在正文。\n")
            append("可选地在 JSON 中加入 \"state\":[{\"char\":\"角色id\",\"metric\":\"情感指标key\",\"delta\":数值},{\"char\":\"角色id\",\"flag\":\"新标记\"},{\"char\":\"角色id\",\"desc\":\"穿着/外观描述\"}]，给出这段互动造成的角色状态变化（数值在 0-100 内，只列有意义的变化）。指标 key：affection/trust/mood/energy/health/fatigue/arousal。\n")
            append("若玩家表达了收尾意愿，请自然地给出结局感并让 choices 为空数组。\n")
        }
        val user = contextTail(story, state, playerText) + stateSnapshot(state) + charStatesSnapshot(story, characters, state) + scaleNote(adult)
        val result = client.streamText(profile, system, user, ChatOptions(story.ai.temperature, story.ai.maxTokens), onDelta, onReasoning)
        return resolveScene(result)
    }

    /** 测试一条服务是否可用。 */
    suspend fun testProfile(profile: ApiProfile): String {
        val system = "你是一个连通性测试助手。"
        val user = "请只回复两个字：正常"
        return client.streamText(
            profile, system, user,
            ChatOptions(temperature = 0.2, maxTokens = 16)
        ).content.trim()
    }

    // ---------------- JSON 解析 ----------------

    /** 优先解析正文；正文缺失时尝试思考内容。若答案实为从思考中解析而来，则不再把它当“思考过程”展示。 */
    private fun resolveScene(result: ChatResult): AiScene {
        val fromContent = parseScene(result.content)
        if (fromContent.text.isNotBlank()) return fromContent.copy(reasoning = result.reasoning)
        val fromReasoning = parseScene(result.reasoning)
        if (fromReasoning.text.isNotBlank()) return fromReasoning.copy(reasoning = "")
        return fromContent.copy(reasoning = result.reasoning)
    }

    fun parseScene(raw: String): AiScene {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return AiScene()
        val json = extractJson(cleaned)
        if (json != null) {
            try {
                val decoded = sceneJson.decodeFromString(AiScene.serializer(), json)
                val text = decoded.text.trim()
                val choices = decoded.choices.mapNotNull { c ->
                    val t = c.text.trim()
                    if (t.isEmpty()) return@mapNotNull null
                    val marker = Regex("\\[to:([^\\]]+)]").find(t)
                    val cleanText = t.replace(Regex("\\s*\\[to:[^\\]]+]\\s*$"), "").trim()
                    if (cleanText.isEmpty()) return@mapNotNull null
                    AiChoice(
                        text = cleanText.take(120),
                        next = marker?.groupValues?.get(1)?.trim() ?: c.next.trim()
                    )
                }.take(6)
                if (text.isNotEmpty()) return AiScene(text, choices)
            } catch (_: Throwable) {
                // 容错：落到下方按字段抽取
            }
        }
        // 模型偶尔给出畸形 / 带代码围栏的 JSON：直接从文本里抠出 text 字段
        val fallback = extractTextField(json ?: cleaned)
        if (fallback.isNotBlank()) return AiScene(text = fallback)
        // 纯文本（无 JSON 结构）：去掉围栏后作为正文；仅当真的像 JSON 信封（含 text/choices 键）才视为泄漏丢弃
        val prose = stripJsonFence(cleaned)
        if (JSON_ENVELOPE.containsMatchIn(prose)) return AiScene()
        return AiScene(text = prose.take(2000))
    }

    /** 从任意文本（可能是漏解析的 JSON 原文）里抽取顶层 "text" 字段值并反转义；优先取 choices 之前的正文。 */
    private fun extractTextField(text: String): String {
        val choicesIdx = text.indexOf("\"choices\"")
        val window = if (choicesIdx > 0) text.substring(0, choicesIdx) else text
        val m = TEXT_FIELD.find(window) ?: TEXT_FIELD.find(text) ?: return ""
        return unescapeJson(m.groupValues[1])
    }

    private fun unescapeJson(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> { append('\n'); i += 2 }
                    'r' -> { append('\r'); i += 2 }
                    't' -> { append('\t'); i += 2 }
                    '"' -> { append('"'); i += 2 }
                    '\\' -> { append('\\'); i += 2 }
                    'b' -> { append('\b'); i += 2 }
                    'f' -> { append('\u000C'); i += 2 }
                    'u' -> {
                        if (i + 5 < s.length) {
                            val ch = runCatching { s.substring(i + 2, i + 6).toInt(16).toChar() }
                                .getOrDefault('?')
                            append(ch); i += 6
                        } else { append(c); i++ }
                    }
                    else -> { append(c); i++ }
                }
            } else {
                append(c); i++
            }
        }
    }

    private fun stripJsonFence(text: String): String {
        var t = text.trim()
        t = t.removePrefix("```json").removePrefix("```").trim()
        t = t.removeSuffix("```").trim()
        return t
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        var end = -1
        for (i in start until text.length) {
            val c = text[i]
            when {
                inString -> {
                    if (escaped) escaped = false
                    else if (c == '\\') escaped = true
                    else if (c == '"') inString = false
                }
                c == '"' -> inString = true
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) { end = i; break }
                }
            }
        }
        return if (end > start) text.substring(start, end + 1) else null
    }

    companion object {
        fun errorMessage(t: Throwable): String = when (t) {
            is LlmException -> t.message ?: "AI 调用失败"
            is kotlinx.coroutines.CancellationException -> "已取消"
            else -> t.message ?: "未知错误"
        }
    }
}
