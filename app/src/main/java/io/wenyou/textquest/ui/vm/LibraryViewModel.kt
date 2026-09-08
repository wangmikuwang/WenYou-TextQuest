package io.wenyou.textquest.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.model.SaveSlot
import io.wenyou.textquest.data.model.Story
import io.wenyou.textquest.data.repo.LocalLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 主页 / 故事库共用的列表状态。 */
data class HomeCard(
    val slot: SaveSlot,
    val story: Story?,
    val stepText: String
)

class LibraryViewModel(container: WenYouApp.AppContainer) : ViewModel() {

    private val library: LocalLibrary = container.library

    private val _saves = MutableStateFlow(library.saves.value)
    private val _stories = MutableStateFlow(library.stories.value)
    private val _characters = MutableStateFlow(library.characters.value)
    private val _providers = MutableStateFlow(library.providers.value)

    init {
        viewModelScope.launch {
            library.saves.collect { _saves.value = it }
        }
        viewModelScope.launch {
            library.stories.collect { _stories.value = it }
        }
        viewModelScope.launch {
            library.characters.collect { _characters.value = it }
        }
        viewModelScope.launch {
            library.providers.collect { _providers.value = it }
        }
    }

    val saves: StateFlow<List<SaveSlot>> = _saves.asStateFlow()
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()
    val characters: StateFlow<List<io.wenyou.textquest.data.model.CharacterData>> = _characters.asStateFlow()
    val providers: StateFlow<List<io.wenyou.textquest.data.model.ApiProfile>> = _providers.asStateFlow()

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

    fun deleteSave(id: String) = viewModelScope.launch { library.deleteSave(id) }

    fun deleteStory(id: String) = viewModelScope.launch { library.deleteStory(id) }

    fun deleteCharacter(id: String) = viewModelScope.launch { library.deleteCharacter(id) }

    fun deleteProvider(id: String) = viewModelScope.launch { library.deleteProvider(id) }

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
