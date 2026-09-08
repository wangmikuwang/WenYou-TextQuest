package io.wenyou.textquest.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** 全局 JSON 配置：容忍新增字段、未知字段，便于手工编辑与跨版本迁移。 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
}

// ---------------------------------------------------------------------------
// 多品牌 API 档案
// ---------------------------------------------------------------------------

/** 协议类别：绝大多数国产/开源服务走 OpenAI 兼容协议。 */
@Serializable
enum class ProviderKind(val label: String) {
    @SerialName("openai_compat") OPENAI_COMPAT("OpenAI 兼容（DeepSeek/Kimi/GLM/Qwen/OpenRouter…）"),
    @SerialName("anthropic") ANTHROPIC("Anthropic Claude"),
    @SerialName("gemini") GEMINI("Google Gemini")
}

/** 用户配置的一条「服务接入」，key 仅保存在本机。 */
@Serializable
data class ApiProfile(
    val id: String,
    val name: String,
    val kind: ProviderKind = ProviderKind.OPENAI_COMPAT,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Double = 0.85,
    val maxTokens: Int = 1024,
    val note: String = ""
)

// ---------------------------------------------------------------------------
// 角色
// ---------------------------------------------------------------------------

/**
 * 角色卡：性格/说话方式/背景会注入到 AI 人设与叙事系统提示中，
 * 在作者自编节点中则由 [speakerId] 决定气泡归属（纯离线也能用）。
 */
@Serializable
data class CharacterData(
    val id: String,
    val name: String,
    val emoji: String = "🎭",
    val colorIndex: Int = 0,
    val tagline: String = "",
    val personality: String = "",
    val speechStyle: String = "",
    val background: String = "",
    val exampleDialogue: String = "",
    val greeting: String = ""
)

// ---------------------------------------------------------------------------
// 剧情（作者自编 + AI 增强）
// ---------------------------------------------------------------------------

@Serializable
enum class StoryMode(val label: String) {
    @SerialName("script") SCRIPT("分支剧本（可离线游玩）"),
    @SerialName("ai_dm") AI_DIRECTOR("AI 导演（自由对话推进）")
}

@Serializable
enum class NodeKind(val label: String) {
    @SerialName("narration") NARRATION("叙述"),
    @SerialName("ai") AI("AI 生成场景"),
    @SerialName("ending") ENDING("结局")
}

@Serializable
enum class CondType(val label: String) {
    @SerialName("flag_true") FLAG_TRUE("拥有标记"),
    @SerialName("flag_false") FLAG_FALSE("没有标记"),
    @SerialName("var") VAR("变量比较")
}

@Serializable
enum class CompareOp(val label: String) {
    @SerialName("eq") EQ("=="),
    @SerialName("ne") NE("!="),
    @SerialName("gt") GT(">"),
    @SerialName("gte") GTE(">="),
    @SerialName("lt") LT("<"),
    @SerialName("lte") LTE("<=")
}

@Serializable
enum class EffectType(val label: String) {
    @SerialName("set_flag") SET_FLAG("设置标记"),
    @SerialName("clear_flag") CLEAR_FLAG("清除标记"),
    @SerialName("set_var") SET_VAR("变量 = 值"),
    @SerialName("add_var") ADD_VAR("变量 += 值"),
    @SerialName("random_var") RANDOM_VAR("变量 = 区间随机"),
    @SerialName("roll") ROLL("掷骰：变量 = dN 结果")
}

/** 选项显示条件（全部满足才显示）。 */
@Serializable
data class Cond(
    val type: CondType = CondType.VAR,
    val name: String = "",
    val op: CompareOp = CompareOp.GTE,
    val value: Double = 0.0
)

/** 选择/进入节点时执行的效果（可多行，按顺序执行）。 */
@Serializable
data class Effect(
    val type: EffectType = EffectType.SET_FLAG,
    val name: String = "",
    val value: Double = 0.0,
    val from: Double = 0.0,
    val to: Double = 100.0
)

@Serializable
data class ChoiceData(
    val text: String,
    val next: String = "",
    val conditions: List<Cond> = emptyList(),
    val effects: List<Effect> = emptyList(),
    val hint: String = ""
)

@Serializable
data class StoryNode(
    val id: String = "",
    val kind: NodeKind = NodeKind.NARRATION,
    val title: String = "",
    val speakerId: String = "",
    val text: String = "",
    /** kind = AI 时：让模型据此生成这一场景（含变量上下文与最近剧情）。 */
    val prompt: String = "",
    val choices: List<ChoiceData> = emptyList(),
    val onEnter: List<Effect> = emptyList(),
    /** AI 场景 / 结局后的去向：为空表示故事结束或停留。 */
    val endTarget: String = ""
)

/** AI 相关剧本设置。 */
@Serializable
data class AiStorySettings(
    val worldSummary: String = "",
    val tone: String = "以细腻的中文文学性叙述为主，第三人称，节奏自然。",
    val directorExtra: String = "",
    val temperature: Double = 0.95,
    val maxTokens: Int = 900,
    val historyWindow: Int = 40
)

/** 一部可玩的剧情。AI_DIRECTOR 模式下仅用开场节点渲染序章后即进入自由对话。 */
@Serializable
data class Story(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val coverEmoji: String = "📖",
    val colorIndex: Int = 0,
    val genre: String = "",
    val mode: StoryMode = StoryMode.SCRIPT,
    val characterIds: List<String> = emptyList(),
    val startNodeId: String = "start",
    val nodes: Map<String, StoryNode> = emptyMap(),
    val initialVariables: Map<String, Double> = emptyMap(),
    val initialFlags: Set<String> = emptySet(),
    val ai: AiStorySettings = AiStorySettings()
)

// ---------------------------------------------------------------------------
// 对局与存档
// ---------------------------------------------------------------------------

@Serializable
enum class EntryKind(val label: String) {
    @SerialName("narration") NARRATION("旁白"),
    @SerialName("character") CHARACTER("角色"),
    @SerialName("choice") CHOICE("玩家选择"),
    @SerialName("dm") DM("AI 导演"),
    @SerialName("system") SYSTEM("系统"),
    @SerialName("error") ERROR("提示")
}

@Serializable
data class LogEntry(
    val kind: EntryKind = EntryKind.NARRATION,
    val speaker: String = "",
    val speakerId: String = "",
    val text: String,
    val ts: Long = 0L
)

/** 一局游戏的完整状态（可序列化存档）。 */
@Serializable
data class SessionState(
    val storyId: String,
    val currentNodeId: String = "",
    val flags: Set<String> = emptySet(),
    val variables: Map<String, Double> = emptyMap(),
    val history: List<LogEntry> = emptyList(),
    val aiEndless: Boolean = false,
    val updatedAt: Long = 0L
)

@Serializable
data class SaveSlot(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val state: SessionState
)

// ---------------------------------------------------------------------------
// 导入 / 导出
// ---------------------------------------------------------------------------

@Serializable
data class AppBundle(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val providers: List<ApiProfile> = emptyList(),
    val characters: List<CharacterData> = emptyList(),
    val stories: List<Story> = emptyList(),
    val saves: List<SaveSlot> = emptyList()
)

/** 把任意 JSON 安全解析为 [JsonElement] 的辅助（用于导入校验）。 */
fun parseLenient(text: String): JsonElement = AppJson.parseToJsonElement(text)
