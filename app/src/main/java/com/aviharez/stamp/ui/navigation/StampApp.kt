package com.aviharez.stamp.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.components.NavTab
import com.aviharez.stamp.ui.debug.DebugGallery
import com.aviharez.stamp.ui.debug.DebugGalleryEntry
import com.aviharez.stamp.ui.components.StampNavBar
import com.aviharez.stamp.ui.screens.PlaceholderScreen
import com.aviharez.stamp.ui.screens.album.AlbumPageScreen
import com.aviharez.stamp.ui.screens.camera.CameraScreen
import com.aviharez.stamp.ui.screens.collections.CollectionsScreen
import com.aviharez.stamp.ui.screens.home.HomeScreen
import com.aviharez.stamp.ui.screens.stamped.StampedScreen
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.SystemBarsFor
import com.aviharez.stamp.ui.theme.rememberReduceMotion

/** App root: nav host, the StampNavBar on tab screens, and system-bar styling per screen. */
@Composable
fun StampApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val reduceMotion = rememberReduceMotion()

    val currentTab = destination?.tab()
    // Keep showing the last tab while the bar animates out (e.g. Home → Camera).
    var lastTab by remember { mutableStateOf(NavTab.Album) }
    if (currentTab != null && currentTab != lastTab) lastTab = currentTab

    SystemBarsFor(darkBackground = destination.isAny(CameraRoute::class, StampedRoute::class))

    Scaffold(
        containerColor = StampTheme.colors.paper,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = currentTab != null,
                enter = if (reduceMotion) EnterTransition.None else slideInVertically(tween(BarMillis)) { it } + fadeIn(tween(BarMillis)),
                exit = if (reduceMotion) ExitTransition.None else slideOutVertically(tween(BarMillis)) { it } + fadeOut(tween(BarMillis)),
            ) {
                StampNavBar(
                    selected = currentTab ?: lastTab,
                    onSelect = { nav.navigateToTab(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = StampTheme.spacing.screenFullBleed, vertical = StampTheme.spacing.xs),
                )
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(ScreenMillis)) },
            exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(ScreenMillis)) },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    contentPadding = inner,
                    onStampIt = { nav.navigate(CameraRoute) },
                    onOpenCollections = { nav.navigate(CollectionsRoute) },
                )
            }
            composable<CollectionsRoute> {
                CollectionsScreen(contentPadding = inner, onOpenAlbum = { nav.navigate(AlbumPageRoute(it)) })
            }
            composable<AlbumPageRoute> { backStack ->
                AlbumPageScreen(albumId = backStack.toRoute<AlbumPageRoute>().albumId, onBack = { nav.popBackStack() })
            }
            composable<CameraRoute>(
                enterTransition = { if (reduceMotion) EnterTransition.None else slideInVertically(tween(ScreenMillis)) { it / 6 } + fadeIn(tween(ScreenMillis)) },
                popExitTransition = { if (reduceMotion) ExitTransition.None else slideOutVertically(tween(ScreenMillis)) { it / 6 } + fadeOut(tween(ScreenMillis)) },
            ) {
                CameraScreen(
                    onClose = { nav.popBackStack() },
                    onCaptured = { path ->
                        // Replace the camera with the celebration; back from Stamped! returns Home.
                        nav.navigate(StampedRoute(path)) { popUpTo<CameraRoute> { inclusive = true } }
                    },
                )
            }
            composable<StampedRoute> { backStack ->
                StampedScreen(
                    stampPath = backStack.toRoute<StampedRoute>().stampPath,
                    onRetake = {
                        nav.navigate(CameraRoute) { popUpTo<StampedRoute> { inclusive = true } }
                    },
                    onStickInAlbum = { nav.popBackStack(HomeRoute, inclusive = false) },
                )
            }
            composable<CalendarRoute> {
                PlaceholderScreen(stringResource(R.string.nav_calendar), milestone = 2, contentPadding = inner)
            }
            composable<MapRoute> {
                PlaceholderScreen(stringResource(R.string.nav_map), milestone = 5, contentPadding = inner)
            }
            composable<ProfileRoute> {
                PlaceholderScreen(stringResource(R.string.nav_profile), milestone = 3, contentPadding = inner) {
                    DebugGalleryEntry(onOpen = { nav.navigate(DebugGalleryRoute) })
                }
            }
            composable<DebugGalleryRoute> { DebugGallery() }
        }
    }
}

private const val BarMillis = 220
private const val ScreenMillis = 240

private fun NavDestination.tab(): NavTab? = when {
    isAny(HomeRoute::class, CollectionsRoute::class) -> NavTab.Album
    isAny(CalendarRoute::class) -> NavTab.Calendar
    isAny(MapRoute::class) -> NavTab.Map
    isAny(ProfileRoute::class) -> NavTab.Profile
    else -> null
}

private fun NavDestination?.isAny(vararg routes: kotlin.reflect.KClass<*>): Boolean =
    this?.hierarchy?.any { dest -> routes.any { dest.hasRoute(it) } } == true

private fun NavHostController.navigateToTab(tab: NavTab) {
    val route: Any = when (tab) {
        NavTab.Album -> HomeRoute
        NavTab.Calendar -> CalendarRoute
        NavTab.Map -> MapRoute
        NavTab.Profile -> ProfileRoute
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
