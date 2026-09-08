package io.wenyou.textquest.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.model.SaveSlot
import io.wenyou.textquest.ui.HubScaffold
import io.wenyou.textquest.ui.R
import io.wenyou.textquest.ui.common.EmojiBadge
import io.wenyou.textquest.ui.common.Pill
import io.wenyou.textquest.ui.common.SectionHeader
import io.wenyou.textquest.ui.theme.avatarColor
import io.wenyou.textquest.ui.vm.HomeCard
import io.wenyou.textquest.ui.vm.LibraryViewModel
import io.wenyou.textquest.ui.vm.Vms

@Composable
fun HomeScreen(container: WenYouApp.AppContainer, nav: NavHostController) {
    val vm: LibraryViewModel = viewModel(factory = Vms.factory { LibraryViewModel(it) })
    val cards by vm.homeCards.collectAsState()
    val stories by vm.stories.collectAsState()
    val providers by vm.providers.collectAsState()
    var pendingDelete by remember { mutableStateOf<HomeCard?>(null) }

    HubScaffold(topBar = {}, nav = nav) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text("文游 · 文字游戏", style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("自编剧情 · 自定义角色 · 多品牌 AI 演绎", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { nav.navigate(R.storyEdit("new")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("新建剧情")
                    }
                    FilledTonalButton(
                        onClick = {
                            if (stories.isNotEmpty()) nav.navigate(R.play(stories.first().id))
                            else nav.navigate(R.STORIES)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (stories.isNotEmpty()) "快速开始" else "去剧情库")
                    }
                }
            }

            if (providers.isEmpty()) {
                item {
                    MissingProviderCard(onClick = { nav.navigate(R.PROVIDERS) })
                }
            }

            if (cards.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有存档", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("新建或选择一个剧情，开始你的第一段旅程。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item { SectionHeader("继续上次的旅程") }
                items(cards, key = { it.slot.id }) { card ->
                    ContinueCard(
                        card,
                        onClick = { nav.navigate(R.play(card.slot.state.storyId, card.slot.id)) },
                        onDelete = { pendingDelete = card }
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionHeader("剧情库 · ${stories.size}")
                    if (stories.isEmpty()) {
                        Text("点击上方「新建剧情」开始创作。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    stories.take(3).forEach { s ->
                        MiniStoryRow(
                            title = s.title,
                            subtitle = s.subtitle,
                            emoji = s.coverEmoji,
                            color = avatarColor(s.colorIndex),
                            onClick = { nav.navigate(R.storyEdit(s.id)) }
                        )
                    }
                    OutlinedButton(
                        onClick = { nav.navigate(R.STORIES) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) { Text("查看全部剧情 / 角色 / 设置") }
                }
            }
        }
    }

    pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除存档？") },
            text = { Text("「${card.slot.name}」（${card.story?.title ?: ""}）将无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSave(card.slot.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MissingProviderCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Build, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("还没有接入 AI 服务", style = MaterialTheme.typography.titleMedium)
                Text("剧本分支可离线游玩；AI 导演与 AI 场景需要任一家 API（支持 DeepSeek / Kimi / OpenAI / Claude / Gemini / Ollama 本地…）。点此去配置。",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ContinueCard(card: HomeCard, onClick: () -> Unit, onDelete: () -> Unit) {
    val story = card.story
    val color = avatarColor(story?.colorIndex ?: 0)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(52.dp).height(52.dp)
                    .clickable(onClick = onClick)
            ) {
                EmojiBadge(story?.coverEmoji ?: "📖", color, fontSize = 24.sp, size = 52.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(card.slot.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(story?.title ?: "（剧情已删除）", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row {
                    Pill(card.stepText)
                    if (story?.mode != null && story.mode.label.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Pill(story.mode.label, container = MaterialTheme.colorScheme.tertiaryContainer)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun MiniStoryRow(
    title: String, subtitle: String, emoji: String, color: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiBadge(emoji, color.copy(alpha = 0.35f), size = 40.dp, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank())
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
