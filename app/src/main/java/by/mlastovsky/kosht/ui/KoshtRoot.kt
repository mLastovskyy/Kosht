package by.mlastovsky.kosht.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.editor.EditorScreen
import by.mlastovsky.kosht.ui.history.HistoryScreen
import by.mlastovsky.kosht.ui.home.HomeScreen
import by.mlastovsky.kosht.ui.stats.StatsScreen
import by.mlastovsky.kosht.ui.navigation.MainTabs
import by.mlastovsky.kosht.ui.navigation.Routes

@Composable
fun KoshtRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isMainTab = MainTabs.any { it.route == currentRoute }
    val showFab = currentRoute == Routes.HOME || currentRoute == Routes.HISTORY

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = isMainTab,
                enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
            ) {
                KoshtNavigationBar(navController, currentRoute)
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showFab,
                enter = scaleIn(tween(200)) + fadeIn(tween(200)),
                exit = scaleOut(tween(150)) + fadeOut(tween(150))
            ) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.editor()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_add_transaction)
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(
                bottom = innerPadding.calculateBottomPadding()
            ),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onTransactionClick = { id -> navController.navigate(Routes.editor(id)) },
                    onSeeAllClick = { navController.navigateToTab(Routes.HISTORY) }
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onTransactionClick = { id -> navController.navigate(Routes.editor(id)) }
                )
            }
            composable(Routes.STATS) {
                StatsScreen()
            }
            composable(Routes.SETTINGS) {
                PlaceholderScreen(stringResource(R.string.nav_settings))
            }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument(Routes.EDITOR_ARG_ID) {
                        type = NavType.LongType
                        defaultValue = Routes.NO_ID
                    }
                ),
                enterTransition = {
                    slideInVertically(tween(300)) { it } + fadeIn(tween(300))
                },
                exitTransition = { fadeOut(tween(150)) },
                popExitTransition = {
                    slideOutVertically(tween(250)) { it } + fadeOut(tween(250))
                }
            ) {
                EditorScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun KoshtNavigationBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        MainTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTab(tab.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(tab.labelRes)) }
            )
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}
