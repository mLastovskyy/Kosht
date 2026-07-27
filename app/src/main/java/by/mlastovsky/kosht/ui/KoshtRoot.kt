package by.mlastovsky.kosht.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import by.mlastovsky.kosht.MainViewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.account.AccountOnboardingScreen
import by.mlastovsky.kosht.ui.account.AccountViewModel
import by.mlastovsky.kosht.ui.achievements.AchievementsScreen
import by.mlastovsky.kosht.ui.awards.AwardCelebration
import by.mlastovsky.kosht.ui.components.UndoDeleteSnackbar
import by.mlastovsky.kosht.ui.editor.EditorScreen
import by.mlastovsky.kosht.ui.guide.GuideScreen
import by.mlastovsky.kosht.ui.history.HistoryScreen
import by.mlastovsky.kosht.ui.home.HomeScreen
import by.mlastovsky.kosht.ui.legal.PolicyUpdateDialog
import by.mlastovsky.kosht.ui.navigation.MainTabs
import by.mlastovsky.kosht.ui.navigation.Routes
import by.mlastovsky.kosht.ui.settings.SettingsScreen
import by.mlastovsky.kosht.ui.stats.StatsScreen
import by.mlastovsky.kosht.ui.tour.TourScreen
import by.mlastovsky.kosht.ui.tour.TourViewModel
import by.mlastovsky.kosht.ui.wallet.WalletScreen
import kotlinx.coroutines.launch

@Composable
fun KoshtRoot(
    accountViewModel: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val account by accountViewModel.account.collectAsStateWithLifecycle()

    val state = account ?: return
    if (!state.onboarded && accountViewModel.isConfigured) {
        AccountOnboardingScreen(accountViewModel)
        return
    }

    val tourSeen = TourSeen() ?: return
    if (!tourSeen) {
        TourScreen()
        return
    }

    AskForNotificationsOnce()

    PolicyUpdateNotice()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isMainTab = currentRoute == null || currentRoute == Routes.TABS

    val pagerState = rememberPagerState(
        pageCount = { MainTabs.size }
    )
    val homePage = MainTabs.indexOfFirst { it.route == Routes.HOME }
    val historyPage = MainTabs.indexOfFirst { it.route == Routes.HISTORY }
    val showFab = isMainTab &&
        (pagerState.currentPage == homePage || pagerState.currentPage == historyPage)
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember {
        SnackbarHostState()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = isMainTab,
                enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
            ) {
                KoshtNavigationBar(
                    selectedPage = pagerState.currentPage,
                    onSelect = { page ->
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
                )
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
            startDestination = Routes.TABS,
            modifier = Modifier.padding(
                bottom = innerPadding.calculateBottomPadding()
            ),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) }
        ) {
            composable(Routes.TABS) {
                HorizontalPager(
                    state = pagerState,

                    beyondViewportPageCount = 1,
                    key = { page -> MainTabs[page].route }
                ) { page ->
                    when (MainTabs[page].route) {
                        Routes.HOME -> HomeScreen(
                            onTransactionClick = { id ->
                                navController.navigate(Routes.editor(id))
                            },
                            onSeeAllClick = {
                                scope.launch { pagerState.animateScrollToPage(historyPage) }
                            },
                            onAchievementsClick = { navController.navigate(Routes.ACHIEVEMENTS) }
                        )

                        Routes.HISTORY -> HistoryScreen(
                            onTransactionClick = { id ->
                                navController.navigate(Routes.editor(id))
                            },
                            onScreen = pagerState.currentPage == page
                        )

                        Routes.STATS -> StatsScreen(
                            onTransactionClick = { id ->
                                navController.navigate(Routes.editor(id))
                            }
                        )

                        Routes.WALLET -> WalletScreen()

                        else -> SettingsScreen(
                            onOpenGuide = { navController.navigate(Routes.GUIDE) }
                        )
                    }
                }
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

    AwardCelebration()

    UndoDeleteSnackbar(snackbarHostState)
}

@Composable
private fun TourSeen(
    viewModel: TourViewModel = viewModel(factory = AppViewModelProvider.Factory)
): Boolean? {
    val seen by viewModel.seen.collectAsStateWithLifecycle()
    return seen
}

@Composable
private fun PolicyUpdateNotice(
    viewModel: MainViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val updated by viewModel.policyUpdated.collectAsStateWithLifecycle()
    if (!updated) return
    PolicyUpdateDialog(
        onAcknowledge = viewModel::acknowledgePolicy
    )
}

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
private fun KoshtNavigationBar(selectedPage: Int, onSelect: (Int) -> Unit) {
    NavigationBar {
        MainTabs.forEachIndexed { page, tab ->
            val selected = page == selectedPage
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(page) },
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
