package io.wenyou.textquest

import android.app.Application
import android.content.Context
import io.wenyou.textquest.data.ai.AiDirector
import io.wenyou.textquest.data.llm.ChatClient
import io.wenyou.textquest.data.model.AppBundle
import io.wenyou.textquest.data.model.AppJson
import io.wenyou.textquest.data.model.CharacterData
import io.wenyou.textquest.data.model.SexualOrientation
import io.wenyou.textquest.data.repo.LocalLibrary
import io.wenyou.textquest.data.repo.SettingsStore
import io.wenyou.textquest.data.sample.SampleData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class WenYouApp : Application() {

    /** 进程级手动依赖注入容器（避免引入 Hilt，保持工程轻量）。 */
    class AppContainer(context: Context) {
        val library = LocalLibrary(context)
        val settings = SettingsStore(context)
        val chatClient = ChatClient()
        val director = AiDirector(chatClient)
    }

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        installCrashLogger()
        appScope.launch {
            when {
                // 文游α：内置示例 + 非LGBT常备；LGBT 预设视「内容开关」而并入；成人向预设始终并入
                BuildConfig.BUILTIN_CONTENT -> {
                    seedSamplesIfNeeded()
                    applyPresetAssets(listOf(
                        "presets/wenyou-bare-presets.json",
                        "presets/wenyou-bare2-presets.json"
                    ), markLgbt = false)
                    if (container.settings.state.value.showLgbt) {
                        applyPresetAssets(listOf(
                            "presets/wenyou-romance-presets.json",
                            "presets/wenyou-extended-presets.json"
                        ), markLgbt = true)
                    }
                    applyPresetAssets(listOf("presets/wenyou-adult-presets.json"), markAdult = true)
                }
                // 文游β：仅内置“非 LGBT”剧情与角色
                BuildConfig.BARE_CONTENT -> {
                    applyPresetAssets(listOf(
                        "presets/wenyou-bare-presets.json",
                        "presets/wenyou-bare2-presets.json"
                    ), markLgbt = false)
                }
            }
            enrichBuiltinInitials()
        }
    }

    /**
     * 一次性为「已存在且未配置」的内置角色补齐 [CharacterData.initial] 与 [CharacterData.orientation]。
     * 非破坏性：只填充初始状态为空、性取向未标注的角色，不覆盖用户自定，
     * 也不会把用户删除的内置内容重新写回（仅针对当前仍存在的 id）。
     */
    private suspend fun enrichBuiltinInitials() {
        val doneInitial = container.settings.presetEnrichDone
        val doneOrient = container.settings.presetOrientDone
        if (doneInitial && doneOrient) return
        try {
            val names = when {
                BuildConfig.BUILTIN_CONTENT -> listOf(
                    "presets/wenyou-bare-presets.json",
                    "presets/wenyou-bare2-presets.json",
                    "presets/wenyou-romance-presets.json",
                    "presets/wenyou-extended-presets.json",
                    "presets/wenyou-adult-presets.json"
                )
                BuildConfig.BARE_CONTENT -> listOf(
                    "presets/wenyou-bare-presets.json",
                    "presets/wenyou-bare2-presets.json"
                )
                else -> emptyList()
            }
            val existing = container.library.characters.value.associateBy { it.id }
            var changedInitial = false
            var changedOrient = false
            val updates = mutableListOf<CharacterData>()
            for (name in names) {
                val text = assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val bundle = AppJson.decodeFromString(AppBundle.serializer(), text)
                for (c in bundle.characters) {
                    val cur = existing[c.id] ?: continue
                    var next = cur
                    if (!doneInitial && cur.initial.metrics.isEmpty() && c.initial.metrics.isNotEmpty()) {
                        next = next.copy(initial = c.initial)
                        changedInitial = true
                    }
                    if (!doneOrient && cur.orientation == SexualOrientation.UNKNOWN && c.orientation != SexualOrientation.UNKNOWN) {
                        next = next.copy(orientation = c.orientation)
                        changedOrient = true
                    }
                    if (next !== cur) updates += next
                }
            }
            for (cc in updates) container.library.upsertCharacter(cc)
            if (changedInitial) container.settings.presetEnrichDone = true
            if (changedOrient) container.settings.presetOrientDone = true
        } catch (_: Throwable) {
            // 补齐失败不阻塞主流程，下次启动重试
        }
    }

    /**
     * 崩溃日志兜底：任何未捕获异常都会写入
     *  - 内部存储 files/crash.log
     *  - 外部应用目录 getExternalFilesDir()/crash.log
     *  - 若用户已在设置里指定「系统文档目录」，也写入该 Documents 目录
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                PrintWriter(sw).use { throwable.printStackTrace(it) }
                val text = buildString {
                    append("timeMillis=").append(System.currentTimeMillis()).append('\n')
                    append("version=").append(BuildConfig.VERSION_NAME).append(" (build ")
                    append(BuildConfig.VERSION_CODE).append(")\n")
                    append("thread=").append(thread?.name).append('\n')
                    append(sw.toString())
                }
                CrashLog.write(this, text, container.settings.crashDirUri)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable) ?: throw throwable
        }
    }

    /** 首次启动植入内置示例剧情与角色（雨夜咖啡馆 / 忆城）。 */
    private suspend fun seedSamplesIfNeeded() {
        if (container.settings.seeded) return
        try {
            val (chars, stories) = SampleData.all()
            for (c in chars) container.library.upsertCharacter(c)
            for (s in stories) container.library.upsertStory(s)
            container.settings.seeded = true
        } catch (_: Throwable) {
            // 示例植入失败不阻塞主流程，下次启动可重试（seeded 仍未置位）
        }
    }

    /**
     * 把 assets/presets/ 下的题材预设包（BL/伪百合/男娘/第四爱/娱乐圈ABO 等）
     * 自动并入资料库：按 id 去重、只补不覆盖。升级安装也能补到新版本新增的预设。
     */
    /** 逐个资源去重合并（按 id 只补不覆盖）。markLgbt/markAdult 用于打标签。 */
    private suspend fun applyPresetAssets(presetFiles: List<String>, markLgbt: Boolean = false, markAdult: Boolean = false) {
        // 每个资源文件只成功合并一次并记录状态；否则每次启动都会全量重扫，
        // 既重复解析，也会把用户已删除的内置内容重新写回
        val already = container.settings.appliedPresetFiles()
        for (name in presetFiles) {
            if (name in already) continue
            try {
                applyPresetAsset(name, markLgbt, markAdult)
                container.settings.markPresetFileApplied(name)
            } catch (_: Throwable) {
                // 单个资源失败不影响其它资源与主流程，下次启动重试
            }
        }
    }

    private suspend fun applyPresetAsset(name: String, markLgbt: Boolean, markAdult: Boolean) {
        val text = assets.open(name)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val bundle = AppJson.decodeFromString(AppBundle.serializer(), text)
        val charIds = container.library.characters.value.mapTo(mutableSetOf()) { it.id }
        for (c in bundle.characters) {
            if (charIds.add(c.id)) {
                val cc = if (markLgbt || markAdult) c.copy(lgbt = c.lgbt || markLgbt, adult = c.adult || markAdult) else c
                container.library.upsertCharacter(cc)
            }
        }
        val storyIds = container.library.stories.value.mapTo(mutableSetOf()) { it.id }
        for (s in bundle.stories) {
            if (storyIds.add(s.id)) {
                val ss = if (markLgbt || markAdult) s.copy(lgbt = s.lgbt || markLgbt, adult = s.adult || markAdult) else s
                container.library.upsertStory(ss)
            }
        }
    }
}
