package com.blumenkraft.morningnewsanchor.companion.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blumenkraft.morningnewsanchor.companion.R
import com.blumenkraft.morningnewsanchor.companion.ui.auth.AuthScreen
import com.blumenkraft.morningnewsanchor.companion.ui.auth.AuthViewModel
import com.blumenkraft.morningnewsanchor.companion.ui.auth.AuthUiState
import com.blumenkraft.morningnewsanchor.companion.ui.briefings.BriefingDetailScreen
import com.blumenkraft.morningnewsanchor.companion.ui.briefings.BriefingsScreen
import com.blumenkraft.morningnewsanchor.companion.ui.dashboard.DashboardScreen
import com.blumenkraft.morningnewsanchor.companion.ui.feeds.FeedsScreen
import com.blumenkraft.morningnewsanchor.companion.ui.inbox.InboxScreen
import com.blumenkraft.morningnewsanchor.companion.ui.preferences.PreferencesScreen

sealed class Dest(val route: String) {
    data object Dashboard : Dest("dashboard")
    data object Briefings : Dest("briefings")
    data object Feeds : Dest("feeds")
    data object Inbox : Dest("inbox")
    data object Preferences : Dest("preferences")

    data object BriefingDetail : Dest("briefings/{briefingId}") {
        fun of(id: String) = "briefings/$id"
        const val ARG = "briefingId"
    }
}

private data class Tab(val dest: Dest, val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Dest.Dashboard, R.string.nav_dashboard, Icons.Default.WbSunny),
    Tab(Dest.Briefings, R.string.nav_briefings, Icons.AutoMirrored.Filled.Article),
    Tab(Dest.Feeds, R.string.nav_feeds, Icons.Default.RssFeed),
    Tab(Dest.Inbox, R.string.nav_inbox, Icons.Default.Inbox),
    Tab(Dest.Preferences, R.string.nav_preferences, Icons.Default.Settings),
)

/**
 * Root of the UI. Auth is a gate rather than a destination: while signed out the nav graph
 * isn't composed at all, so no screen can ever query with a null user id.
 */
@Composable
fun CompanionApp(onReady: () -> Unit) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        if (authState !is AuthUiState.Loading) onReady()
    }

    when (authState) {
        is AuthUiState.Loading -> Unit // splash is still up
        is AuthUiState.SignedOut,
        is AuthUiState.Error,
        -> AuthScreen(viewModel = authViewModel)
        is AuthUiState.SignedIn -> SignedInScaffold()
    }
}

@Composable
private fun SignedInScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDest?.hierarchy?.any { it.route == tab.dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.dest.route) {
                                // Standard bottom-nav behaviour: single instance per tab, and
                                // switching tabs doesn't stack copies of the start destination.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.Dashboard.route) {
                DashboardScreen(
                    onOpenBriefing = { id -> navController.navigate(Dest.BriefingDetail.of(id)) },
                )
            }
            composable(Dest.Briefings.route) {
                BriefingsScreen(
                    onOpenBriefing = { id -> navController.navigate(Dest.BriefingDetail.of(id)) },
                )
            }
            composable(Dest.BriefingDetail.route) { entry ->
                BriefingDetailScreen(
                    briefingId = entry.arguments?.getString(Dest.BriefingDetail.ARG).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Dest.Feeds.route) { FeedsScreen() }
            composable(Dest.Inbox.route) { InboxScreen() }
            composable(Dest.Preferences.route) { PreferencesScreen() }
        }
    }
}
