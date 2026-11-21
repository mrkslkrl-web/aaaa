package app.solco.music

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.solco.music.ui.SolcoViewModel
import app.solco.music.ui.components.AmbientBackdrop
import app.solco.music.ui.screens.AlbumDetailScreen
import app.solco.music.ui.screens.ArtistDetailScreen
import app.solco.music.ui.screens.HomeScreen
import app.solco.music.ui.screens.ImportScreen
import app.solco.music.ui.screens.LibraryScreen
import app.solco.music.ui.screens.LoginScreen
import app.solco.music.ui.screens.MoreScreen
import app.solco.music.ui.screens.PendingScreen
import app.solco.music.ui.screens.ReleasesScreen
import app.solco.music.ui.screens.ReviewsScreen
import app.solco.music.ui.screens.SettingsScreen
import app.solco.music.ui.screens.TopScreen
import app.solco.music.ui.screens.TrackDetailScreen
import app.solco.music.ui.screens.UwuFufuScreen
import app.solco.music.ui.theme.SolcoColors
import app.solco.music.ui.theme.SolcoTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            SolcoTheme {
                SolcoRoot()
            }
        }
    }
}

private data class NavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color
)

private val bottomTabs = listOf(
    NavTab("home", "Home", Icons.Rounded.Home, SolcoColors.AccentViolet),
    NavTab("artists", "Artisti", Icons.Rounded.Person, SolcoColors.AccentCyan),
    NavTab("albums", "Album", Icons.Rounded.Album, SolcoColors.AccentPink),
    NavTab("singles", "Singoli", Icons.Rounded.MusicNote, SolcoColors.AccentMint),
    NavTab("more", "Altro", Icons.Rounded.GridView, SolcoColors.AccentGold)
)

@Composable
private fun SolcoRoot(vm: SolcoViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        vm.toast.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Automatically refresh Recent Plays & Now Playing whenever app returns to foreground
    DisposableEffect(lifecycleOwner, state.phase) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state.phase == "ready") {
                if (System.currentTimeMillis() - state.lastSyncMs > 20_000L) {
                    vm.syncRecentAuto()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Lightweight automatic background poll every 45s while app is open
    LaunchedEffect(state.phase) {
        if (state.phase != "ready") return@LaunchedEffect
        while (true) {
            delay(45_000L)
            vm.syncRecentAuto()
        }
    }

    when (state.phase) {
        "boot" -> {
            AmbientBackdrop {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SolcoColors.AccentViolet)
                }
            }
        }
        "login" -> {
            LoginScreen(vm)
        }
        else -> {
            val nav = rememberNavController()
            val backStack by nav.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route ?: "home"

            fun navigateTo(rawRoute: String) {
                val parts = rawRoute.split("/")
                val encoded = if (parts.size == 2 && parts[0] in listOf("artist", "album", "track")) {
                    "${parts[0]}/${URLEncoder.encode(parts[1], "UTF-8")}"
                } else rawRoute
                nav.navigate(encoded) {
                    launchSingleTop = true
                }
            }

            AmbientBackdrop {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        FloatingGlassBottomBar(
                            currentRoute = currentRoute,
                            unresolvedCount = state.model.unresolved.size,
                            onSelect = { tabRoute ->
                                nav.navigate(tabRoute) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                ) { _ ->
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable("home") { HomeScreen(state, vm, ::navigateTo) }
                        composable("artists") { LibraryScreen("artist", state, vm, ::navigateTo) }
                        composable("albums") { LibraryScreen("album", state, vm, ::navigateTo) }
                        composable("singles") { LibraryScreen("track", state, vm, ::navigateTo) }
                        composable("more") { MoreScreen(state, ::navigateTo) }

                        composable(
                            route = "artist/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { entry ->
                            val id = URLDecoder.decode(entry.arguments?.getString("id").orEmpty(), "UTF-8")
                            ArtistDetailScreen(id, state, vm, onBack = { nav.popBackStack() }, onNavigate = ::navigateTo)
                        }
                        composable(
                            route = "album/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { entry ->
                            val id = URLDecoder.decode(entry.arguments?.getString("id").orEmpty(), "UTF-8")
                            AlbumDetailScreen(id, state, vm, onBack = { nav.popBackStack() }, onNavigate = ::navigateTo)
                        }
                        composable(
                            route = "track/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { entry ->
                            val id = URLDecoder.decode(entry.arguments?.getString("id").orEmpty(), "UTF-8")
                            TrackDetailScreen(id, state, vm, onBack = { nav.popBackStack() }, onNavigate = ::navigateTo)
                        }

                        composable(
                            route = "reviews?tab={tab}",
                            arguments = listOf(navArgument("tab") { defaultValue = "album" })
                        ) { entry ->
                            val tab = entry.arguments?.getString("tab") ?: "album"
                            ReviewsScreen(tab, state, vm, onBack = { nav.popBackStack() }, onNavigate = ::navigateTo)
                        }
                        composable("reviews") {
                            ReviewsScreen("album", state, vm, onBack = { nav.popBackStack() }, onNavigate = ::navigateTo)
                        }
                        composable("releases") {
                            ReleasesScreen(state, vm, onBack = { nav.popBackStack() }, onNavigate = ::navigateTo)
                        }
                        composable("top") {
                            TopScreen(state, vm, onBack = { nav.popBackStack() }, onNavigate = ::navigateTo)
                        }
                        composable("uwu") {
                            UwuFufuScreen(state, vm, onBack = { nav.popBackStack() })
                        }
                        composable("pending") {
                            PendingScreen(state, vm, onBack = { nav.popBackStack() })
                        }
                        composable("import") {
                            ImportScreen(state, vm, onBack = { nav.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(state, vm, onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingGlassBottomBar(
    currentRoute: String,
    unresolvedCount: Int,
    onSelect: (String) -> Unit
) {
    val activeTab = when {
        currentRoute.startsWith("artist") -> "artists"
        currentRoute.startsWith("album") -> "albums"
        currentRoute.startsWith("track") || currentRoute == "singles" -> "singles"
        currentRoute == "home" -> "home"
        else -> "more"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(SolcoColors.BgSurface.copy(alpha = 0.94f))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(SolcoColors.BorderGlow, SolcoColors.Border)),
                    RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomTabs.forEach { tab ->
                val selected = activeTab == tab.route
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selected) tab.accent.copy(alpha = 0.22f) else Color.Transparent
                        )
                        .then(
                            if (selected) Modifier.border(1.dp, tab.accent.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                            else Modifier
                        )
                        .clickable { onSelect(tab.route) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) tab.accent else SolcoColors.TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            if (tab.route == "more" && unresolvedCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(SolcoColors.AccentCoral)
                                )
                            }
                        }
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) tab.accent else SolcoColors.TextMuted
                        )
                    }
                }
            }
        }
    }
}
