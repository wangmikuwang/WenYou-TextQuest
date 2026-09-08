package io.wenyou.textquest.data.repo

import android.content.Context
import io.wenyou.textquest.data.model.ApiProfile
import io.wenyou.textquest.data.model.AppBundle
import io.wenyou.textquest.data.model.AppJson
import io.wenyou.textquest.data.model.CharacterData
import io.wenyou.textquest.data.model.SaveSlot
import io.wenyou.textquest.data.model.Story
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 轻量本地资料库：所有实体以 JSON 文件存于应用私有目录，
 * 无数据库迁移负担，可直接整体导出/导入。启动时加载进内存，
 * 每次变更写盘并更新 StateFlow。
 */
class LocalLibrary(context: Context) {

    private val dir: File = File(context.filesDir, "lib").apply { mkdirs() }
    private val providersFile = File(dir, "providers.json")
    private val charactersFile = File(dir, "characters.json")
    private val storiesFile = File(dir, "stories.json")
    private val savesFile = File(dir, "saves.json")

    private val _providers = MutableStateFlow(readList(providersFile, ApiProfile.serializer()))
    private val _characters = MutableStateFlow(readList(charactersFile, CharacterData.serializer()))
    private val _stories = MutableStateFlow(readList(storiesFile, Story.serializer()))
    private val _saves = MutableStateFlow(readList(savesFile, SaveSlot.serializer()))

    val providers: StateFlow<List<ApiProfile>> = _providers.asStateFlow()
    val characters: StateFlow<List<CharacterData>> = _characters.asStateFlow()
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()
    val saves: StateFlow<List<SaveSlot>> = _saves.asStateFlow()

    // ---------------- CRUD ----------------

    suspend fun upsertProvider(p: ApiProfile) = withContext(Dispatchers.IO) {
        _providers.value = replaceById(_providers.value, p.id, p).also { persistList(providersFile, it, ApiProfile.serializer()) }
    }

    suspend fun deleteProvider(id: String) = withContext(Dispatchers.IO) {
        _providers.value = _providers.value.filterNot { it.id == id }.also {
            persistList(providersFile, it, ApiProfile.serializer())
        }
    }

    suspend fun upsertCharacter(c: CharacterData) = withContext(Dispatchers.IO) {
        _characters.value = replaceById(_characters.value, c.id, c).also {
            persistList(charactersFile, it, CharacterData.serializer())
        }
    }

    suspend fun deleteCharacter(id: String) = withContext(Dispatchers.IO) {
        _characters.value = _characters.value.filterNot { it.id == id }.also {
            persistList(charactersFile, it, CharacterData.serializer())
        }
    }

    suspend fun upsertStory(s: Story) = withContext(Dispatchers.IO) {
        _stories.value = replaceById(_stories.value, s.id, s).also {
            persistList(storiesFile, it, Story.serializer())
        }
    }

    suspend fun deleteStory(id: String) = withContext(Dispatchers.IO) {
        _stories.value = _stories.value.filterNot { it.id == id }.also {
            persistList(storiesFile, it, Story.serializer())
        }
        _saves.value = _saves.value.filterNot { it.state.storyId == id }.also {
            persistList(savesFile, it, SaveSlot.serializer())
        }
    }

    suspend fun upsertSave(slot: SaveSlot) = withContext(Dispatchers.IO) {
        _saves.value = replaceById(_saves.value, slot.id, slot).also {
            persistList(savesFile, it, SaveSlot.serializer())
        }
    }

    suspend fun deleteSave(id: String) = withContext(Dispatchers.IO) {
        _saves.value = _saves.value.filterNot { it.id == id }.also {
            persistList(savesFile, it, SaveSlot.serializer())
        }
    }

    // ---------------- 批量/导入导出 ----------------

    fun bundle(): AppBundle = AppBundle(
        exportedAt = System.currentTimeMillis(),
        providers = _providers.value,
        characters = _characters.value,
        stories = _stories.value,
        saves = _saves.value
    )

    suspend fun importBundle(bundle: AppBundle): Int = withContext(Dispatchers.IO) {
        _providers.value = bundle.providers
        _characters.value = bundle.characters
        _stories.value = bundle.stories
        _saves.value = bundle.saves
        persistList(providersFile, bundle.providers, ApiProfile.serializer())
        persistList(charactersFile, bundle.characters, CharacterData.serializer())
        persistList(storiesFile, bundle.stories, Story.serializer())
        persistList(savesFile, bundle.saves, SaveSlot.serializer())
        bundle.providers.size + bundle.characters.size + bundle.stories.size + bundle.saves.size
    }

    // ---------------- 内部工具 ----------------

    private fun <T> replaceById(list: List<T>, id: String, item: T): List<T> {
        val out = list.toMutableList()
        val idx = out.indexOfFirst {
            when (it) {
                is ApiProfile -> it.id == id
                is CharacterData -> it.id == id
                is Story -> it.id == id
                is SaveSlot -> it.id == id
                else -> false
            }
        }
        if (idx >= 0) out[idx] = item else out.add(item)
        return out
    }

    private fun <T> persistList(file: File, list: List<T>, serializer: kotlinx.serialization.KSerializer<T>) {
        try {
            val text = AppJson.encodeToString(ListSerializer(serializer), list)
            file.writeText(text)
        } catch (t: Throwable) {
            // 写盘/序列化失败：记录到 crash.log，内存数据仍已更新，避免因此崩溃
            try { File(file.parentFile, "crash.log").writeText("persistList(${file.name}) failed: $t\n") } catch (_: Throwable) {}
        }
    }

    private fun <T> readList(file: File, serializer: kotlinx.serialization.KSerializer<T>): List<T> {
        if (!file.exists()) return emptyList()
        return try {
            AppJson.decodeFromString(ListSerializer(serializer), file.readText())
        } catch (t: Throwable) {
            // 文件损坏时保留现场（.corrupt），从空列表继续，避免应用崩溃
            try { file.copyTo(File(file.parentFile, file.name + ".corrupt-" + System.currentTimeMillis()), true) } catch (_: Throwable) {}
            emptyList()
        }
    }
}

/** 解析 Json 的兜底实例（复用 AppJson 的宽松配置）。 */
val LenientJson: Json get() = AppJson
