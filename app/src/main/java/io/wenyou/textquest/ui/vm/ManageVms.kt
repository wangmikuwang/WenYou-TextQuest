package io.wenyou.textquest.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.llm.ProviderCatalog
import io.wenyou.textquest.data.llm.ProviderPreset
import io.wenyou.textquest.data.model.ApiProfile
import io.wenyou.textquest.data.model.AppBundle
import io.wenyou.textquest.data.model.AppJson
import io.wenyou.textquest.data.model.BottomRule
import io.wenyou.textquest.data.model.CharacterData
import io.wenyou.textquest.data.model.CharacterMetrics
import io.wenyou.textquest.data.model.ProviderKind
import io.wenyou.textquest.data.model.SexualOrientation
import io.wenyou.textquest.data.repo.LocalLibrary
import io.wenyou.textquest.data.repo.SettingsStore
import io.wenyou.textquest.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// ---------------- 角色编辑 ----------------

data class CharacterEditorState(
    val char: CharacterData? = null,
    val isNew: Boolean = true,
    val message: String = "",
    val flagsText: String = "",
    val availableRules: List<BottomRule> = emptyList()
)

class CharacterEditorViewModel(
    private val charId: String?,
    container: WenYouApp.AppContainer
) : ViewModel() {

    private val library: LocalLibrary = container.library

    private val _ui = MutableStateFlow(CharacterEditorState(isNew = charId == null))
    val ui: StateFlow<CharacterEditorState> = _ui.asStateFlow()

    init {
        _ui.update { it.copy(availableRules = library.bottomRules.value) }
        if (charId != null) {
            val c = library.characters.value.firstOrNull { it.id == charId }
            if (c == null) _ui.update { it.copy(message = "未找到该角色") }
            else _ui.update { it.copy(char = c, isNew = false, flagsText = c.initial.flags.joinToString(", ")) }
        } else {
            _ui.update {
                it.copy(
                    char = CharacterData(id = UUID.randomUUID().toString(), name = ""),
                    flagsText = ""
                )
            }
        }
    }

    private fun current() = _ui.value.char ?: CharacterData(id = UUID.randomUUID().toString(), name = "")
    private fun update(t: (CharacterData) -> CharacterData) =
        _ui.update { it.copy(char = t(it.char ?: current())) }

    fun setName(v: String) = update { it.copy(name = v) }
    fun setEmoji(v: String) = update { it.copy(emoji = v) }
    fun setColor(v: Int) = update { it.copy(colorIndex = v) }
    fun setTagline(v: String) = update { it.copy(tagline = v) }
    fun setPersonality(v: String) = update { it.copy(personality = v) }
    fun setSpeech(v: String) = update { it.copy(speechStyle = v) }
    fun setBackground(v: String) = update { it.copy(background = v) }
    fun setExample(v: String) = update { it.copy(exampleDialogue = v) }
    fun setExtraPrompt(v: String) = update { it.copy(extraPrompt = v) }
    fun setBottomPrompt(v: String) = update { it.copy(bottomPrompt = v) }
    fun setBottomRuleIds(ids: List<String>) = update { it.copy(bottomRuleIds = ids) }
    fun setGreeting(v: String) = update { it.copy(greeting = v) }

    fun setInitialMetric(key: String, v: Double) = update {
        it.copy(initial = it.initial.copy(
            metrics = it.initial.metrics + (key to CharacterMetrics.clamp(v))
        ))
    }
    fun setInitialFlagsText(v: String) = _ui.update { it.copy(flagsText = v) }
    fun setInitialDesc(v: String) = update { it.copy(initial = it.initial.copy(description = v)) }

    fun setOrientation(v: SexualOrientation) = update { it.copy(orientation = v) }

    /** 内容分类标记：影响角色库分类与内容开关过滤。 */
    fun setLgbt(v: Boolean) = update { it.copy(lgbt = v) }
    fun setAdult(v: Boolean) = update { it.copy(adult = v) }

    fun save() {
        val uiState = _ui.value
        val c = uiState.char ?: current()
        if (c.name.isBlank()) {
            _ui.update { it.copy(message = "角色的名字不能为空") }
            return
        }
        val flags = uiState.flagsText
            .split(Regex("[,\\n，、\\s]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val toSave = c.copy(
            id = c.id.ifBlank { UUID.randomUUID().toString() },
            initial = c.initial.copy(flags = flags)
        )
        viewModelScope.launch {
            try {
                library.upsertCharacter(toSave)
                _ui.update { it.copy(message = "已保存「${toSave.name}」", isNew = false) }
            } catch (t: Throwable) {
                _ui.update { it.copy(message = "保存失败：${t.message}") }
            }
        }
    }
}

// ---------------- AI 服务编辑 ----------------

data class ProviderEditorState(
    val profile: ApiProfile? = null,
    val isNew: Boolean = true,
    val testing: Boolean = false,
    val message: String = "",
    val availableModels: List<String> = emptyList(),
    val listingModels: Boolean = false,
    val listMessage: String = ""
)

class ProviderEditorViewModel(
    private val providerId: String?,
    container: WenYouApp.AppContainer
) : ViewModel() {

    private val library: LocalLibrary = container.library
    private val director = container.director
    private val chatClient = container.chatClient

    private val _ui = MutableStateFlow(ProviderEditorState(isNew = providerId == null))
    val ui: StateFlow<ProviderEditorState> = _ui.asStateFlow()

    val presets: List<ProviderPreset> = ProviderCatalog.presets()

    init {
        if (providerId != null) {
            val p = library.providers.value.firstOrNull { it.id == providerId }
            if (p == null) _ui.update { it.copy(message = "未找到该服务") }
            else _ui.update { it.copy(profile = p, isNew = false) }
        } else {
            _ui.update { it.copy(profile = freshFromPreset("openai")) }
        }
    }

    private fun freshFromPreset(key: String): ApiProfile {
        val preset = ProviderCatalog.find(key) ?: ProviderCatalog.find("openai")!!
        return ApiProfile(
            id = UUID.randomUUID().toString(),
            name = preset.label,
            kind = preset.kind,
            baseUrl = preset.baseUrl,
            model = preset.models.firstOrNull().orEmpty(),
            note = preset.note
        )
    }

    private fun profile() = _ui.value.profile
        ?: ApiProfile(id = UUID.randomUUID().toString(), name = "", kind = ProviderKind.OPENAI_COMPAT)

    private fun update(t: (ApiProfile) -> ApiProfile) =
        _ui.update { it.copy(profile = t(it.profile ?: profile())) }

    fun applyPreset(key: String) {
        val preset = ProviderCatalog.find(key) ?: return
        _ui.update {
            val current = it.profile
            val effective = current ?: freshFromPreset(key)
            it.copy(
                profile = effective.copy(
                    name = if (effective.name.isBlank()) preset.label else effective.name,
                    kind = preset.kind,
                    baseUrl = preset.baseUrl,
                    model = if (current?.model.isNullOrBlank()) preset.models.firstOrNull().orEmpty() else current!!.model,
                    note = preset.note
                )
            )
        }
    }

    fun setName(v: String) = update { it.copy(name = v) }
    fun setBase(v: String) = update { it.copy(baseUrl = v) }
    fun setKey(v: String) = update { it.copy(apiKey = v) }
    fun setModel(v: String) = update { it.copy(model = v) }
    fun setNote(v: String) = update { it.copy(note = v) }
    fun setTemperature(v: Double) = update { it.copy(temperature = v) }
    fun setMaxTokens(v: Int) = update { it.copy(maxTokens = v) }

    fun save() {
        val p = profile()
        if (p.name.isBlank()) {
            _ui.update { it.copy(message = "服务名称不能为空") }
            return
        }
        if (p.baseUrl.isBlank()) {
            _ui.update { it.copy(message = "请填写接口地址（可从预设一键填入）") }
            return
        }
        if (p.model.isBlank()) {
            _ui.update { it.copy(message = "请填写模型名") }
            return
        }
        viewModelScope.launch {
            library.upsertProvider(p)
            _ui.update { it.copy(message = "已保存「${p.name}」", isNew = false) }
        }
    }

    fun delete() {
        val id = profile().id
        if (id.isBlank()) return
        viewModelScope.launch {
            library.deleteProvider(id)
            _ui.update { it.copy(message = "已删除") }
        }
    }

    fun test() {
        val p = profile()
        if (p.baseUrl.isBlank() || p.model.isBlank()) {
            _ui.update { it.copy(message = "先填好地址与模型名再测试") }
            return
        }
        _ui.update { it.copy(testing = true, message = "") }
        viewModelScope.launch {
            val result = try {
                director.testProfile(p)
            } catch (t: Throwable) {
                t.message ?: "未知错误"
            }
            _ui.update {
                it.copy(testing = false, message = if (result == "正常" || result.contains("正常"))
                    "✅ 连接正常，模型回复：${result.take(80)}"
                else "❌ $result")
            }
        }
    }

    /** 从接口自动拉取可用模型列表（OpenAI 兼容 / Claude / Gemini）。 */
    fun refreshModels() {
        val p = profile()
        if (p.baseUrl.isBlank()) {
            _ui.update { it.copy(listMessage = "先填好接口地址再读取模型") }
            return
        }
        if (_ui.value.listingModels) return
        _ui.update { it.copy(listingModels = true, listMessage = "", availableModels = emptyList()) }
        viewModelScope.launch {
            var models: List<String>
            var err: String?
            try {
                models = chatClient.listModels(p)
                err = null
            } catch (t: Throwable) {
                models = emptyList()
                err = t.message ?: "未知错误"
            }
            _ui.update {
                if (err != null) {
                    it.copy(listingModels = false, listMessage = "读取失败：$err")
                } else {
                    it.copy(listingModels = false, availableModels = models, listMessage = "共 ${models.size} 个模型")
                }
            }
        }
    }
}

// ---------------- 底层基调 ----------------

data class BottomRuleEditorState(
    val rule: BottomRule? = null,
    val isNew: Boolean = true,
    val message: String = ""
)

class BottomRulesViewModel(container: WenYouApp.AppContainer) : ViewModel() {
    private val library: LocalLibrary = container.library
    private val _rules = MutableStateFlow(library.bottomRules.value)
    val rules: StateFlow<List<BottomRule>> = _rules.asStateFlow()

    init {
        viewModelScope.launch { library.bottomRules.collect { _rules.value = it } }
    }

    fun delete(id: String) = viewModelScope.launch { library.deleteBottomRule(id) }
}

class BottomRuleEditorViewModel(
    private val ruleId: String?,
    container: WenYouApp.AppContainer
) : ViewModel() {

    private val library: LocalLibrary = container.library

    private val _ui = MutableStateFlow(BottomRuleEditorState(isNew = ruleId == null))
    val ui: StateFlow<BottomRuleEditorState> = _ui.asStateFlow()

    init {
        if (ruleId != null) {
            val r = library.bottomRules.value.firstOrNull { it.id == ruleId }
            if (r == null) _ui.update { it.copy(message = "未找到该底层基调") }
            else _ui.update { it.copy(rule = r, isNew = false) }
        } else {
            _ui.update { it.copy(rule = BottomRule(id = UUID.randomUUID().toString(), name = "", content = "")) }
        }
    }

    private fun current() = _ui.value.rule ?: BottomRule(id = UUID.randomUUID().toString(), name = "", content = "")
    private fun update(t: (BottomRule) -> BottomRule) = _ui.update { it.copy(rule = t(it.rule ?: current())) }

    fun setName(v: String) = update { it.copy(name = v) }
    fun setContent(v: String) = update { it.copy(content = v) }

    fun save() {
        val r = _ui.value.rule ?: current()
        if (r.name.isBlank()) {
            _ui.update { it.copy(message = "底层基调需要一个名字") }
            return
        }
        if (r.content.isBlank()) {
            _ui.update { it.copy(message = "请填写底层基调的内容（不可动摇规则）") }
            return
        }
        val now = System.currentTimeMillis()
        val toSave = r.copy(
            id = r.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = if (r.createdAt == 0L) now else r.createdAt,
            updatedAt = now
        )
        viewModelScope.launch {
            try {
                library.upsertBottomRule(toSave)
                _ui.update { it.copy(message = "已保存「${toSave.name}」", isNew = false) }
            } catch (t: Throwable) {
                _ui.update { it.copy(message = "保存失败：${t.message}") }
            }
        }
    }
}

// ---------------- 设置 ----------------

data class SettingsUi(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultProviderId: String? = null,
    val showLgbt: Boolean = true,
    val adultContent: Boolean = true,
    val contentUnlocked: Boolean = false,
    val providers: List<ApiProfile> = emptyList(),
    val message: String = ""
)

class SettingsViewModel(container: WenYouApp.AppContainer) : ViewModel() {

    private val library: LocalLibrary = container.library
    private val store: SettingsStore = container.settings

    private val _providers = MutableStateFlow(library.providers.value)
    private val _message = MutableStateFlow("")

    val ui: StateFlow<SettingsUi> = kotlinx.coroutines.flow.combine(
        store.state, _providers, _message
    ) { prefs: io.wenyou.textquest.data.repo.UiPrefs,
        providers: List<ApiProfile>,
        message: String ->
        SettingsUi(prefs.themeMode, prefs.dynamicColor, prefs.defaultProviderId, prefs.showLgbt, prefs.adultContent, prefs.contentUnlocked, providers, message)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, SettingsUi(providers = library.providers.value))

    init {
        viewModelScope.launch {
            library.providers.collect { _providers.value = it }
        }
    }

    fun setMode(mode: ThemeMode) = store.setThemeMode(mode)
    fun setDynamic(on: Boolean) = store.setDynamicColor(on)
    fun setDefaultProvider(id: String?) = store.setDefaultProvider(id)
    fun setShowLgbt(on: Boolean) = store.setShowLgbt(on)
    fun setAdultContent(on: Boolean) = store.setAdultContent(on)

    /** 是否已解锁内容开关（α 版默认隐藏，连点版本号 10 次解锁）。 */
    fun unlockContentPrefs() = store.setContentUnlocked(true)

    /** 崩溃日志保存目录（SAF tree URI）。 */
    fun setCrashDir(uri: String?) { store.crashDirUri = uri }
    fun crashDir(): String? = store.crashDirUri

    fun setMessage(text: String) {
        _message.value = text
    }

    fun exportString(): String =
        AppJson.encodeToString(AppBundle.serializer(), library.bundle())

    fun importString(text: String) {
        try {
            val bundle = AppJson.decodeFromString(AppBundle.serializer(), text)
            viewModelScope.launch {
                val n = library.importBundle(bundle)
                _message.value = "导入成功：$n 条数据"
            }
        } catch (t: Throwable) {
            _message.value = "导入失败：${t.message}"
        }
    }
}

