package io.wenyou.textquest

import android.app.Application
import android.content.Context
import io.wenyou.textquest.data.ai.AiDirector
import io.wenyou.textquest.data.llm.ChatClient
import io.wenyou.textquest.data.model.AppBundle
import io.wenyou.textquest.data.model.AppJson
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
            seedSamplesIfNeeded()
            applyPresetAssets()
        }
    }

    /**
     * 崩溃日志兜底：任何未捕获异常都被写进「内部存储 crash.log 与外部目录 crash.log」，
     * 方便在真机上定位闪退原因（尤其 Android 15+/17 行为差异）。
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
                val dirs = listOfNotNull(filesDir, getExternalFilesDir(null)).distinct()
                for (dir in dirs) {
                    try { File(dir, "crash.log").writeText(text) } catch (_: Throwable) {}
                }
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
    private suspend fun applyPresetAssets() {
        val presetFiles = listOf(
            "presets/romance-presets.json",
            "presets/wenyou-extended-presets.json"
        )
        for (name in presetFiles) {
            try {
                applyPresetAsset(name)
            } catch (_: Throwable) {
                // 单个资源失败不影响其它资源与主流程，下次启动重试
            }
        }
    }

    private suspend fun applyPresetAsset(name: String) {
        val text = assets.open(name)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val bundle = AppJson.decodeFromString(AppBundle.serializer(), text)
        val charIds = container.library.characters.value.mapTo(mutableSetOf()) { it.id }
        for (c in bundle.characters) {
            if (charIds.add(c.id)) container.library.upsertCharacter(c)
        }
        val storyIds = container.library.stories.value.mapTo(mutableSetOf()) { it.id }
        for (s in bundle.stories) {
            if (storyIds.add(s.id)) container.library.upsertStory(s)
        }
    }
}
