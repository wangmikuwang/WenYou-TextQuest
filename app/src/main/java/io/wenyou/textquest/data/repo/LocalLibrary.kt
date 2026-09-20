package io.wenyou.textquest.data.repo

import android.content.Context
import io.wenyou.textquest.data.model.ApiProfile
import io.wenyou.textquest.data.model.AppBundle
import io.wenyou.textquest.data.model.AppJson
import io.wenyou.textquest.data.model.BottomRule
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
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 轻量本地资料库：所有实体以 JSON 文件存于应用私有目录，
 * 无数据库迁移负担，可直接整体导出/导入。启动时加载进内存，
 * 每次变更写盘并更新 StateFlow。
 */
class LocalLibrary internal constructor(private val dir: File) {

    constructor(context: Context) : this(File(context.filesDir, "lib"))

    init { dir.mkdirs() }
    // ponytail: 资料库写入共用一把锁；数据量大到阻塞编辑时再改用数据库事务。
    private val lock = Any()
    private val _writeError = MutableStateFlow<String?>(null)
    val writeError: StateFlow<String?> = _writeError.asStateFlow()
    fun clearWriteError() { _writeError.value = null }

    private suspend fun <T> write(block: () -> T): T = withContext(Dispatchers.IO) {
        synchronized(lock) { block() }
    }
    private val providersFile = File(dir, "providers.json")
    private val charactersFile = File(dir, "characters.json")
    private val storiesFile = File(dir, "stories.json")
    private val savesFile = File(dir, "saves.json")
    private val bottomRulesFile = File(dir, "bottom_rules.json")

    private val _providers = MutableStateFlow(readList(providersFile, ApiProfile.serializer()))
    private val _characters = MutableStateFlow(readList(charactersFile, CharacterData.serializer()))
    private val _stories = MutableStateFlow(readList(storiesFile, Story.serializer()))
    private val _saves = MutableStateFlow(readList(savesFile, SaveSlot.serializer()))
    private val _bottomRules = MutableStateFlow(readList(bottomRulesFile, BottomRule.serializer()))

    val providers: StateFlow<List<ApiProfile>> = _providers.asStateFlow()
    val characters: StateFlow<List<CharacterData>> = _characters.asStateFlow()
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()
    val saves: StateFlow<List<SaveSlot>> = _saves.asStateFlow()
    val bottomRules: StateFlow<List<BottomRule>> = _bottomRules.asStateFlow()

    // ---------------- CRUD ----------------

    suspend fun upsertProvider(p: ApiProfile) = write {
        _providers.value = replaceById(_providers.value, p.id, p).also { persistList(providersFile, it, ApiProfile.serializer()) }
    }

    suspend fun deleteProvider(id: String) = write {
        _providers.value = _providers.value.filterNot { it.id == id }.also {
            persistList(providersFile, it, ApiProfile.serializer())
        }
    }

    suspend fun upsertCharacter(c: CharacterData) = write {
        _characters.value = replaceById(_characters.value, c.id, c).also {
            persistList(charactersFile, it, CharacterData.serializer())
        }
    }

    suspend fun deleteCharacter(id: String) = write {
        _characters.value = _characters.value.filterNot { it.id == id }.also {
            persistList(charactersFile, it, CharacterData.serializer())
        }
    }

    suspend fun upsertStory(s: Story) = write {
        _stories.value = replaceById(_stories.value, s.id, s).also {
            persistList(storiesFile, it, Story.serializer())
        }
    }

    suspend fun deleteStory(id: String) = write {
        _stories.value = _stories.value.filterNot { it.id == id }.also {
            persistList(storiesFile, it, Story.serializer())
        }
        _saves.value = _saves.value.filterNot { it.state.storyId == id }.also {
            persistList(savesFile, it, SaveSlot.serializer())
        }
    }

    suspend fun upsertSave(slot: SaveSlot) = write {
        _saves.value = replaceById(_saves.value, slot.id, slot).also {
            persistList(savesFile, it, SaveSlot.serializer())
        }
    }

    suspend fun deleteSave(id: String) = write {
        _saves.value = _saves.value.filterNot { it.id == id }.also {
            persistList(savesFile, it, SaveSlot.serializer())
        }
    }

    suspend fun upsertBottomRule(r: BottomRule) = write {
        _bottomRules.value = replaceById(_bottomRules.value, r.id, r).also {
            persistList(bottomRulesFile, it, BottomRule.serializer())
        }
    }

    suspend fun deleteBottomRule(id: String) = write {
        _bottomRules.value = _bottomRules.value.filterNot { it.id == id }.also {
            persistList(bottomRulesFile, it, BottomRule.serializer())
        }
        // 同时从所有角色上摘除对该规则的引用，避免留下悬空 id
        if (_characters.value.any { r -> id in r.bottomRuleIds }) {
            _characters.value = _characters.value.map { c ->
                if (id in c.bottomRuleIds) c.copy(bottomRuleIds = c.bottomRuleIds - id) else c
            }.also { persistList(charactersFile, it, CharacterData.serializer()) }
        }
    }

    // ---------------- 批量/导入导出 ----------------

    fun bundle(): AppBundle = synchronized(lock) { AppBundle(
        exportedAt = System.currentTimeMillis(),
        providers = _providers.value,
        characters = _characters.value,
        stories = _stories.value,
        saves = _saves.value,
        bottomRules = _bottomRules.value
    ) }

    suspend fun importBundle(bundle: AppBundle): Int = write {
        persistList(providersFile, bundle.providers, ApiProfile.serializer())
        _providers.value = bundle.providers
        persistList(charactersFile, bundle.characters, CharacterData.serializer())
        _characters.value = bundle.characters
        persistList(storiesFile, bundle.stories, Story.serializer())
        _stories.value = bundle.stories
        persistList(savesFile, bundle.saves, SaveSlot.serializer())
        _saves.value = bundle.saves
        persistList(bottomRulesFile, bundle.bottomRules, BottomRule.serializer())
        _bottomRules.value = bundle.bottomRules
        bundle.providers.size + bundle.characters.size + bundle.stories.size + bundle.saves.size + bundle.bottomRules.size
    }

    data class SharedImportResult(val added: Int, val existing: Int)

    /** 分享码导入：仅按 id 补入缺失的剧情与角色，不覆盖同名、不触碰用户已有数据。 */
    suspend fun importShared(bundle: AppBundle): SharedImportResult = write {
        val charIds = _characters.value.mapTo(mutableSetOf()) { it.id }
        val newChars = bundle.characters.filter { charIds.add(it.id) }
        val storyIds = _stories.value.mapTo(mutableSetOf()) { it.id }
        val newStories = bundle.stories.filter { storyIds.add(it.id) }
        val ruleIds = _bottomRules.value.mapTo(mutableSetOf()) { it.id }
        val newRules = bundle.bottomRules.filter { ruleIds.add(it.id) }
        if (newChars.isNotEmpty() || newStories.isNotEmpty() || newRules.isNotEmpty()) {
            val chars = _characters.value + newChars
            val stories = _stories.value + newStories
            val rules = _bottomRules.value + newRules
            persistList(charactersFile, chars, CharacterData.serializer())
            _characters.value = chars
            persistList(storiesFile, stories, Story.serializer())
            _stories.value = stories
            persistList(bottomRulesFile, rules, BottomRule.serializer())
            _bottomRules.value = rules
        }
        val added = newChars.size + newStories.size + newRules.size
        SharedImportResult(added, bundle.characters.size + bundle.stories.size + bundle.bottomRules.size - added)
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
                is BottomRule -> it.id == id
                else -> false
            }
        }
        if (idx >= 0) out[idx] = item else out.add(item)
        return out
    }

    private fun <T> persistList(file: File, list: List<T>, serializer: kotlinx.serialization.KSerializer<T>) {
        var pending: File? = null
        try {
            val text = AppJson.encodeToString(ListSerializer(serializer), list)
            val temp = File.createTempFile(file.name, ".pending", dir)
            pending = temp
            FileOutputStream(temp).use {
                it.write(text.toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            val message = "无法保存 ${file.name}：${e.message}"
            _writeError.value = message
            throw IOException(message, e)
        } finally {
            pending?.delete()
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
