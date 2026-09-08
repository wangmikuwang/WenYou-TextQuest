package io.wenyou.textquest.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.ai.AiChoice
import io.wenyou.textquest.data.ai.AiDirector
import io.wenyou.textquest.data.engine.GameEngine
import io.wenyou.textquest.data.model.ApiProfile
import io.wenyou.textquest.data.model.CharacterData
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
            val saveName = if (saveId.isNotBlank())
                library.saves.value.firstOrNull { it.id == saveId }?.name ?: "" else ""
            val base: SessionState = if (saveId.isNotBlank()) {
                library.saves.value.firstOrNull { it.id == saveId }?.state ?: GameEngine.newSession(story)
            } else {
                GameEngine.newSession(story)
            }
            _ui.update {
                it.copy(story = story, characters = chars, aiMode = story.mode == StoryMode.AI_DIRECTOR,
                    activeSaveId = saveId.ifBlank { null }, saveName = saveName,
                    providers = library.providers.value, selectedProviderId = null)
            }
            beginPlay(story, base, isFresh = saveId.isBlank())
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
        if (node.endTarget.isBlank()) return
        _ui.update { it.copy(pendingAiChoices = emptyList(), aiDelta = "") }
        advanceTo(node.endTarget)
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
        _ui.update { it.copy(stage = PlayStage.AI_WORKING, aiDelta = "", pendingAiChoices = emptyList(), providerMissing = false) }
        viewModelScope.launch {
            try {
                val scene = director.generateScene(profile, story, node, ui.characters, s) { delta ->
                    _ui.update { it.copy(aiDelta = it.aiDelta + delta) }
                }
                finishAiScene(scene.text, scene.choices)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                aiFailed(t)
            }
        }
    }

    private fun finishAiScene(text: String, choices: List<AiChoice>) {
        val ui = _ui.value
        val story = ui.story ?: return
        val node = story.nodes[ui.nodeId]
        if (text.isNotBlank()) {
            if (node?.speakerId?.isNotBlank() == true) {
                val speaker = ui.characters.firstOrNull { it.id == node.speakerId }?.name ?: "角色"
                appendEntries(listOf(LogEntry(EntryKind.CHARACTER, speaker = speaker, speakerId = node.speakerId, text = text)))
            } else {
                appendEntries(listOf(LogEntry(EntryKind.NARRATION, text = text)))
            }
        }
        val exit = node?.endTarget
        if (choices.isEmpty()) {
            _ui.update { it.copy(stage = PlayStage.AUTHORED, aiDelta = "", pendingAiChoices = emptyList(), visibleChoices = emptyList()) }
            if (!exit.isNullOrBlank()) {
                advanceTo(exit)
            } else {
                _ui.update { it.copy(lastMessage = "AI 没有给出选项——你可以点「继续」让故事延伸。") }
            }
        } else {
            _ui.update { it.copy(stage = PlayStage.AUTHORED, aiDelta = "", pendingAiChoices = choices, visibleChoices = emptyList()) }
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
        val profile = provider()
        if (profile == null) {
            appendEntries(listOf(LogEntry(EntryKind.ERROR, speaker = "系统",
                text = "AI 导演模式需要先配置并选择一家 AI 服务（「AI 服务」页）。")))
            return
        }
        appendEntries(listOf(LogEntry(EntryKind.CHOICE, speaker = "你", text = trimmed)))
        _ui.update { it.copy(stage = PlayStage.AI_WORKING, aiDelta = "", pendingAiChoices = emptyList()) }
        val s = session ?: return
        viewModelScope.launch {
            try {
                val scene = director.directorTurn(profile, story, ui.characters, s, trimmed) { delta ->
                    _ui.update { it.copy(aiDelta = it.aiDelta + delta) }
                }
                if (scene.text.isNotBlank()) {
                    appendEntries(listOf(LogEntry(EntryKind.DM, speaker = "AI 导演", text = scene.text)))
                }
                _ui.update { it.copy(aiDelta = "", stage = PlayStage.DM_INPUT, pendingAiChoices = scene.choices) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                appendEntries(listOf(LogEntry(EntryKind.ERROR, speaker = "系统", text = "AI 导演出错：${AiDirector.errorMessage(t)}")))
                _ui.update { it.copy(aiDelta = "", stage = PlayStage.DM_INPUT, pendingAiChoices = emptyList()) }
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
        val fresh = GameEngine.newSession(story)
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
        session = s.copy(history = (s.history + entries).takeLast(600), updatedAt = System.currentTimeMillis())
        _ui.update { it.copy(session = session) }
    }
}
