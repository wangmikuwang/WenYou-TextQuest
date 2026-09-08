package io.wenyou.textquest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.ui.common.AppDropdown
import io.wenyou.textquest.ui.common.AppField
import io.wenyou.textquest.ui.common.SectionHeader
import io.wenyou.textquest.ui.common.TonalCard
import io.wenyou.textquest.ui.vm.ProviderEditorState
import io.wenyou.textquest.ui.vm.ProviderEditorViewModel
import io.wenyou.textquest.ui.vm.Vms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(container: WenYouApp.AppContainer, nav: NavHostController, providerId: String) {
    val vm: ProviderEditorViewModel = viewModel(
        factory = Vms.factory { ProviderEditorViewModel(if (providerId == "new") null else providerId, it) }
    )
    val ui by vm.ui.collectAsState()
    val profile = ui.profile
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (ui.isNew) "接入 AI 服务" else "编辑服务") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (!ui.isNew) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, "删除服务", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (profile == null) {
            Text("服务不存在", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (ui.message.isNotBlank()) {
                item {
                    TonalCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(ui.message, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            item { SectionHeader("1 · 选择厂商（可一键填充地址与模型）") }
            item {
                TonalCard {
                    val presetKey = profile.let { p ->
                        vm.presets.firstOrNull { it.kind == p.kind && it.baseUrl == p.baseUrl }?.key
                    }
                    AppDropdown(
                        label = "厂商 / 预设",
                        options = vm.presets.map { it.label to it.key },
                        selected = presetKey,
                        onSelect = { vm.applyPreset(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    AppField(value = profile.name, onValueChange = { vm.setName(it) },
                        label = "服务名称", singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    AppField(value = profile.baseUrl, onValueChange = { vm.setBase(it) },
                        label = "接口地址 baseUrl", singleLine = true,
                        supporting = "OpenAI 兼容：…/v1（含 /chat/completions 会自动补全）")
                    Spacer(Modifier.height(8.dp))
                    AppField(value = profile.model, onValueChange = { vm.setModel(it) },
                        label = "模型名 model", singleLine = true,
                        supporting = "示例：deepseek-chat / glm-4-flash / claude-3-5-sonnet-latest / gemini-2.0-flash")
                    Spacer(Modifier.height(8.dp))
                    AppField(value = profile.apiKey, onValueChange = { vm.setKey(it) },
                        label = "API Key", singleLine = true,
                        supporting = if (profile.kind.label.contains("Ollama") || profile.note.contains("本地"))
                            "本地服务通常无需 Key" else "只存本机，不会上传",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (profile.apiKey.length > 6) PasswordVisualTransformation() else VisualTransformation.None)
                    Spacer(Modifier.height(8.dp))
                    AppField(value = profile.note, onValueChange = { vm.setNote(it) },
                        label = "备注（可选）", singleLine = true)
                }
            }
            item { SectionHeader("2 · 生成参数") }
            item {
                TonalCard {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("创意温度", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(90.dp))
                        Slider(
                            value = profile.temperature.toFloat(),
                            onValueChange = { vm.setTemperature(it.toDouble()) },
                            valueRange = 0.0f..1.5f
                        )
                        Text(String.format("%.2f", profile.temperature), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    IntField(
                        value = profile.maxTokens,
                        onChange = { vm.setMaxTokens(it) },
                        label = "最大回复长度（tokens）"
                    )
                }
            }
            item { ModelPickerCard(ui, vm) }
            item { Spacer(Modifier.height(4.dp)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { vm.test() },
                        enabled = !ui.testing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (ui.testing) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        else Text("测试连接")
                    }
                    Button(onClick = { vm.save() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Check, null)
                        Spacer(Modifier.width(6.dp))
                        Text("保存")
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除服务？") },
            text = { Text("「${profile?.name ?: ""}」将被移除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete()
                    nav.navigateUp()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerCard(ui: ProviderEditorState, vm: ProviderEditorViewModel) {
    TonalCard {
        Text("可用模型（从接口读取）", style = MaterialTheme.typography.labelLarge)
        Text("填好接口地址与 Key 后点「读取」，从下面点选一个模型名即可自动填入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.refreshModels() }, enabled = !ui.listingModels) {
                if (ui.listingModels) {
                    CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("读取可用模型")
                }
            }
            if (ui.listMessage.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(ui.listMessage, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f))
            }
        }
        if (ui.availableModels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ui.availableModels.forEach { model ->
                    FilterChip(
                        selected = model == ui.profile?.model,
                        onClick = { vm.setModel(model) },
                        label = { Text(model, maxLines = 1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun IntField(value: Int, onChange: (Int) -> Unit, label: String) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = draft,
        onValueChange = { raw ->
            draft = raw
            raw.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

