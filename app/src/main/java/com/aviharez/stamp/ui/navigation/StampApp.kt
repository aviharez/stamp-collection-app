package com.aviharez.stamp.ui.navigation

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.aviharez.stamp.ui.debug.DebugProSwitch
import com.aviharez.stamp.ui.screens.paywall.PaywallReason
import com.aviharez.stamp.ui.screens.paywall.PaywallScreen
import com.aviharez.stamp.ui.screens.settings.AdPrivacyEntry
import com.aviharez.stamp.ui.screens.settings.BackupEntry
import com.aviharez.stamp.ui.screens.settings.ProEntry
import com.aviharez.stamp.ui.components.StampNavBar
import com.aviharez.stamp.ui.screens.PlaceholderScreen
import com.aviharez.stamp.ui.screens.album.AlbumPageScreen
import com.aviharez.stamp.ui.screens.calendar.CalendarScreen
import com.aviharez.stamp.ui.screens.camera.CameraScreen
import com.aviharez.stamp.ui.screens.collections.CollectionsScreen
import com.aviharez.stamp.ui.screens.home.HomeScreen
import com.aviharez.stamp.ui.screens.importer.ImportScreen
import com.aviharez.stamp.ui.screens.stamped.StampedScreen
import com.aviharez.stamp.ui.screens.welcome.FirstRunViewModel
import com.aviharez.stamp.ui.screens.detail.StampDetailScreen
import com.aviharez.stamp.ui.screens.welcome.WelcomeScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.SystemBarsFor
import com.aviharez.stamp.ui.theme.rememberReduceMotion

/** App root: nav host, the StampNavBar on tab screens, and system-bar styling per screen. */
@Composable
fun StampApp(firstRun: FirstRunViewModel = hiltViewModel()) {
    // Wait the few ms it takes to know whether this is a fresh install, so Home never flashes first.
    val showWelcome by firstRun.showWelcome.collectAsStateWithLifecycle()
    when (showWelcome) {
        null -> Box(Modifier.fillMaxSize().background(StampTheme.colors.paper))
        else -> StampNavigation(startOnWelcome = showWelcome == true, onWelcomeDone = firstRun::done)
    }
}

@Composable
private fun StampNavigation(startOnWelcome: Boolean, onWelcomeDone: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val reduceMotion = rememberReduceMotion()

    val currentTab = destination?.tab()
    // Keep showing the last tab while the bar animates out (e.g. Home → Camera).
    var lastTab by remember { mutableStateOf(NavTab.Album) }
    if (currentTab != null && currentTab != lastTab) lastTab = currentTab

    SystemBarsFor(darkBackground = destination.isAny(WelcomeRoute::class, CameraRoute::class, ImportRoute::class, StampedRoute::class))

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
                    // Re-tapping the current tab returns to its first screen (Collections → Home).
                    onSelect = { if (it == currentTab) nav.popBackStack(it.root, inclusive = false) else nav.navigateToTab(it) },
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
            startDestination = if (startOnWelcome) WelcomeRoute else HomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(ScreenMillis)) },
            exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(ScreenMillis)) },
        ) {
            composable<WelcomeRoute> {
                // Each choice swaps the welcome for Home underneath, so back from the camera lands Home.
                fun leaveTo(next: Any?) {
                    onWelcomeDone()
                    nav.navigate(HomeRoute) { popUpTo<WelcomeRoute> { inclusive = true } }
                    next?.let { nav.navigate(it) }
                }
                WelcomeScreen(
                    onMakeStamp = { leaveTo(CameraRoute(fromWelcome = true)) },
                    onPicked = { uri -> leaveTo(ImportRoute(uri.toString())) },
                    onSkip = { leaveTo(null) },
                )
            }
            composable<HomeRoute> {
                HomeScreen(
                    contentPadding = inner,
                    onStampIt = { nav.navigate(CameraRoute()) },
                    onOpenCollections = { nav.navigate(CollectionsRoute) },
                    onOpenAlbum = { nav.navigate(AlbumPageRoute(it)) },
                )
            }
            composable<CollectionsRoute> { entry ->
                val openNewAlbum by entry.savedStateHandle.getStateFlow(AlbumEarnedKey, false).collectAsStateWithLifecycle()
                CollectionsScreen(
                    contentPadding = inner,
                    onBack = { nav.popBackStack() },
                    onOpenAlbum = { nav.navigate(AlbumPageRoute(it)) },
                    onNeedPro = { nav.navigate(PaywallRoute(PaywallReason.Albums.name)) },
                    openNewAlbum = openNewAlbum,
                    onNewAlbumOpened = { entry.savedStateHandle[AlbumEarnedKey] = false },
                )
            }
            composable<AlbumPageRoute> {
                AlbumPageScreen(
                    onBack = { nav.popBackStack() },
                    onNextStamp = { nav.navigate(CameraRoute()) },
                    onGetPro = { nav.navigate(PaywallRoute(PaywallReason.Watermark.name)) },
                    onOpenStamp = { nav.navigate(StampDetailRoute(it)) },
                )
            }
            composable<StampDetailRoute> { entry ->
                val openNewAlbum by entry.savedStateHandle.getStateFlow(AlbumEarnedKey, false).collectAsStateWithLifecycle()
                StampDetailScreen(
                    onClose = { nav.popBackStack() },
                    onReframe = { nav.navigate(ImportRoute(reframeId = it)) },
                    onOpenPaywall = { reason -> nav.navigate(PaywallRoute(reason.name)) },
                    openNewAlbum = openNewAlbum,
                    onNewAlbumOpened = { entry.savedStateHandle[AlbumEarnedKey] = false },
                )
            }
            composable<CameraRoute>(
                enterTransition = { if (reduceMotion) EnterTransition.None else slideInVertically(tween(ScreenMillis)) { it / 6 } + fadeIn(tween(ScreenMillis)) },
                popExitTransition = { if (reduceMotion) ExitTransition.None else slideOutVertically(tween(ScreenMillis)) { it / 6 } + fadeOut(tween(ScreenMillis)) },
            ) { entry ->
                CameraScreen(
                    askRightAway = entry.toRoute<CameraRoute>().fromWelcome,
                    onClose = { nav.popBackStack() },
                    onCaptured = { stampId ->
                        // Replace the camera with the celebration; back from Stamped! returns Home.
                        nav.navigate(StampedRoute(stampId)) { popUpTo<CameraRoute> { inclusive = true } }
                    },
                    onImport = { uri -> nav.navigate(ImportRoute(uri.toString())) },
                )
            }
            composable<ImportRoute>(
                enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(ScreenMillis)) },
            ) { entry ->
                val reframing = entry.toRoute<ImportRoute>().reframeId != null
                ImportScreen(
                    onClose = { nav.popBackStack() },
                    onSaved = { stampId ->
                        // Re-framed: back to the stamp, which now shows the new picture.
                        if (reframing) {
                            nav.popBackStack()
                            return@ImportScreen
                        }
                        // Like a capture: Stamped! replaces the camera (if import started there) and this screen.
                        val fromCamera = nav.hasInBackStack<CameraRoute>()
                        nav.navigate(StampedRoute(stampId)) {
                            if (fromCamera) popUpTo<CameraRoute> { inclusive = true } else popUpTo<ImportRoute> { inclusive = true }
                        }
                    },
                    onPickAnother = { uri ->
                        nav.navigate(ImportRoute(uri.toString())) { popUpTo<ImportRoute> { inclusive = true } }
                    },
                )
            }
            composable<StampedRoute> { entry ->
                val openNewAlbum by entry.savedStateHandle.getStateFlow(AlbumEarnedKey, false).collectAsStateWithLifecycle()
                StampedScreen(
                    openNewAlbum = openNewAlbum,
                    onNewAlbumOpened = { entry.savedStateHandle[AlbumEarnedKey] = false },
                    onRetake = {
                        nav.navigate(CameraRoute()) { popUpTo<StampedRoute> { inclusive = true } }
                    },
                    onPickAnother = { uri ->
                        nav.navigate(ImportRoute(uri.toString())) { popUpTo<StampedRoute> { inclusive = true } }
                    },
                    onOpenPaywall = { reason -> nav.navigate(PaywallRoute(reason.name)) },
                    onStickInAlbum = { albumId ->
                        // Open the album the stamp went into; back from there returns Home.
                        nav.navigate(AlbumPageRoute(albumId)) { popUpTo<HomeRoute> { inclusive = false } }
                    },
                )
            }
            composable<CalendarRoute> {
                CalendarScreen(
                    contentPadding = inner,
                    onStampIt = { nav.navigate(CameraRoute()) },
                    onOpenAlbum = { nav.navigate(AlbumPageRoute(it)) },
                )
            }
            composable<MapRoute> {
                PlaceholderScreen(stringResource(R.string.nav_map), comingSoon = true, contentPadding = inner)
            }
            composable<ProfileRoute> {
                PlaceholderScreen(stringResource(R.string.nav_profile), comingSoon = false, contentPadding = inner) {
                    ProEntry(onGetPro = { nav.navigate(PaywallRoute(PaywallReason.Profile.name)) })
                    DebugGalleryEntry(onOpen = { nav.navigate(DebugGalleryRoute) })
                    BackupEntry()
                    AdPrivacyEntry()
                    DebugProSwitch()
                }
            }
            composable<PaywallRoute>(
                enterTransition = { if (reduceMotion) EnterTransition.None else slideInVertically(tween(ScreenMillis)) { it / 6 } + fadeIn(tween(ScreenMillis)) },
                popExitTransition = { if (reduceMotion) ExitTransition.None else slideOutVertically(tween(ScreenMillis)) { it / 6 } + fadeOut(tween(ScreenMillis)) },
            ) {
                PaywallScreen(
                    onClose = { nav.popBackStack() },
                    onAlbumUnlocked = {
                        // Back where "New album" was tapped, straight onto the form.
                        nav.previousBackStackEntry?.savedStateHandle?.set(AlbumEarnedKey, true)
                        nav.popBackStack()
                    },
                )
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

private inline fun <reified T : Any> NavHostController.hasInBackStack(): Boolean =
    runCatching { getBackStackEntry<T>() }.isSuccess

private fun NavDestination?.isAny(vararg routes: kotlin.reflect.KClass<*>): Boolean =
    this?.hierarchy?.any { dest -> routes.any { dest.hasRoute(it) } } == true

/** First screen of each tab. */
private val NavTab.root: Any
    get() = when (this) {
        NavTab.Album -> HomeRoute
        NavTab.Calendar -> CalendarRoute
        NavTab.Map -> MapRoute
        NavTab.Profile -> ProfileRoute
    }

private fun NavHostController.navigateToTab(tab: NavTab) {
    navigate(tab.root) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Paywall → caller: an album slot was earned with a rewarded ad; open the new-album form. */
private const val AlbumEarnedKey = "album_earned"
