package io.wenyou.textquest.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.BuildConfig
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.model.CharacterData
import io.wenyou.textquest.data.model.SexualOrientation
import io.wenyou.textquest.ui.HubScaffold
import io.wenyou.textquest.ui.R
import io.wenyou.textquest.ui.common.EmojiBadge
import io.wenyou.textquest.ui.common.GifShareReader
import io.wenyou.textquest.ui.common.Pill
import io.wenyou.textquest.ui.common.TonalCard
import io.wenyou.textquest.ui.theme.avatarColor
import io.wenyou.textquest.ui.vm.LibraryViewModel
import io.wenyou.textquest.ui.vm.Vms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(container: WenYouApp.AppContainer, nav: NavHostController) {
    val vm: LibraryViewModel = viewModel(factory = Vms.factory { LibraryViewModel(it) })
    val characters by vm.characters.collectAsState()
    val totalCharacters by vm.totalCharacters.collectAsState()
    val filters by vm.filters.collectAsState()
    var pendingDelete by remember { mutableStateOf<CharacterData?>(null) }
    var sharePicker by remember { mutableStateOf<CharacterData?>(null) }
    var shareCodeChar by remember { mutableStateOf<CharacterData?>(null) }
    var shareQrChar by remember { mutableStateOf<CharacterData?>(null) }
    var importPicker by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val albumPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = try {
                context.contentResolver.openInputStream(uri)?.use { GifShareReader.read(it) }
            } catch (_: Throwable) { null }
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
                title = { Text("角色") },
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // 性取向筛选仅在文游α提供（β 版不含 lgbt 元素）
                    if (BuildConfig.BUILTIN_CONTENT) {
                        OrientationFilterRow(
                            selected = filters.orientationFilter,
                            onSelect = { vm.setOrientationFilter(it) }
                        )
                    }
                }
                if (characters.isEmpty()) {
                    item {
                        CharacterEmptyState(
                            title = if (totalCharacters > 0) "该分类下暂无角色" else "还没有角色",
                            body = if (totalCharacters > 0) "试试切换上方性取向，或清除筛选查看全部。" else "性格、说话方式与背景会注入 AI；分支剧本也可直接引用角色来展示台词。",
                            showReset = totalCharacters > 0,
                            onReset = { vm.setOrientationFilter(null) }
                        )
                    }
                } else {
                    items(characters, key = { it.id }) { c ->
                        CharacterCard(c, onEdit = { nav.navigate(R.charEdit(c.id)) },
                            onShare = { sharePicker = c },
                            onDelete = { pendingDelete = c })
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(R.charEdit("new")) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("新建角色") }
            )
        }
    }

    pendingDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除角色？") },
            text = { Text("「${c.name}」将被删除（已使用它的剧情不受影响）。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCharacter(c.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    sharePicker?.let { c ->
        SharePickDialog(
            title = c.name,
            onCode = { shareCodeChar = c; sharePicker = null },
            onQr = { shareQrChar = c; sharePicker = null },
            onDismiss = { sharePicker = null }
        )
    }
    shareCodeChar?.let { c ->
        ShareTextDialog(
            title = c.name,
            code = vm.shareCodeForCharacter(c.id),
            onDismiss = { shareCodeChar = null }
        )
    }
    shareQrChar?.let { c ->
        ShareQrDialog(
            title = c.name,
            code = vm.shareCodeForCharacter(c.id),
            onDismiss = { shareQrChar = null }
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

/** 性取向过滤 Chip 行（全部 + 各取向）。 */
@Composable
private fun OrientationFilterRow(
    selected: SexualOrientation?,
    onSelect: (SexualOrientation?) -> Unit
) {
    val options: List<SexualOrientation?> = listOf(null) + SexualOrientation.entries
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(opt?.label ?: "全部") }
            )
        }
    }
}

/** 全库为空 / 性取向筛选后无内容 的占位与「清除筛选」入口。 */
@Composable
private fun CharacterEmptyState(title: String, body: String, showReset: Boolean, onReset: () -> Unit) {
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

@Composable
private fun CharacterCard(c: CharacterData, onEdit: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(c.emoji, avatarColor(c.colorIndex), size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (c.tagline.isNotBlank())
                    Text(c.tagline, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (c.personality.isNotBlank())
                    Text("性格：${c.personality}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Spacer(Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (BuildConfig.BUILTIN_CONTENT) {
                        Pill(c.orientation.label, container = MaterialTheme.colorScheme.secondaryContainer)
                        if (c.lgbt) Pill("LGBT", container = MaterialTheme.colorScheme.tertiaryContainer)
                    }
                    if (c.adult) Pill("18+", container = MaterialTheme.colorScheme.tertiaryContainer)
                }
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, "分享", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.outline) }
        }
    }
}
