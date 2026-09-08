package io.wenyou.textquest.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.BuildConfig
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.ui.HubScaffold
import io.wenyou.textquest.ui.common.AppDropdown
import io.wenyou.textquest.ui.common.SectionHeader
import io.wenyou.textquest.ui.common.TonalCard
import io.wenyou.textquest.ui.theme.ThemeMode
import io.wenyou.textquest.ui.vm.SettingsViewModel
import io.wenyou.textquest.ui.vm.Vms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: WenYouApp.AppContainer, nav: NavHostController) {
    val vm: SettingsViewModel = viewModel(factory = Vms.factory { SettingsViewModel(it) })
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(vm.exportString().toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
            }
            vm.setMessage(if (ok) "已导出全部数据（剧情/角色/服务/存档）" else "导出失败")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (text == null) vm.setMessage("读取文件失败")
            else vm.importString(text)
        }
    }

    HubScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        nav = nav
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("文游 · 文字游戏", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Material You 动态配色 · 本地数据优先", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (ui.message.isNotBlank()) {
                item {
                    TonalCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(ui.message, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            item { SectionHeader("外观（Material You）") }
            item {
                TonalCard {
                    Text("主题模式", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    AppDropdown(
                        label = "主题模式",
                        options = listOf(
                            "跟随系统" to ThemeMode.SYSTEM,
                            "浅色" to ThemeMode.LIGHT,
                            "深色" to ThemeMode.DARK
                        ),
                        selected = ui.mode,
                        onSelect = { vm.setMode(it) }
                    )
                }
            }
            item {
                TonalCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("动态取色（壁纸配色）", style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    "从壁纸生成整套色调角色（Android 12+）"
                                else "此设备需要 Android 12+ 才能使用动态取色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = ui.dynamicColor,
                            onCheckedChange = { vm.setDynamic(it) },
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    }
                }
            }

            item { SectionHeader("AI 默认服务") }
            item {
                TonalCard {
                    Text("对局默认调用（AI 导演/AI 场景）", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    AppDropdown(
                        label = "默认服务",
                        options = listOf("（使用第一个可用）" to "") +
                            ui.providers.map { it.name to it.id },
                        selected = ui.defaultProviderId ?: "",
                        onSelect = { id -> vm.setDefaultProvider(id.ifBlank { null }) }
                    )
                }
            }

            item { SectionHeader("数据备份") }
            item {
                TonalCard {
                    Text("所有剧情、角色、AI 服务与存档都以 JSON 保存在本机，可整体导出/导入迁移。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            exportLauncher.launch("wenyou-backup-${System.currentTimeMillis()}.json")
                        }) { Text("导出备份") }
                        OutlinedButton(onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }) { Text("导入备份") }
                    }
                }
            }

            item {
                TonalCard {
                    Text("设计遵循", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("界面遵循 Android Material You 设计规范：动态色调角色与容器色、圆角形态、动效与自适应排版。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("v${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）· 本地优先：API Key 不离开本机\n每次改动请运行 gradlew bumpVersion 升版后再打包。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
