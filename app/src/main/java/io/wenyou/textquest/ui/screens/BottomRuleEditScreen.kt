package io.wenyou.textquest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.ui.common.AppField
import io.wenyou.textquest.ui.common.TonalCard
import io.wenyou.textquest.ui.vm.BottomRuleEditorViewModel
import io.wenyou.textquest.ui.vm.Vms

/** 新建／编辑一条底层基调（不可动摇规则）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomRuleEditScreen(container: WenYouApp.AppContainer, nav: NavHostController, ruleId: String) {
    val vm: BottomRuleEditorViewModel = viewModel(
        factory = Vms.factory { BottomRuleEditorViewModel(if (ruleId == "new") null else ruleId, it) }
    )
    val ui by vm.ui.collectAsState()
    val rule = ui.rule

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (ui.isNew) "新建底层基调" else "编辑底层基调") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (rule == null) {
            Text("底层基调不存在", Modifier.padding(padding))
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
                    Text(
                        "底层基调是角色的「不可动摇规则」。角色扮演时先执行它，再按人物设定扮演；冲突时以此层为准。一个底层基调可被多个角色选择执行。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(top = 12.dp))
                    AppField(
                        value = rule.name,
                        onValueChange = { vm.setName(it) },
                        label = "名称",
                        singleLine = true,
                        placeholder = "例如：绝不伤害所爱、始终守口如瓶、无论何时都保持优雅",
                        supporting = "用于在角色编辑里识别这条规则。"
                    )
                    Spacer(Modifier.padding(top = 10.dp))
                    AppField(
                        value = rule.content,
                        onValueChange = { vm.setContent(it) },
                        label = "底层基调内容（不可动摇规则）",
                        minLines = 6,
                        placeholder = "例如：无论剧情如何推进，本角色都绝不出卖同伴；遭到胁迫时优先保全他人性命。",
                        supporting = "会原样注入到该角色人设的最底层，作为最高优先级约束。"
                    )
                }
            }
            item { Spacer(Modifier.padding(top = 4.dp)) }
            item {
                Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text("保存底层基调")
                }
            }
        }
    }
}
