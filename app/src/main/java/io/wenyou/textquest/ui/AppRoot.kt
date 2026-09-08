package io.wenyou.textquest.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.wenyou.textquest.WenYouApp
import io.wenyou.textquest.ui.screens.CharacterEditScreen
import io.wenyou.textquest.ui.screens.CharactersScreen
import io.wenyou.textquest.ui.screens.HomeScreen
import io.wenyou.textquest.ui.screens.PlayScreen
import io.wenyou.textquest.ui.screens.ProviderEditScreen
import io.wenyou.textquest.ui.screens.ProvidersScreen
import io.wenyou.textquest.ui.screens.SettingsScreen
import io.wenyou.textquest.ui.screens.StoryEditScreen
import io.wenyou.textquest.ui.screens.StoryListScreen
import io.wenyou.textquest.ui.theme.WenYouTheme

/** 全部路由。编辑/游玩页参数约定：`new` 表示新建。 */
object R {
    const val HOME = "home"
    const val STORIES = "stories"
    const val CHARACTERS = "characters"
    const val PROVIDERS = "providers"
    const val SETTINGS = "settings"

    const val ARG_STORY = "storyId"
    const val ARG_SAVE = "saveId"
    const val ARG_CHAR = "charId"
    const val ARG_PROVIDER = "providerId"

    const val STORY_EDIT = "story_edit/{$ARG_STORY}"
    const val CHAR_EDIT = "char_edit/{$ARG_CHAR}"
    const val PROVIDER_EDIT = "provider_edit/{$ARG_PROVIDER}"
    const val PLAY = "play/{$ARG_STORY}/{$ARG_SAVE}"

    fun storyEdit(id: String) = "story_edit/$id"
    fun charEdit(id: String) = "char_edit/$id"
    fun providerEdit(id: String) = "provider_edit/$id"
    fun play(storyId: String, saveId: String = "new") = "play/$storyId/$saveId"

    val HUBS = setOf(HOME, STORIES, CHARACTERS, PROVIDERS, SETTINGS)
}

@Composable
fun WenYouAppRoot(container: WenYouApp.AppContainer) {
    val prefs by container.settings.state.collectAsState()
    WenYouTheme(prefs.themeMode, prefs.dynamicColor) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = R.HOME) {
            composable(R.HOME) { HomeScreen(container, nav) }
            composable(R.STORIES) { StoryListScreen(container, nav) }
            composable(
                R.STORY_EDIT,
                arguments = listOf(navArgument(R.ARG_STORY) { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString(R.ARG_STORY) ?: "new"
                StoryEditScreen(container, nav, storyId = id)
            }
            composable(R.CHARACTERS) { CharactersScreen(container, nav) }
            composable(
                R.CHAR_EDIT,
                arguments = listOf(navArgument(R.ARG_CHAR) { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString(R.ARG_CHAR) ?: "new"
                CharacterEditScreen(container, nav, charId = id)
            }
            composable(R.PROVIDERS) { ProvidersScreen(container, nav) }
            composable(
                R.PROVIDER_EDIT,
                arguments = listOf(navArgument(R.ARG_PROVIDER) { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString(R.ARG_PROVIDER) ?: "new"
                ProviderEditScreen(container, nav, providerId = id)
            }
            composable(R.SETTINGS) { SettingsScreen(container, nav) }
            composable(
                R.PLAY,
                arguments = listOf(
                    navArgument(R.ARG_STORY) { type = NavType.StringType },
                    navArgument(R.ARG_SAVE) { type = NavType.StringType }
                )
            ) { entry ->
                val storyId = entry.arguments?.getString(R.ARG_STORY).orEmpty()
                val saveId = entry.arguments?.getString(R.ARG_SAVE) ?: "new"
                PlayScreen(container, nav, storyId = storyId, saveId = saveId)
            }
        }
    }
}

/** Hub 底部导航（Material 3 NavigationBar）。 */
@Composable
fun HubBottomBar(nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val items = listOf(
        HubItem(R.HOME, "主页", Icons.Filled.Home),
        HubItem(R.STORIES, "剧情", Icons.AutoMirrored.Filled.List),
        HubItem(R.CHARACTERS, "角色", Icons.Filled.Person),
        HubItem(R.PROVIDERS, "AI 服务", Icons.Filled.Build),
        HubItem(R.SETTINGS, "设置", Icons.Filled.Settings)
    )
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = current == item.route,
                onClick = {
                    if (current != item.route) {
                        nav.navigate(item.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}

/** 带底部导航的 Hub 页 Scaffold（顶部栏由各页决定：无则传 null）。 */
@Composable
fun HubScaffold(
    topBar: @Composable () -> Unit,
    nav: NavHostController,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    Scaffold(
        topBar = topBar,
        bottomBar = { HubBottomBar(nav) }
    ) { padding ->
        content(padding)
    }
}

private data class HubItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
