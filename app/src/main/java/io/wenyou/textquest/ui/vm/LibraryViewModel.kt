package io.wenyou.textquest.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.model.AppBundle
import io.wenyou.textquest.data.model.SaveSlot
import io.wenyou.textquest.data.model.SexualOrientation
import io.wenyou.textquest.data.model.Story
import io.wenyou.textquest.data.model.StoryMode
import io.wenyou.textquest.data.model.storyContentClass
import io.wenyou.textquest.data.repo.LocalLibrary
import io.wenyou.textquest.data.repo.SettingsStore
import io.wenyou.textquest.data.repo.ShareCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 主页 / 故事库共用的列表状态。 */
data class HomeCard(
    val slot: SaveSlot,
    val story: Story?,
    val stepText: String
)

/** 剧情库按「运行模式」的分类。 */
enum class StoryModeFilter(val label: String) {
    ALL("全部"),
    SCRIPT("分支剧本"),
    AI_DIRECTOR("AI 导演")
}

/** 剧情库按「内容」的分类。 */
enum class StoryContentFilter(val label: String) {
    ALL("全部"),
    ALL_AGE("全年龄"),
    LGBT("LGBT"),
    ADULT("18+")
}

/** 剧情库 / 角色库的分类过滤状态。 */
data class LibraryUi(
    val modeFilter: StoryModeFilter = StoryModeFilter.ALL,
    val contentFilter: StoryContentFilter = StoryContentFilter.ALL,
    val orientationFilter: SexualOrientation? = null
)

class LibraryViewModel(container: WenYouApp.AppContainer) : ViewModel() {

    private val library: LocalLibrary = container.library
    private val settings: SettingsStore = container.settings

    private val _saves = MutableStateFlow(library.saves.value)
    private val _stories = MutableStateFlow(library.stories.value)
    private val _characters = MutableStateFlow(library.characters.value)
    private val _providers = MutableStateFlow(library.providers.value)
    private val _filters = MutableStateFlow(LibraryUi())

    /** 内容开关：false 时隐藏 LGBT 预设内容。 */
    private val showLgbt = settings.state.map { it.showLgbt }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.state.value.showLgbt)

    /** 成人内容开关：false 时隐藏 adult 预设内容。 */
    private val adultContent = settings.state.map { it.adultContent }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.state.value.adultContent)

    init {
        viewModelScope.launch { library.saves.collect { _saves.value = it } }
        viewModelScope.launch { library.stories.collect { _stories.value = it } }
        viewModelScope.launch { library.characters.collect { _characters.value = it } }
        viewModelScope.launch { library.providers.collect { _providers.value = it } }
    }

    val filters: StateFlow<LibraryUi> = _filters.asStateFlow()

    val saves: StateFlow<List<SaveSlot>> = _saves.asStateFlow()

    /** 按「内容开关/成人开关」+「模式分类」+「内容分类」过滤后的剧情。 */
    val stories: StateFlow<List<Story>> = combine(_stories, showLgbt, adultContent, _filters) {
            list: List<Story>, lgbt: Boolean, adult: Boolean, f: LibraryUi ->
            list.filter { (lgbt || !it.lgbt) && (adult || !it.adult) }
                .let { seq ->
                    when (f.modeFilter) {
                        StoryModeFilter.ALL -> seq
                        StoryModeFilter.SCRIPT -> seq.filter { it.mode == StoryMode.SCRIPT }
                        StoryModeFilter.AI_DIRECTOR -> seq.filter { it.mode == StoryMode.AI_DIRECTOR }
                    }
                }
                .let { seq ->
                    when (f.contentFilter) {
                        StoryContentFilter.ALL -> seq
                        else -> seq.filter { storyContentClass(it).label == f.contentFilter.label }
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, _stories.value)

    /** 按「内容开关/成人开关」+「性取向」过滤后的角色。 */
    val characters: StateFlow<List<io.wenyou.textquest.data.model.CharacterData>> =
        combine(_characters, showLgbt, adultContent, _filters) {
                list: List<io.wenyou.textquest.data.model.CharacterData>,
                lgbt: Boolean, adult: Boolean, f: LibraryUi ->
                list.filter { (lgbt || !it.lgbt) && (adult || !it.adult) }
                    .let { seq ->
                        val o = f.orientationFilter
                        if (o == null) seq else seq.filter { it.orientation == o }
                    }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, _characters.value)

    val providers: StateFlow<List<io.wenyou.textquest.data.model.ApiProfile>> = _providers.asStateFlow()

    /** 未过滤的原始总数（用于区分“库为空”与“该分类无内容”）。 */
    val totalStories: StateFlow<Int> = _stories.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _stories.value.size)
    val totalCharacters: StateFlow<Int> = _characters.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _characters.value.size)

    val homeCards: StateFlow<List<HomeCard>> = combine(_saves, _stories) {
            saves: List<SaveSlot>, stories: List<Story> ->
            saves.sortedByDescending { it.updatedAt }.map { slot ->
                val story = stories.firstOrNull { it.id == slot.state.storyId }
                HomeCard(
                    slot = slot,
                    story = story,
                    stepText = "${slot.state.history.size} 步 · ${formatWhen(slot.updatedAt)}"
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 某部剧情的全部存档（用于剧情详情页「读取存档」）。 */
    fun savesForStory(storyId: String): List<SaveSlot> =
        _saves.value.filter { it.state.storyId == storyId }.sortedByDescending { it.updatedAt }

    fun setModeFilter(f: StoryModeFilter) = _filters.update { it.copy(modeFilter = f) }
    fun setContentFilter(f: StoryContentFilter) = _filters.update { it.copy(contentFilter = f) }
    fun setOrientationFilter(o: SexualOrientation?) = _filters.update { it.copy(orientationFilter = o) }

    fun deleteSave(id: String) = viewModelScope.launch { library.deleteSave(id) }
    fun deleteStory(id: String) = viewModelScope.launch { library.deleteStory(id) }
    fun deleteCharacter(id: String) = viewModelScope.launch { library.deleteCharacter(id) }
    fun deleteProvider(id: String) = viewModelScope.launch { library.deleteProvider(id) }

    /** 生成一部剧情的分享码（含其引用的角色；剧情不存在返回空串）。
     *
     *  注意：仅打包「当前仍存在」且被剧情引用的角色。若某角色 id 已被删除但仍被
     *  剧情引用，则跳过该 id（而非打包一个不存在角色），避免对方导入后出现空角色。 */
    fun shareCodeFor(storyId: String): String {
        val story = _stories.value.firstOrNull { it.id == storyId } ?: return ""
        val chars = _characters.value.filter { it.id in story.characterIds }
        return ShareCode.encode(AppBundle(characters = chars, stories = listOf(story)))
    }

    /** 生成单个角色的分享码（角色不存在返回空串）。 */
    fun shareCodeForCharacter(characterId: String): String {
        val c = _characters.value.firstOrNull { it.id == characterId } ?: return ""
        return ShareCode.encode(AppBundle(characters = listOf(c)))
    }

    /** 从分享码导入：只补不覆盖，结果通过 onResult 回调（主线程执行）。 */
    fun importShareCode(code: String, onResult: (String) -> Unit) {
        val bundle = ShareCode.decode(code)
        if (bundle == null) {
            onResult("分享码无效，请检查是否完整")
            return
        }
        if (bundle.stories.isEmpty() && bundle.characters.isEmpty()) {
            onResult("分享码中没有可导入的内容")
            return
        }
        viewModelScope.launch {
            try {
                val n = library.importShared(bundle)
                onResult("导入成功：新增 $n 条内容")
            } catch (t: Throwable) {
                onResult("导入失败：${t.message}")
            }
        }
    }

    fun setShowLgbt(on: Boolean) = settings.setShowLgbt(on)
    fun showLgbt(): Boolean = settings.state.value.showLgbt

    fun storyCount(): Int = _stories.value.size

    companion object {
        fun formatWhen(ts: Long): String {
            val diff = System.currentTimeMillis() - ts
            val minutes = diff / 60000L
            return when {
                minutes < 1 -> "刚刚"
                minutes < 60 -> "$minutes 分钟前"
                minutes < 60 * 24 -> "${minutes / 60} 小时前"
                else -> "${minutes / (60 * 24)} 天前"
            }
        }
    }
}
