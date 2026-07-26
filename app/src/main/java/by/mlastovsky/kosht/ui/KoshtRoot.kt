package by.mlastovsky.kosht.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.LaunchedEffect
import by.mlastovsky.kosht.MainViewModel
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.account.AccountOnboardingScreen
import by.mlastovsky.kosht.ui.account.AccountViewModel
import by.mlastovsky.kosht.ui.achievements.AchievementsScreen
import by.mlastovsky.kosht.ui.editor.EditorScreen
import by.mlastovsky.kosht.ui.guide.GuideScreen
import by.mlastovsky.kosht.ui.history.HistoryScreen
import by.mlastovsky.kosht.ui.home.HomeScreen
import by.mlastovsky.kosht.ui.settings.SettingsScreen
import by.mlastovsky.kosht.ui.stats.StatsScreen
import by.mlastovsky.kosht.ui.wallet.WalletScreen
import by.mlastovsky.kosht.ui.navigation.MainTabs
import by.mlastovsky.kosht.ui.navigation.Routes

@Composable
fun KoshtRoot(
    accountViewModel: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    // Nothing until the stored answer is known, so the main UI never flashes
    // up only to be replaced by the first-launch question.
    val state = account ?: return
    if (!state.onboarded && accountViewModel.isConfigured) {
        AccountOnboardingScreen(accountViewModel)
        return
    }

    AskForNotificationsOnce()

    // Above the whole app, because a change in the documents concerns whoever
    // is using it, on whichever screen they happen to be.
    PolicyUpdateNotice()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isMainTab = MainTabs.any { it.route == currentRoute }
    val showFab = currentRoute == Routes.HOME || currentRoute == Routes.HISTORY

    // One host for the whole app: the Scaffold that owns the add button is the
    // only thing that can keep a snackbar out from under it.
    val snackbarHostState = androidx.compose.runtime.remember {
        androidx.compose.material3.SnackbarHostState()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
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
                    onSeeAllClick = { navController.navigateToTab(Routes.HISTORY) },
                    onAchievementsClick = { navController.navigate(Routes.ACHIEVEMENTS) }
                )
            }
            composable(
                route = Routes.ACHIEVEMENTS,
                enterTransition = {
                    slideInVertically(tween(300)) { it } + fadeIn(tween(300))
                },
                exitTransition = { fadeOut(tween(150)) },
                popExitTransition = {
                    slideOutVertically(tween(250)) { it } + fadeOut(tween(250))
                }
            ) {
                AchievementsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onTransactionClick = { id -> navController.navigate(Routes.editor(id)) }
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    onTransactionClick = { id -> navController.navigate(Routes.editor(id)) }
                )
            }
            composable(Routes.WALLET) {
                WalletScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenGuide = { navController.navigate(Routes.GUIDE) }
                )
            }
            composable(
                route = Routes.GUIDE,
                enterTransition = {
                    slideInVertically(tween(300)) { it } + fadeIn(tween(300))
                },
                exitTransition = { fadeOut(tween(150)) },
                popExitTransition = {
                    slideOutVertically(tween(250)) { it } + fadeOut(tween(250))
                }
            ) {
                GuideScreen(onBack = { navController.popBackStack() })
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

    // Above every screen, because an award is earned on whichever one is open.
    by.mlastovsky.kosht.ui.awards.AwardCelebration()

    // Same reason: a record can be deleted from History or from the editor.
    by.mlastovsky.kosht.ui.components.UndoDeleteSnackbar(snackbarHostState)
}

/**
 * The Terms and the data policy do change, and a person who agreed to one text
 * should hear about the next one rather than find it in Settings. Shown once per
 * version, whatever screen is open.
 */
@Composable
private fun PolicyUpdateNotice(
    viewModel: MainViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val updated by viewModel.policyUpdated.collectAsStateWithLifecycle()
    if (!updated) return
    by.mlastovsky.kosht.ui.legal.PolicyUpdateDialog(
        onAcknowledge = viewModel::acknowledgePolicy
    )
}

/**
 * "Payments awaiting confirmation" is on out of the box, but Android 13 and
 * later post nothing until the user allows it — and Settings only asks when a
 * switch is flipped, which someone who never touched the defaults would never
 * do. So ask once, after the account question is out of the way, and remember
 * that it was asked whatever the answer.
 */
@Composable
private fun AskForNotificationsOnce(
    viewModel: MainViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val asked by viewModel.notificationsAsked.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val wanted = settings?.let {
        it.notifyDailyReminder || it.notifyRecurringDue || it.notifyWeeklySummary ||
            it.notifyAwards
    } == true
    LaunchedEffect(asked, wanted) {
        if (asked || !wanted) return@LaunchedEffect
        viewModel.markNotificationsAsked()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                        contentDescription = stringResource(tab.labelRes)
                    )
                }
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
