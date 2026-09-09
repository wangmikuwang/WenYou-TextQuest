package io.wenyou.textquest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.data.model.CharacterMetrics
import io.wenyou.textquest.ui.common.AppField
import io.wenyou.textquest.ui.common.ColorDots
import io.wenyou.textquest.ui.common.EmojiBadge
import io.wenyou.textquest.ui.common.SectionHeader
import io.wenyou.textquest.ui.common.TonalCard
import io.wenyou.textquest.ui.theme.AvatarPalette
import io.wenyou.textquest.ui.theme.avatarColor
import io.wenyou.textquest.ui.vm.CharacterEditorViewModel
import io.wenyou.textquest.ui.vm.Vms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditScreen(container: WenYouApp.AppContainer, nav: NavHostController, charId: String) {
    val vm: CharacterEditorViewModel = viewModel(
        factory = Vms.factory { CharacterEditorViewModel(if (charId == "new") null else charId, it) }
    )
    val ui by vm.ui.collectAsState()
    val char = ui.char

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (ui.isNew) "新建角色" else "编辑角色") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (char == null) {
            Text("角色不存在", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (ui.message.isNotBlank()) {
                item { TonalCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(ui.message, color = MaterialTheme.colorScheme.onTertiaryContainer)
                } }
            }
            item {
                TonalCard {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        EmojiBadge(char.emoji.ifBlank { "🎭" }, avatarColor(char.colorIndex), size = 64.dp)
                        Spacer(Modifier.width(14.dp))
                        AppField(
                            value = char.name,
                            onValueChange = { vm.setName(it) },
                            label = "角色名字",
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            supporting = "将作为台词署名，并注入 AI 人设"
                        )
                    }
                    Spacer(Modifier.padding(top = 12.dp))
                    AppField(
                        value = char.emoji,
                        onValueChange = { vm.setEmoji(it.take(4)) },
                        label = "头像 Emoji",
                        singleLine = true,
                        supporting = "示例：🍂 🕯️ 🐱 ⚔️ 🧙"
                    )
                    Spacer(Modifier.padding(top = 10.dp))
                    Text("形象色", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.padding(top = 8.dp))
                    ColorDots(colors = AvatarPalette, selected = char.colorIndex, onSelect = { vm.setColor(it) })
                }
            }
            item { SectionHeader("人物设定（会原样交给 AI）") }
            item {
                TonalCard {
                    AppField(value = char.tagline, onValueChange = { vm.setTagline(it) },
                        label = "一句话印象", singleLine = true)
                    Spacer(Modifier.padding(top = 8.dp))
                    AppField(value = char.personality, onValueChange = { vm.setPersonality(it) },
                        label = "性格", minLines = 3,
                        placeholder = "例如：外冷内热、毒舌但守约、害怕人群却渴望被理解……")
                    Spacer(Modifier.padding(top = 8.dp))
                    AppField(value = char.speechStyle, onValueChange = { vm.setSpeech(it) },
                        label = "说话方式", minLines = 3,
                        placeholder = "例如：话少、爱用比喻；激动时语速加快；从不说谎。")
                    Spacer(Modifier.padding(top = 8.dp))
                    AppField(value = char.background, onValueChange = { vm.setBackground(it) },
                        label = "背景经历", minLines = 3,
                        placeholder = "与玩家相遇前的故事、身份、秘密……")
                    Spacer(Modifier.padding(top = 8.dp))
                    AppField(value = char.exampleDialogue, onValueChange = { vm.setExample(it) },
                        label = "台词示范", minLines = 2,
                        placeholder = "给 AI 一两句标志性台词，便于模仿语气。")
                    Spacer(Modifier.padding(top = 8.dp))
                    AppField(value = char.extraPrompt, onValueChange = { vm.setExtraPrompt(it) },
                        label = "附加人设提示语（高优先级）", minLines = 5,
                        placeholder = "写你的身份/世界观/规则/说话风格……会放到人设最前，权重最高，供 AI 优先遵循。",
                        supporting = "用于强化人设与世界观；拼接系统提示时位于最高优先级。")
                    Spacer(Modifier.padding(top = 8.dp))
                    AppField(value = char.bottomPrompt, onValueChange = { vm.setBottomPrompt(it) },
                        label = "底层基调 / 不可动摇规则", minLines = 5,
                        placeholder = "写本角色必须无条件遵守的底层规则……会拼接到该角色人设的最底部。",
                        supporting = "置于该角色人设最底，冲突时以此层为准；留空则不注入。")
                }
            }
            item { SectionHeader("初始状态（开局沿用）") }
            item {
                TonalCard {
                    Text(
                        "对局开始时该角色的数值、标记与外貌描述，之后由 AI 导演实时更新。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(top = 8.dp))
                    CharacterMetrics.defs.forEach { def ->
                        val v = char.initial.metrics[def.key] ?: 0.0
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("${def.icon} ${def.label}", modifier = Modifier.width(96.dp),
                                style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = v.toFloat(),
                                onValueChange = { vm.setInitialMetric(def.key, it.toDouble()) },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(v.toInt().toString(), modifier = Modifier.width(28.dp),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(Modifier.padding(top = 8.dp))
                    AppField(
                        value = ui.flagsText,
                        onValueChange = { vm.setInitialFlagsText(it) },
                        label = "初始标记（flags）",
                        minLines = 2,
                        placeholder = "例如：成年、警官、搭档（每行一个，或用逗号分隔）",
                        supporting = "用于剧情判断；可留空。"
                    )
                    Spacer(Modifier.padding(top = 8.dp))
                    AppField(
                        value = char.initial.description,
                        onValueChange = { vm.setInitialDesc(it) },
                        label = "初始状态描述",
                        minLines = 2,
                        placeholder = "例如：刚结束一场会议，靠在椅背上闭目养神。",
                        supporting = "角色侧边抽屉显示的当前状态描述，可留空。"
                    )
                }
            }
            item { Spacer(Modifier.padding(top = 4.dp)) }
            item {
                Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存角色")
                }
            }
        }
    }
}
