package io.wenyou.textquest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.model.ApiProfile
import io.wenyou.textquest.data.model.CharacterData
import io.wenyou.textquest.data.model.CharacterMetrics
import io.wenyou.textquest.data.model.EntryKind
import io.wenyou.textquest.data.engine.GameEngine
import io.wenyou.textquest.data.model.LogEntry
import io.wenyou.textquest.data.model.NodeKind
import io.wenyou.textquest.ui.theme.avatarColor
import io.wenyou.textquest.ui.vm.PlayStage
import io.wenyou.textquest.ui.vm.PlayUi
import io.wenyou.textquest.ui.vm.PlayViewModel
import io.wenyou.textquest.ui.vm.Vms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(container: WenYouApp.AppContainer, nav: NavHostController, storyId: String, saveId: String) {
    val vm: PlayViewModel = viewModel(
        factory = Vms.factory { PlayViewModel(storyId, saveId, it) }
    )
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(ui.lastMessage) {
        if (ui.lastMessage.isNotBlank()) snackbar.showSnackbar(ui.lastMessage)
    }
    val history = ui.session?.history.orEmpty()
    val live = (ui.stage == PlayStage.AI_WORKING && ui.aiDelta.isNotBlank())
    var showProvider by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(history.size, ui.aiDelta.length) {
        val last = history.size - 1 + if (live) 1 else 0
        if (last >= 0) listState.scrollToItem(last)
    }

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = { CharacterStateDrawer(ui) }) {
        Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.story?.title ?: "对局", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (ui.nodeTitle.isNotBlank() && ui.nodeTitle != ui.story?.title) {
                            Text(ui.nodeTitle, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { scope.launch { drawerState.open() } }) { Text("状态") }
                    TextButton(onClick = { showProvider = true },
                        enabled = ui.providers.isNotEmpty()) {
                        Text("模型")
                    }
                    IconButton(onClick = { vm.saveNow() }) { Icon(Icons.Filled.Check, "存档") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                itemsIndexed(history) { _, entry ->
                    StoryEntry(entry, ui)
                    Spacer(Modifier.height(10.dp))
                }
                if (live) {
                    item(key = "live") {
                        StreamingCard(ui.aiDelta)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
            }
            ActionPanel(vm, ui, nav)
        }
    }
    }

    if (showProvider) {
        ProviderDialog(
            ui = ui,
            vm = vm,
            container = container,
            onDismiss = { showProvider = false }
        )
    }
}

// ---------------------------------------------------------------------------
// 对局内切换 AI 服务（模型）
// ---------------------------------------------------------------------------

@Composable
private fun ProviderDialog(
    ui: PlayUi,
    vm: PlayViewModel,
    container: WenYouApp.AppContainer,
    onDismiss: () -> Unit
) {
    val defaultId = container.settings.defaultProviderId
    val effective = ui.selectedProviderId
        ?: ui.providers.firstOrNull { it.id == defaultId }?.id
        ?: ui.providers.firstOrNull()?.id
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用哪个 AI 服务？") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProviderOption(
                    label = "跟随默认服务",
                    sub = ui.providers.firstOrNull { it.id == defaultId }?.let { "${it.name} · ${it.model}" }
                        ?: ui.providers.firstOrNull()?.let { "${it.name} · ${it.model}" }
                        ?: "（暂无）",
                    selected = ui.selectedProviderId == null,
                    onClick = { vm.selectProvider(null); onDismiss() }
                )
                ui.providers.forEach { p ->
                    ProviderOption(
                        label = p.name,
                        sub = "${p.model} · ${p.kind.label}",
                        selected = p.id == effective,
                        onClick = { vm.selectProvider(p.id); onDismiss() }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ProviderOption(
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                if (sub.isNotBlank())
                    Text(sub, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (selected) {
                Icon(Icons.Filled.Check, "当前", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 文案渲染
// ---------------------------------------------------------------------------

@Composable
private fun StoryEntry(entry: LogEntry, ui: PlayUi) {
    val text = entry.text
    if (text.isBlank()) return
    when (entry.kind) {
        EntryKind.NARRATION -> {
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        EntryKind.CHARACTER -> {
            val char = ui.characters.firstOrNull { it.id == entry.speakerId }
            val color = avatarColor(char?.colorIndex ?: 0)
            val name = entry.speaker.ifBlank { char?.name ?: "角色" }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(color.red, color.green, color.blue, alpha = 0.10f)
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = color) {
                            Text(char?.emoji ?: "🎭",
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.titleSmall, color = color,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        EntryKind.CHOICE -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(text,
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        EntryKind.DM -> {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("🌫 ${entry.speaker.ifBlank { "AI 导演" }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
        EntryKind.SYSTEM -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontStyle = FontStyle.Italic)
            }
        }
        EntryKind.ERROR -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(text,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun StreamingCard(delta: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(delta.ifBlank { "AI 正在构思…" }, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("▍", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------------------------------------------------------------------------
// 底部操作面板
// ---------------------------------------------------------------------------

@Composable
private fun ActionPanel(vm: PlayViewModel, ui: PlayUi, nav: NavHostController) {
    when (ui.stage) {
        PlayStage.INIT -> Unit
        PlayStage.AI_WORKING -> {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在写作……", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PlayStage.AUTHORED -> {
            val node = ui.story?.nodes?.get(ui.nodeId)
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (ui.visibleChoices.isNotEmpty()) {
                    Text("接下来……", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    ui.visibleChoices.forEachIndexed { i, choice ->
                        Button(
                            onClick = { vm.chooseAuthored(i) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text(choice.text) }
                    }
                } else if (ui.pendingAiChoices.isNotEmpty()) {
                    Text("你的选择：", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    ui.pendingAiChoices.forEachIndexed { i, choice ->
                        FilledTonalButton(
                            onClick = { vm.chooseAi(i) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) { Text(choice.text) }
                    }
                    if (ui.aiTargetExit) {
                        OutlinedButton(
                            onClick = { vm.aiExitToMainline() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        ) { Text("（结束这段，回到主线）") }
                    }
                } else {
                    val isAiNode = node?.kind == NodeKind.AI
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isAiNode) {
                            Text("AI 未给出选项。", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(onClick = { vm.continueAi() }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("继续生成")
                                }
                                if (ui.aiTargetExit) {
                                    OutlinedButton(onClick = { vm.aiExitToMainline() }, modifier = Modifier.weight(1f)) {
                                        Text("回到主线")
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(Modifier.fillMaxWidth(), color = Color.Transparent) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("存档后随时可在主页继续", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.restart() }) { Text("重开本局") }
                    }
                }
            }
        }
        PlayStage.DM_INPUT -> {
            DmInput(ui, vm)
        }
        PlayStage.STOPPED -> {
            StoppedPanel(ui, vm, nav)
        }
    }
}

@Composable
private fun DmInput(ui: PlayUi, vm: PlayViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ui.pendingAiChoices.isNotEmpty()) {
                Text("AI 导演给的走向灵感（点一下直接采用，也可自由输入）：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.pendingAiChoices.forEach { c ->
                        FilterChip(selected = false, onClick = { vm.dmSend(c.text) },
                            label = { Text(c.text, maxLines = 1) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("输入你想做的事 / 说的话……") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    minLines = 1,
                    shape = RoundedCornerShape(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            vm.dmSend(text)
                            text = ""
                        }
                    },
                    enabled = text.isNotBlank()
                ) {
                    Icon(Icons.Filled.Send, "发送", tint = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(onClick = { vm.dmSend("继续") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("让导演继续（不输入直接推进）")
            }
        }
    }
}

@Composable
private fun StoppedPanel(ui: PlayUi, vm: PlayViewModel, nav: NavHostController) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(12.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ui.stoppedTitle.ifBlank { "这一局结束了" },
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(ui.stoppedMessage, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.restart() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("再来一次")
                }
                FilledTonalButton(onClick = { vm.retryAi() },
                    enabled = ui.aiTargetExit || ui.story?.nodes?.get(ui.nodeId)?.kind == NodeKind.AI,
                    modifier = Modifier.weight(1f)) {
                    Text("重试")
                }
                OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.weight(1f)) {
                    Text("返回")
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { vm.saveNow() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("保留这份存档")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 角色状态抽屉
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterStateDrawer(ui: PlayUi) {
    Surface(
        modifier = Modifier.fillMaxHeight().width(300.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("角色状态", style = MaterialTheme.typography.titleLarge)
            if (ui.session?.characterStates.isNullOrEmpty()) {
                Text("还没有角色状态。剧情里为角色设置「好感度/身体状况/穿着」等效果后，这里会实时显示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ui.characters.forEach { c ->
                val st = ui.session?.characterStates?.get(c.id)
                if (st == null) return@forEach
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${c.emoji} ${c.name}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        CharacterMetrics.defs.forEach { d ->
                            val v = st.metrics[d.key] ?: return@forEach
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${d.icon} ${d.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f))
                                Text(GameEngine.formatNumber(CharacterMetrics.clamp(v)),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            LinearProgressIndicator(
                                progress = { (CharacterMetrics.clamp(v) / 100.0).toFloat() },
                                modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (st.flags.isNotEmpty())
                            Text("标记：${st.flags.joinToString("、")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (st.description.isNotBlank())
                            Text("穿着/外观：${st.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
