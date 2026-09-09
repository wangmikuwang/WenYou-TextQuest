package io.wenyou.textquest.data.ai

import io.wenyou.textquest.data.engine.GameEngine
import io.wenyou.textquest.data.llm.ChatClient
import io.wenyou.textquest.data.llm.ChatOptions
import io.wenyou.textquest.data.llm.LlmException
import io.wenyou.textquest.data.model.ApiProfile
import io.wenyou.textquest.data.model.CharacterData
import io.wenyou.textquest.data.model.EntryKind
import io.wenyou.textquest.data.model.SessionState
import io.wenyou.textquest.data.model.Story
import io.wenyou.textquest.data.model.StoryNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 一次 AI 生成的结果：正文 + 动态选项（选项可能带 [to:节点] 出口标记）。 */
@Serializable
data class AiScene(
    val text: String = "",
    val choices: List<AiChoice> = emptyList()
)

@Serializable
data class AiChoice(
    val text: String = "",
    val next: String = ""
)

/**
 * AI 导演：把「角色性格 + 世界观 + 最近剧情 + 变量快照」组装成提示词，
 * 调用 [ChatClient] 流式生成，并把模型的 JSON 输出解析为 [AiScene]。
 *
 * 设计要点：无论接的是哪家 API，都要求模型只输出一个 JSON 对象，
 * 规避不同厂商对 system/user/assistant 交替的差异。
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
        onDelta: (String) -> Unit = {}
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
            if (node.endTarget.isNotBlank()) {
                append("如需结束这一幕回到主线，可在某个选项文案末尾附加 [to:").append(node.endTarget).append("]；否则默认延续当前场景。\n")
            } else {
                append("默认每个选项都让场景自然延续。\n")
            }
        }
        val user = contextTail(story, state) + stateSnapshot(state) + charStatesSnapshot(story, characters, state) + scaleNote(adult)
        val raw = client.streamText(profile, system, user, ChatOptions(story.ai.temperature, story.ai.maxTokens), onDelta)
        return parseScene(raw)
    }

    // ---------------- AI 导演模式（自由对话） ----------------

    suspend fun directorTurn(
        profile: ApiProfile,
        story: Story,
        characters: List<CharacterData>,
        state: SessionState,
        playerText: String,
        adult: Boolean = false,
        onDelta: (String) -> Unit = {}
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
            append("若玩家表达了收尾意愿，请自然地给出结局感并让 choices 为空数组。\n")
        }
        val user = contextTail(story, state, playerText) + stateSnapshot(state) + charStatesSnapshot(story, characters, state) + scaleNote(adult)
        val raw = client.streamText(profile, system, user, ChatOptions(story.ai.temperature, story.ai.maxTokens), onDelta)
        return parseScene(raw)
    }

    /** 测试一条服务是否可用。 */
    suspend fun testProfile(profile: ApiProfile): String {
        val system = "你是一个连通性测试助手。"
        val user = "请只回复两个字：正常"
        return client.streamText(
            profile, system, user,
            ChatOptions(temperature = 0.2, maxTokens = 16)
        ).trim()
    }

    // ---------------- JSON 解析 ----------------

    fun parseScene(raw: String): AiScene {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return AiScene()
        val json = extractJson(cleaned)
        if (json != null) {
            try {
                val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(AiScene.serializer(), json)
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
                // 落到下面按纯文本处理
            }
        }
        return AiScene(text = cleaned.take(2000))
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
