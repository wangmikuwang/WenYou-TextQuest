package io.wenyou.textquest.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.BuildConfig
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.model.ContentClass
import io.wenyou.textquest.data.model.NodeKind
import io.wenyou.textquest.data.model.SaveSlot
import io.wenyou.textquest.data.model.Story
import io.wenyou.textquest.data.model.StoryMode
import io.wenyou.textquest.data.model.storyContentClass
import io.wenyou.textquest.ui.HubScaffold
import io.wenyou.textquest.ui.R
import io.wenyou.textquest.ui.common.EmojiBadge
import io.wenyou.textquest.ui.common.Pill
import io.wenyou.textquest.ui.common.QrCode
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
    // 分享：先选「分享码 or 二维码」，再进对应界面
    var sharePicker by remember { mutableStateOf<Story?>(null) }
    var shareCodeStory by remember { mutableStateOf<Story?>(null) }
    var shareQrStory by remember { mutableStateOf<Story?>(null) }
    // 导入：先选「粘贴分享码 or 扫码识别」
    var importPicker by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    // 从相册选一张含二维码的图片识别
    val albumPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            val text = bmp?.let { QrCode.decode(it) }
            if (text.isNullOrBlank()) {
                android.widget.Toast.makeText(context, "未识别到二维码", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                vm.importShareCode(text) { msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    HubScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("剧情库") },
                actions = {
                    TextButton(onClick = { importPicker = true }) { Text("导入码") }
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
                            onShare = { sharePicker = story },
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

    sharePicker?.let { story ->
        SharePickDialog(
            title = story.title,
            onCode = { shareCodeStory = story; sharePicker = null },
            onQr = { shareQrStory = story; sharePicker = null },
            onDismiss = { sharePicker = null }
        )
    }

    shareCodeStory?.let { story ->
        ShareTextDialog(
            title = story.title,
            code = vm.shareCodeFor(story.id),
            onDismiss = { shareCodeStory = null }
        )
    }

    shareQrStory?.let { story ->
        ShareQrDialog(
            title = story.title,
            code = vm.shareCodeFor(story.id),
            onDismiss = { shareQrStory = null }
        )
    }

    if (importPicker) {
        ImportPickDialog(
            onText = { importText = true; importPicker = false },
            onScan = { importPicker = false; scanning = true },
            onAlbum = { importPicker = false; albumPicker.launch("image/*") },
            onDismiss = { importPicker = false }
        )
    }

    if (importText) {
        ImportTextDialog(
            onDismiss = { importText = false },
            onImport = { code, cb -> vm.importShareCode(code, cb) }
        )
    }

    if (scanning) {
        QrScannerDialog(
            onResult = { text ->
                scanning = false
                vm.importShareCode(text) { msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { scanning = false }
        )
    }
}

/** 分享方式选择：分享码（文本） or 二维码。 */
@Composable
fun SharePickDialog(title: String, onCode: () -> Unit, onQr: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享「$title」") },
        text = { Text("选择分享方式：给对方「分享码」文本，或生成「二维码」让对方直接扫码。") },
        confirmButton = {
            Row {
                TextButton(onClick = onCode) { Text("分享码") }
                TextButton(onClick = onQr) { Text("二维码") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/** 分享码（文本）弹窗：复制或系统分享。 */
@Composable
fun ShareTextDialog(title: String, code: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享码 · $title") },
        text = {
            Column {
                Text(
                    "把下面的分享码发给朋友，对方在「导入码 → 粘贴分享码」即可导入。",
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
                    if (copied) "已复制到剪贴板" else "可复制，或直接调用系统分享。",
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

/** 高对比白色圆角卡片里的二维码，观感贴近设备配对页。 */
@Composable
private fun QrCard(bitmap: android.graphics.Bitmap, dp: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "二维码",
            modifier = Modifier.padding(10.dp).size(dp.dp)
        )
    }
}

/** 模拟“正在连接”的脉冲提示。 */
@Composable
private fun ConnectingIndicator(label: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse)
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** 二维码弹窗：单张大图、白底圆角卡片、连接动画；支持保存到本地。 */
@Composable
fun ShareQrDialog(title: String, code: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val qr = remember(code) { QrCode.encode(code, 720) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("二维码 · $title") },
        text = {
            Column {
                ConnectingIndicator("对方扫码即可连接")
                Spacer(Modifier.height(10.dp))
                if (qr != null) {
                    QrCard(qr, 280, Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "用手机相机对准上方二维码，或回到「导入码 → 相机扫码 / 相册识别」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("该内容较大，二维码放不下，可改用「分享码」文本。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (copied) "已复制分享码文本" else "也可点「复制文本」手动粘贴。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                }) { Text("复制") }
                if (qr != null) {
                    TextButton(onClick = {
                        val loc = QrCode.saveToGallery(context, qr, title)
                        android.widget.Toast.makeText(context,
                            if (loc != null) "已保存到 $loc" else "保存失败",
                            android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text("保存") }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

/** 导入方式选择：粘贴分享码 / 相机扫码 / 相册图片识别。 */
@Composable
fun ImportPickDialog(onText: () -> Unit, onScan: () -> Unit, onAlbum: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入分享码") },
        text = { Text("选择导入方式：粘贴「分享码」文本，或从二维码识别（相机扫码 / 相册选图）。") },
        confirmButton = {
            Row {
                TextButton(onClick = onText) { Text("粘贴分享码") }
                TextButton(onClick = onScan) { Text("相机扫码") }
                TextButton(onClick = onAlbum) { Text("相册识别") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/** 粘贴分享码文本导入（只补不覆盖）。 */
@Composable
fun ImportTextDialog(onDismiss: () -> Unit, onImport: (String, (String) -> Unit) -> Unit) {
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入分享码") },
        text = {
            Column {
                Text(
                    "粘贴对方发来的分享码（以 WY1: 开头）。剧情与角色按 id 补入，不覆盖已有内容。",
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
            Row {
                TextButton(onClick = {
                    if (text.isNotBlank()) onImport(text) { result = it }
                }) { Text("导入") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StoryCard(story: Story, onEdit: () -> Unit, onPlay: () -> Unit, onSaves: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val color = avatarColor(story.colorIndex)
    val aiNodes = story.nodes.values.count { it.kind == NodeKind.AI }
    var menuOpen by remember { mutableStateOf(false) }
    val modeText = if (story.mode == StoryMode.AI_DIRECTOR) "AI 导演" else "分支剧本"
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                EmojiBadge(story.coverEmoji, color, size = 54.dp)
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier.weight(1f).padding(top = 2.dp).clickable(onClick = onEdit)
                ) {
                    Text(story.title, style = MaterialTheme.typography.titleLarge, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    if (story.subtitle.isNotBlank())
                        Text(story.subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onPlay,
                    modifier = Modifier.align(Alignment.CenterVertically)) {
                    Icon(Icons.Filled.PlayArrow, "游玩")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("读取存档") }, onClick = { menuOpen = false; onSaves() })
                        DropdownMenuItem(text = { Text("生成分享码") }, onClick = { menuOpen = false; onShare() })
                        DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Pill(modeText)
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
    }
}
