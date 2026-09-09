package io.wenyou.textquest.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.model.CharacterData
import io.wenyou.textquest.data.model.SexualOrientation
import io.wenyou.textquest.ui.HubScaffold
import io.wenyou.textquest.ui.R
import io.wenyou.textquest.ui.common.EmojiBadge
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
    val filters by vm.filters.collectAsState()
    var pendingDelete by remember { mutableStateOf<CharacterData?>(null) }

    HubScaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("角色") }) },
        nav = nav
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (characters.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("还没有角色", style = MaterialTheme.typography.titleLarge)
                    Text("性格、说话方式与背景会注入 AI；分支剧本也可直接引用角色来展示台词。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OrientationFilterRow(
                            selected = filters.orientationFilter,
                            onSelect = { vm.setOrientationFilter(it) }
                        )
                    }
                    items(characters, key = { it.id }) { c ->
                        CharacterCard(c, onEdit = { nav.navigate(R.charEdit(c.id)) },
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

@Composable
private fun CharacterCard(c: CharacterData, onEdit: () -> Unit, onDelete: () -> Unit) {
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
                    Pill(c.orientation.label, container = MaterialTheme.colorScheme.secondaryContainer)
                    if (c.adult) Pill("18+", container = MaterialTheme.colorScheme.tertiaryContainer)
                    if (c.lgbt) Pill("LGBT", container = MaterialTheme.colorScheme.tertiaryContainer)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.outline) }
        }
    }
}
