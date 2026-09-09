package io.wenyou.textquest.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.ai.AiChoice
import io.wenyou.textquest.data.ai.AiDirector
import io.wenyou.textquest.data.ai.StateChange
import io.wenyou.textquest.data.engine.GameEngine
import io.wenyou.textquest.data.model.ApiProfile
import io.wenyou.textquest.data.model.CharacterData
import io.wenyou.textquest.data.model.CharacterMetrics
import io.wenyou.textquest.data.model.CharacterState
import io.wenyou.textquest.data.model.ChoiceData
import io.wenyou.textquest.data.model.EntryKind
import io.wenyou.textquest.data.model.LogEntry
import io.wenyou.textquest.data.model.NodeKind
import io.wenyou.textquest.data.model.SaveSlot
import io.wenyou.textquest.data.model.SessionState
import io.wenyou.textquest.data.model.Story
import io.wenyou.textquest.data.model.StoryMode
import io.wenyou.textquest.data.model.StoryNode
import io.wenyou.textquest.data.repo.LocalLibrary
import io.wenyou.textquest.data.repo.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class PlayStage { INIT, AUTHORED, DM_INPUT, AI_WORKING, STOPPED }

data class PlayUi(
    val story: Story? = null,
    val session: SessionState? = null,
    val characters: List<CharacterData> = emptyList(),
    val stage: PlayStage = PlayStage.INIT,
    val nodeId: String = "",
    val nodeTitle: String = "",
    val visibleChoices: List<ChoiceData> = emptyList(),
    val pendingAiChoices: List<AiChoice> = emptyList(),
    val aiDelta: String = "",
    val aiReasoningDelta: String = "",
    val aiTargetExit: Boolean = false,
    val stoppedTitle: String = "",
    val stoppedMessage: String = "",
    val aiMode: Boolean = false,
    val providerMissing: Boolean = false,
    val lastMessage: String = "",
    val activeSaveId: String? = null,
    val saveName: String = "",
    val providers: List<ApiProfile> = emptyList(),
    val selectedProviderId: String? = null
)

/**
 * 对局驱动状态机：把「分支引擎 + AI 场景 + AI 导演自由模式」统一成
 * ［开始 → 作者选项 / AI 选项 / 自由输入 → 结局/停止］的流转。
 */
class PlayViewModel(
    private val storyId: String,
    private val saveId: String,
    private val container: WenYouApp.AppContainer
) : ViewModel() {

    private val library: LocalLibrary = container.library
    private val settings: SettingsStore = container.settings
    private val director: AiDirector = container.director

    private val _ui = MutableStateFlow(PlayUi())
    val ui: StateFlow<PlayUi> = _ui.asStateFlow()

    private var session: SessionState? = null

    /** 当前 AI 生成任务。发起新请求前会取消旧任务，避免重试等场景产生并发覆盖。 */
    private var aiJob: Job? = null

    init {
        viewModelScope.launch {
            val story = library.stories.value.firstOrNull { it.id == storyId }
            if (story == null) {
                _ui.update {
                    it.copy(stage = PlayStage.STOPPED, stoppedTitle = "剧情不见了",
                        stoppedMessage = "未找到该剧情（可能已被删除）。")
                }
                return@launch
            }
            val chars = library.characters.value.filter { it.id in story.characterIds }
            // “new” 是路由中开新局的哨兵值，不作为存档 id 加载
            val loadId = saveId.takeIf { it.isNotBlank() && it != "new" }
            val loadedSlot = loadId?.let { library.saves.value.firstOrNull { x -> x.id == it } }
            val saveName = loadedSlot?.name ?: ""
            val base: SessionState = loadedSlot?.state ?: GameEngine.newSession(story, chars)
            _ui.update {
                it.copy(story = story, characters = chars, aiMode = story.mode == StoryMode.AI_DIRECTOR,
                    activeSaveId = loadId, saveName = saveName,
                    providers = library.providers.value, selectedProviderId = null)
            }
            beginPlay(story, base, isFresh = loadedSlot == null)
        }
    }

    // ---------------- 开局 / 读档 ----------------

    private fun beginPlay(story: Story, base: SessionState, isFresh: Boolean) {
        var s = base
        if (isFresh) {
            val arrival = GameEngine.arriveAt(story, base, story.startNodeId)
            s = arrival.state
            session = s
            logSystem(arrival.notes)
        } else {
            session = s
        }
        if (story.mode == StoryMode.AI_DIRECTOR) {
            if (isFresh) logNodeNarration(story.nodes[s.currentNodeId], s)
            _ui.update {
                it.copy(stage = PlayStage.DM_INPUT, nodeId = s.currentNodeId,
                    nodeTitle = story.nodes[s.currentNodeId]?.title.orEmpty(),
                    visibleChoices = emptyList(), pendingAiChoices = emptyList(), providerMissing = provider() == null)
            }
        } else {
            renderNode()
        }
    }

    private fun renderNode() {
        val ui = _ui.value
        val story = ui.story ?: return
        val s = session ?: return
        val nodeId = s.currentNodeId
        val node = story.nodes[nodeId]
        if (node == null) {
            _ui.update {
                it.copy(stage = PlayStage.STOPPED, stoppedTitle = "走神了",
                    stoppedMessage = "这个剧情节点不存在，故事戛然而止。")
            }
            return
        }
        when (node.kind) {
            NodeKind.NARRATION -> {
                logNodeNarration(node, s)
                val choices = GameEngine.visibleChoices(s, story, nodeId)
                _ui.update {
                    it.copy(stage = PlayStage.AUTHORED, nodeId = nodeId, nodeTitle = node.title,
                        visibleChoices = choices, pendingAiChoices = emptyList(),
                        providerMissing = provider() == null, aiDelta = "")
                }
                if (choices.isEmpty() && node.endTarget.isNotBlank() && node.endTarget != nodeId) {
                    advanceTo(node.endTarget)
                } else if (choices.isEmpty()) {
                    _ui.update {
                        it.copy(stage = PlayStage.STOPPED, stoppedTitle = node.title.ifBlank { "未完待续" },
                            stoppedMessage = "作者还没给这个节点写后续选项。\n\n你可以在「剧情编辑」里补上分支，或重新开始。")
                    }
                }
            }
            NodeKind.ENDING -> {
                val rendered = GameEngine.renderTemplate(node.text, s.variables)
                appendEntries(listOf(LogEntry(EntryKind.NARRATION, text = rendered)))
                _ui.update {
                    it.copy(stage = PlayStage.STOPPED, nodeId = nodeId,
                        stoppedTitle = node.title.ifBlank { "结局" },
                        stoppedMessage = "你抵达了这个故事的结局。")
                }
            }
            NodeKind.AI -> {
                _ui.update {
                    it.copy(stage = PlayStage.AI_WORKING, nodeId = nodeId, nodeTitle = node.title,
                        visibleChoices = emptyList(), pendingAiChoices = emptyList(),
                        aiDelta = "", aiTargetExit = node.endTarget.isNotBlank(),
                        providerMissing = provider() == null)
                }
                runAiScene()
            }
        }
    }

    private fun logNodeNarration(node: StoryNode?, s: SessionState) {
        if (node == null || node.text.isBlank()) return
        val rendered = GameEngine.renderTemplate(node.text, s.variables)
        if (node.speakerId.isNotBlank()) {
            val speaker = _ui.value.characters.firstOrNull { it.id == node.speakerId }?.name ?: "角色"
            appendEntries(listOf(LogEntry(EntryKind.CHARACTER, speaker = speaker, speakerId = node.speakerId, text = rendered)))
        } else {
            appendEntries(listOf(LogEntry(EntryKind.NARRATION, text = rendered)))
        }
    }

    private fun advanceTo(target: String) {
        val story = _ui.value.story ?: return
        val s = session ?: return
        val arrival = GameEngine.arriveAt(story, s, target)
        session = arrival.state
        logSystem(arrival.notes)
        renderNode()
    }

    // ---------------- 玩家动作 ----------------

    fun chooseAuthored(index: Int) {
        if (_ui.value.stage != PlayStage.AUTHORED) return
        val ui = _ui.value
        val story = ui.story ?: return
        val choices = ui.visibleChoices
        if (index !in choices.indices) return
        chooseBy(story, choices[index], ui.nodeId)
    }

    private fun chooseBy(story: Story, choice: ChoiceData, fromNodeId: String) {
        val s = session ?: return
        appendEntries(listOf(LogEntry(EntryKind.CHOICE, speaker = "你", text = choice.text)))
        val outcome = GameEngine.applyEffects(s, choice.effects)
        session = outcome.state
        logSystem(outcome.notes)
        val node = story.nodes[fromNodeId]
        val target = choice.next.ifBlank { "@self" }
        val self = target == "@self" || target == fromNodeId
        if (!self) {
            val arrival = GameEngine.arriveAt(story, outcome.state, target)
            session = arrival.state
            logSystem(arrival.notes)
            renderNode()
        } else if (node?.kind == NodeKind.AI) {
            runAiScene()
        } else {
            // 作者把选项指回本节点：留在原地，重新展示本节点选项（不重复正文）
            _ui.update {
                it.copy(stage = PlayStage.AUTHORED, visibleChoices = GameEngine.visibleChoices(outcome.state, story, fromNodeId),
                    pendingAiChoices = emptyList())
            }
        }
    }

    fun chooseAi(index: Int) {
        if (_ui.value.stage != PlayStage.AUTHORED) return
        val ui = _ui.value
        val story = ui.story ?: return
        val choice = ui.pendingAiChoices.getOrNull(index) ?: return
        appendEntries(listOf(LogEntry(EntryKind.CHOICE, speaker = "你", text = choice.text)))
        val next = choice.next.ifBlank { "@self" }
        if (next != "@self" && story.nodes.containsKey(next)) {
            _ui.update { it.copy(pendingAiChoices = emptyList(), aiDelta = "") }
            advanceTo(next)
        } else {
            runAiScene()
        }
    }

    /** AI 场景结束该段，前往作者设定的主线出口。 */
    fun aiExitToMainline() {
        val story = _ui.value.story ?: return
        val node = story.nodes[_ui.value.nodeId] ?: return
        val target = node.endTarget
        if (target.isBlank() || target == node.id || !story.nodes.containsKey(target)) return
        _ui.update { it.copy(pendingAiChoices = emptyList(), aiDelta = "") }
        advanceTo(target)
    }

    // ---------------- AI 场景 / 导演 ----------------

    private fun runAiScene() {
        val ui = _ui.value
        val story = ui.story ?: return
        val s = session ?: return
        val node = story.nodes[ui.nodeId] ?: return
        val profile = provider()
        if (profile == null) {
            appendEntries(listOf(LogEntry(EntryKind.ERROR, speaker = "系统",
                text = "这个场景需要 AI 生成，但还没有可用的 AI 服务。请到「AI 服务」添加并设为默认，再重试。")))
            _ui.update {
                it.copy(stage = PlayStage.STOPPED, stoppedTitle = "缺少 AI 服务",
                    stoppedMessage = "前往「AI 服务」页添加任一家（支持 OpenAI 兼容 / Claude / Gemini / 本地 Ollama）。")
            }
            return
        }
        // 已有生成任务在运行时，不再发起新的并发请求（覆盖快速连点等场景）
        if (aiJob?.isActive == true) return
        _ui.update { it.copy(stage = PlayStage.AI_WORKING, aiDelta = "", aiReasoningDelta = "", pendingAiChoices = emptyList(), providerMissing = false) }
        launchAiJob { job ->
            try {
                val scene = director.generateScene(profile, story, node, ui.characters, s,
                    adult = story.adult,
                    bottomRules = library.bottomRules.value,
                    onReasoning = { r -> _ui.update { it.copy(aiReasoningDelta = it.aiReasoningDelta + r) } },
                    onDelta = { delta -> _ui.update { it.copy(aiDelta = it.aiDelta + delta) } })
                if (job.isActive && aiJob === job) {
                    finishAiScene(scene.text, scene.choices, scene.reasoning, scene.stateEffects)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (job.isActive) aiFailed(t)
            }
        }
    }

    /** 在 [viewModelScope] 中串行执行 AI 请求：取消旧的、记录当前任务。 */
    private fun launchAiJob(block: suspend (Job) -> Unit) {
        aiJob?.cancel()
        val job = viewModelScope.launch {
            val self = coroutineContext[Job] ?: return@launch
            try {
                block(self)
            } finally {
                if (aiJob === self) aiJob = null
            }
        }
        aiJob = job
    }

    /** 应用 AI 建议的角色状态变化，并打印「✨ 状态变化」日志。 */
    private fun applyStateChanges(changes: List<StateChange>) {
        if (changes.isEmpty()) return
        val s = session ?: return
        val ui = _ui.value
        var charStates = s.characterStates
        val parts = mutableListOf<String>()
        for (c in changes) {
            val char = ui.characters.firstOrNull { it.id == c.char } ?: continue
            val st = charStates[c.char] ?: CharacterState()
            var metrics = st.metrics
            var flags = st.flags
            var desc = st.description
            if (c.metric.isNotBlank() && c.delta != 0.0) {
                val ov = metrics[c.metric] ?: 0.0
                val nv = CharacterMetrics.clamp(ov + c.delta)
                if (nv != ov) {
                    metrics = metrics + (c.metric to nv)
                    val sign = if (c.delta > 0) "+" else ""
                    parts += "${CharacterMetrics.icon(c.metric)}${char.name} ${CharacterMetrics.label(c.metric)}$sign${GameEngine.formatNumber(c.delta)}"
                }
            }
            if (c.flag.isNotBlank()) flags = flags + c.flag
            if (c.desc.isNotBlank()) desc = c.desc
            if (metrics != st.metrics || flags != st.flags || desc != st.description) {
                charStates = charStates + (c.char to st.copy(metrics = metrics, flags = flags, description = desc))
            }
        }
        if (parts.isNotEmpty() || charStates != s.characterStates) {
            session = s.copy(characterStates = charStates, updatedAt = System.currentTimeMillis())
            if (parts.isNotEmpty()) {
                appendEntries(listOf(LogEntry(EntryKind.SYSTEM, speaker = "系统", text = "✨ 状态变化：${parts.joinToString("　")}")))
            }
        }
    }

    private fun finishAiScene(text: String, choices: List<AiChoice>, reasoning: String = "", stateEffects: List<StateChange> = emptyList()) {
        val ui = _ui.value
        val story = ui.story ?: return
        val node = story.nodes[ui.nodeId]
        applyStateChanges(stateEffects)
        if (text.isNotBlank()) {
            if (node?.speakerId?.isNotBlank() == true) {
                val speaker = ui.characters.firstOrNull { it.id == node.speakerId }?.name ?: "角色"
                appendEntries(listOf(LogEntry(EntryKind.CHARACTER, speaker = speaker, speakerId = node.speakerId, text = text, reasoning = reasoning)))
            } else {
                appendEntries(listOf(LogEntry(EntryKind.NARRATION, text = text, reasoning = reasoning)))
            }
        }
        // 只有指向真实存在的其它节点才算有效出口，避免死循环 / 跳到不存在的剧情
        val exit = node?.endTarget?.takeIf { it.isNotBlank() && it != node.id && story.nodes.containsKey(it) }
        if (choices.isEmpty()) {
            _ui.update { it.copy(stage = PlayStage.AUTHORED, aiDelta = "", aiReasoningDelta = "", pendingAiChoices = emptyList(), visibleChoices = emptyList()) }
            if (exit != null) {
                advanceTo(exit)
            } else {
                _ui.update { it.copy(lastMessage = "AI 没有给出选项——你可以点「继续」让故事延伸。") }
            }
        } else {
            _ui.update { it.copy(stage = PlayStage.AUTHORED, aiDelta = "", aiReasoningDelta = "", pendingAiChoices = choices, visibleChoices = emptyList()) }
        }
    }

    fun continueAi() = runAiScene()

    fun retryAi() = runAiScene()

    private fun aiFailed(t: Throwable) {
        val message = AiDirector.errorMessage(t)
        appendEntries(listOf(LogEntry(EntryKind.ERROR, speaker = "系统", text = "AI 生成失败：$message")))
        _ui.update {
            it.copy(stage = PlayStage.STOPPED, stoppedTitle = "AI 生成失败",
                stoppedMessage = "$message\n\n你可以点「重试」，或（若设了主线出口）「回到主线」。")
        }
    }

    /** AI 导演自由对话。 */
    fun dmSend(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val ui = _ui.value
        val story = ui.story ?: return
        if (story.mode != StoryMode.AI_DIRECTOR) return
        val s = session ?: return
        val profile = provider()
        if (profile == null) {
            appendEntries(listOf(LogEntry(EntryKind.ERROR, speaker = "系统",
                text = "AI 导演模式需要先配置并选择一家 AI 服务（「AI 服务」页）。")))
            return
        }
        if (_ui.value.stage == PlayStage.AI_WORKING) return
        appendEntries(listOf(LogEntry(EntryKind.CHOICE, speaker = "你", text = trimmed)))
        _ui.update { it.copy(stage = PlayStage.AI_WORKING, aiDelta = "", aiReasoningDelta = "", pendingAiChoices = emptyList()) }
        launchAiJob { job ->
            try {
                val scene = director.directorTurn(profile, story, ui.characters, s, trimmed,
                    adult = story.adult,
                    bottomRules = library.bottomRules.value,
                    onReasoning = { r -> _ui.update { it.copy(aiReasoningDelta = it.aiReasoningDelta + r) } },
                    onDelta = { delta -> _ui.update { it.copy(aiDelta = it.aiDelta + delta) } })
                if (job.isActive && aiJob === job) {
                    if (scene.text.isNotBlank()) {
                        appendEntries(listOf(LogEntry(EntryKind.DM, speaker = "AI 导演", text = scene.text, reasoning = scene.reasoning)))
                        applyStateChanges(scene.stateEffects)
                    }
                    _ui.update { it.copy(aiDelta = "", aiReasoningDelta = "", stage = PlayStage.DM_INPUT, pendingAiChoices = scene.choices) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (job.isActive) {
                    appendEntries(listOf(LogEntry(EntryKind.ERROR, speaker = "系统", text = "AI 导演出错：${AiDirector.errorMessage(t)}")))
                    _ui.update { it.copy(aiDelta = "", aiReasoningDelta = "", stage = PlayStage.DM_INPUT, pendingAiChoices = emptyList()) }
                }
            }
        }
    }

    // ---------------- 存档 / 重开 ----------------

    fun saveNow() {
        viewModelScope.launch {
            val s = session ?: return@launch
            val ui = _ui.value
            val story = ui.story ?: return@launch
            val now = System.currentTimeMillis()
            val name = ui.saveName.ifBlank { "${story.title} · ${s.history.size} 步" }
            val existing = ui.activeSaveId
            val id = existing ?: UUID.randomUUID().toString()
            val createdAt = existing?.let { library.saves.value.firstOrNull { x -> x.id == it }?.createdAt } ?: now
            library.upsertSave(SaveSlot(id, name, createdAt, now, s))
            _ui.update { it.copy(activeSaveId = id, saveName = name, lastMessage = "已存档「$name」") }
        }
    }

    fun restart() {
        val story = _ui.value.story ?: return
        // 若仍有未结束的生成任务，先取消，避免旧结果写入新开局
        aiJob?.cancel()
        aiJob = null
        val fresh = GameEngine.newSession(story, _ui.value.characters)
        _ui.update {
            it.copy(activeSaveId = null, saveName = "", lastMessage = "",
                pendingAiChoices = emptyList(), aiDelta = "", stoppedTitle = "", stoppedMessage = "")
        }
        beginPlay(story, fresh, isFresh = true)
    }

    // ---------------- 内部 ----------------

    private fun provider(): ApiProfile? {
        val list = library.providers.value
        if (list.isEmpty()) return null
        val overridden = _ui.value.selectedProviderId
        val def = when {
            overridden != null -> list.firstOrNull { it.id == overridden }
            else -> list.firstOrNull { it.id == settings.defaultProviderId }
        }
        return def ?: list.first()
    }

    /** 对局内临时切换使用的 AI 服务（null = 跟随默认/第一个可用）。 */
    fun selectProvider(id: String?) {
        _ui.update { it.copy(selectedProviderId = id) }
    }

    private fun logSystem(notes: List<String>) {
        if (notes.isEmpty()) return
        appendEntries(notes.map { LogEntry(EntryKind.SYSTEM, speaker = "系统", text = it) })
    }

    private fun appendEntries(entries: List<LogEntry>) {
        val s = session ?: return
        val now = System.currentTimeMillis()
        val stamped = entries.map { if (it.ts == 0L) it.copy(ts = now) else it }
        session = s.copy(history = (s.history + stamped).takeLast(600), updatedAt = now)
        _ui.update { it.copy(session = session) }
    }
}
