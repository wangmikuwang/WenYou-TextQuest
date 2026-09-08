package io.wenyou.textquest.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.model.NodeKind
import io.wenyou.textquest.data.model.Story
import io.wenyou.textquest.ui.HubScaffold
import io.wenyou.textquest.ui.R
import io.wenyou.textquest.ui.common.EmojiBadge
import io.wenyou.textquest.ui.common.Pill
import io.wenyou.textquest.ui.theme.avatarColor
import io.wenyou.textquest.ui.vm.LibraryViewModel
import io.wenyou.textquest.ui.vm.Vms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryListScreen(container: WenYouApp.AppContainer, nav: NavHostController) {
    val vm: LibraryViewModel = viewModel(factory = Vms.factory { LibraryViewModel(it) })
    val stories by vm.stories.collectAsState()
    var pendingDelete by remember { mutableStateOf<Story?>(null) }

    HubScaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("剧情库") }) },
        nav = nav
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (stories.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("还没有任何剧情", style = MaterialTheme.typography.titleLarge)
                    Text("点右下角「＋」开始编一个分支故事，或用内置示例练手。\n纯分支剧本离线可玩；想用 AI 场景/导演就去配一家 API。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(stories, key = { it.id }) { story ->
                        StoryCard(story, onEdit = { nav.navigate(R.storyEdit(story.id)) },
                            onPlay = { nav.navigate(R.play(story.id)) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryCard(story: Story, onEdit: () -> Unit, onPlay: () -> Unit, onDelete: () -> Unit) {
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
                    Pill("${story.nodes.size} 节点")
                    if (aiNodes > 0) Pill("AI×$aiNodes", container = MaterialTheme.colorScheme.tertiaryContainer)
                    if (story.characterIds.isNotEmpty())
                        Pill("角色 ${story.characterIds.size}", container = MaterialTheme.colorScheme.secondaryContainer)
                }
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, "试玩", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
