package io.wenyou.textquest.data.repo

import android.content.Context
import android.content.SharedPreferences
import io.wenyou.textquest.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 外观/内容偏好（由根主题与设置页共享观察）。 */
data class UiPrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultProviderId: String? = null,
    val showLgbt: Boolean = true,
    val adultContent: Boolean = true
)

/** 轻量应用设置（SharedPreferences），变更同步发布到 [state] 供主题实时响应。 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wenyou_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UiPrefs> = _state.asStateFlow()

    private fun load(): UiPrefs = UiPrefs(
        themeMode = themeOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        defaultProviderId = prefs.getString(KEY_PROVIDER, null),
        showLgbt = prefs.getBoolean(KEY_SHOW_LGBT, true),
        adultContent = prefs.getBoolean(KEY_ADULT, true)
    )

    /** 旧版本可能写入过未知枚举值（主题改名/清理残留），损坏时回退跟随系统。 */
    private fun themeOf(raw: String?): ThemeMode = try {
        ThemeMode.valueOf(raw ?: "")
    } catch (_: Throwable) {
        ThemeMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setDynamicColor(on: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, on).apply()
        _state.value = _state.value.copy(dynamicColor = on)
    }

    fun setDefaultProvider(id: String?) {
        prefs.edit().putString(KEY_PROVIDER, id).apply()
        _state.value = _state.value.copy(defaultProviderId = id)
    }

    fun setShowLgbt(on: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_LGBT, on).apply()
        _state.value = _state.value.copy(showLgbt = on)
    }

    fun setAdultContent(on: Boolean) {
        prefs.edit().putBoolean(KEY_ADULT, on).apply()
        _state.value = _state.value.copy(adultContent = on)
    }

    // ---- 兼容旧读取点 ----
    val defaultProviderId: String?
        get() = prefs.getString(KEY_PROVIDER, null)

    var seeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEDED, value).apply()

    /** 已成功合并过一次的预设资源文件名。用于避免每次启动重复全量导入（把用户删除的内容“复活”）。 */
    fun appliedPresetFiles(): Set<String> =
        prefs.getStringSet(KEY_PRESET_FILES, emptySet())?.toSet() ?: emptySet()

    fun markPresetFileApplied(name: String) {
        val next = appliedPresetFiles() + name
        prefs.edit().putStringSet(KEY_PRESET_FILES, next).apply()
    }

    /** 崩溃日志保存目录（SAF 授权的 Documents tree URI；空 = 未选择）。 */
    var crashDirUri: String?
        get() = prefs.getString(KEY_CRASH_DIR, null)
        set(value) = prefs.edit().putString(KEY_CRASH_DIR, value).apply()

    var compactCards: Boolean
        get() = prefs.getBoolean(KEY_COMPACT, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPACT, value).apply()

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_PROVIDER = "default_provider"
        const val KEY_SEEDED = "seeded_v1"
        const val KEY_PRESET_FILES = "preset_files_applied_v2"
        const val KEY_CRASH_DIR = "crash_dir_uri"
        const val KEY_SHOW_LGBT = "show_lgbt"
        const val KEY_ADULT = "adult_content"
        const val KEY_COMPACT = "compact_cards"
    }
}