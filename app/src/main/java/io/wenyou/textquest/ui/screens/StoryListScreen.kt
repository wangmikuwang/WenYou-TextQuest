package io.wenyou.textquest.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.BuildConfig
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.model.ContentClass
import io.wenyou.textquest.data.model.NodeKind
import io.wenyou.textquest.data.model.SaveSlot
import io.wenyou.textquest.data.model.Story
import io.wenyou.textquest.data.model.storyContentClass
import io.wenyou.textquest.ui.HubScaffold
import io.wenyou.textquest.ui.R
import io.wenyou.textquest.ui.common.EmojiBadge
import io.wenyou.textquest.ui.common.Pill
import io.wenyou.textquest.ui.theme.avatarColor
import io.wenyou.textquest.ui.vm.LibraryViewModel
import io.wenyou.textquest.ui.vm.StoryContentFilter
import io.wenyou.textquest.ui.vm.StoryModeFilter
import io.wenyou.textquest.ui.vm.Vms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryListScreen(container: WenYouApp.AppContainer, nav: NavHostController) {
    val vm: LibraryViewModel = viewModel(factory = Vms.factory { LibraryViewModel(it) })
    val stories by vm.stories.collectAsState()
    val totalStories by vm.totalStories.collectAsState()
    val filters by vm.filters.collectAsState()
    var pendingDelete by remember { mutableStateOf<Story?>(null) }
    var managesSaves by remember { mutableStateOf<Story?>(null) }
    var sharingStory by remember { mutableStateOf<Story?>(null) }
    var importDialog by remember { mutableStateOf(false) }

    HubScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("剧情库") },
                actions = {
                    TextButton(onClick = { importDialog = true }) { Text("导入码") }
                }
            )
        },
        nav = nav
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    FilterChipRow(
                        options = StoryModeFilter.entries,
                        selected = filters.modeFilter,
                        label = { it.label },
                        onSelect = { vm.setModeFilter(it) }
                    )
                }
                item {
                    val contentOptions = if (BuildConfig.BUILTIN_CONTENT) StoryContentFilter.entries
                    else StoryContentFilter.entries.filter { it != StoryContentFilter.LGBT }
                    FilterChipRow(
                        options = contentOptions,
                        selected = filters.contentFilter,
                        label = { it.label },
                        onSelect = { vm.setContentFilter(it) }
                    )
                }
                if (stories.isEmpty()) {
                    item {
                        FilterEmptyState(
                            title = if (totalStories > 0) "该分类下暂无剧情" else "还没有任何剧情",
                            body = if (totalStories > 0) "试试切换上方分类，或清除筛选查看全部。" else "点右下角「＋」编一个分支故事，或用内置示例练手。",
                            showReset = totalStories > 0,
                            onReset = {
                                vm.setModeFilter(StoryModeFilter.ALL)
                                vm.setContentFilter(StoryContentFilter.ALL)
                            }
                        )
                    }
                } else {
                    items(stories, key = { it.id }) { story ->
                        StoryCard(story,
                            onEdit = { nav.navigate(R.storyEdit(story.id)) },
                            onPlay = { nav.navigate(R.play(story.id)) },
                            onSaves = { managesSaves = story },
                            onShare = { sharingStory = story },
                            onDelete = { pendingDelete = story })
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(R.storyEdit("new")) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("新建剧情") }
            )
        }
    }

    pendingDelete?.let { story ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除剧情？") },
            text = { Text("「${story.title}」及其所有存档都会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteStory(story.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    managesSaves?.let { story ->
        SavesDialog(
            story = story,
            saves = vm.savesForStory(story.id),
            onLoad = { slot -> nav.navigate(R.play(story.id, slot.id)) },
            onDelete = { slot -> vm.deleteSave(slot.id) },
            onDismiss = { managesSaves = null }
        )
    }

    sharingStory?.let { story ->
        ShareCodeDialog(
            story = story,
            code = vm.shareCodeFor(story.id),
            onDismiss = { sharingStory = null }
        )
    }

    if (importDialog) {
        ImportCodeDialog(
            onDismiss = { importDialog = false },
            onImport = { code, cb -> vm.importShareCode(code, cb) }
        )
    }
}

/** 生成/复制/分享一段剧情的分享码。 */
@Composable
private fun ShareCodeDialog(story: Story, code: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享码 · ${story.title}") },
        text = {
            Column {
                Text(
                    "把这段分享码发给朋友，对方在剧情库点右上角「导入码」即可获得本剧情及其角色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = {},
                    readOnly = true,
                    minLines = 3,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (copied) "已复制到剪贴板" else "分享码通常较长，复制或系统分享均可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                }) { Text("复制") }
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, code)
                    }
                    context.startActivity(Intent.createChooser(send, "分享剧情"))
                }) { Text("分享") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

/** 粘贴分享码并导入（只补不覆盖）。 */
@Composable
private fun ImportCodeDialog(onDismiss: () -> Unit, onImport: (String, (String) -> Unit) -> Unit) {
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入分享码") },
        text = {
            Column {
                Text(
                    "粘贴对方发来的分享码（以 WY1: 开头）。剧情节与角色会按 id 补入，不覆盖你已有的内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("分享码") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                if (result.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(result, style = MaterialTheme.typography.bodySmall,
                        color = if (result.startsWith("导入成功")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onImport(text) { result = it }
            }) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/** 横向滚动的过滤 Chip 行（全部 + 各分类）。 */
@Composable
private fun <T> FilterChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(label(opt)) }
            )
        }
    }
}

/** 全库为空 / 分类筛选后无内容 的占位与「清除筛选」入口。 */
@Composable
private fun FilterEmptyState(title: String, body: String, showReset: Boolean, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        if (showReset) {
            TextButton(onClick = onReset) { Text("清除筛选 / 查看全部") }
        }
    }
}

/** 列出某剧情的所有存档：可读取或删除。 */
@Composable
private fun SavesDialog(
    story: Story,
    saves: List<SaveSlot>,
    onLoad: (SaveSlot) -> Unit,
    onDelete: (SaveSlot) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("存档 · ${story.title}") },
        text = {
            if (saves.isEmpty()) {
                Text("还没有存档。对局页右上角「✓」可保存当前进度。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    saves.forEach { slot ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { onLoad(slot) }) {
                                Text(slot.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text("${slot.state.history.size} 步 · ${LibraryViewModel.formatWhen(slot.updatedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDelete(slot) }) {
                                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryCard(story: Story, onEdit: () -> Unit, onPlay: () -> Unit, onSaves: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val color = avatarColor(story.colorIndex)
    val aiNodes = story.nodes.values.count { it.kind == NodeKind.AI }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(story.coverEmoji, color, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(story.title, style = MaterialTheme.typography.titleLarge)
                if (story.subtitle.isNotBlank())
                    Text(story.subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(story.mode.label)
                    // β 版不含 lgbt 元素：不渲染 LGBT 内容分类标记
                    val cls = storyContentClass(story)
                    if (!(BuildConfig.BARE_CONTENT && cls == ContentClass.LGBT)) {
                        Pill(cls.label,
                            container = if (story.adult) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer)
                    }
                    Pill("${story.nodes.size} 节点")
                    if (aiNodes > 0) Pill("AI×$aiNodes", container = MaterialTheme.colorScheme.tertiaryContainer)
                    if (story.characterIds.isNotEmpty())
                        Pill("角色 ${story.characterIds.size}", container = MaterialTheme.colorScheme.secondaryContainer)
                }
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, "游玩", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onSaves) {
                Icon(Icons.Filled.Check, "读取存档", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, "生成分享码", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
