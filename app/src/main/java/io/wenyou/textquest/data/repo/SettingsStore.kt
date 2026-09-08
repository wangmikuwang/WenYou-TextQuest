package io.wenyou.textquest.data.repo

import android.content.Context
import android.content.SharedPreferences
import io.wenyou.textquest.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 外观偏好（由根主题与设置页共享观察）。 */
data class UiPrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultProviderId: String? = null
)

/** 轻量应用设置（SharedPreferences），变更同步发布到 [state] 供主题实时响应。 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wenyou_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UiPrefs> = _state.asStateFlow()

    private fun load(): UiPrefs = UiPrefs(
        themeMode = ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        defaultProviderId = prefs.getString(KEY_PROVIDER, null)
    )

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

    // ---- 兼容旧读取点 ----
    val defaultProviderId: String?
        get() = prefs.getString(KEY_PROVIDER, null)

    var seeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEDED, value).apply()

    /** 内置「题材预设包」是否已合并进资料库。 */
    var presetsApplied: Boolean
        get() = prefs.getBoolean(KEY_PRESETS, false)
        set(value) = prefs.edit().putBoolean(KEY_PRESETS, value).apply()

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
        const val KEY_PRESETS = "presets_applied_v1"
        const val KEY_CRASH_DIR = "crash_dir_uri"
        const val KEY_COMPACT = "compact_cards"
    }
}
