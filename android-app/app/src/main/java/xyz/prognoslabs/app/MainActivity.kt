package xyz.prognoslabs.app

import android.Manifest
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import xyz.prognoslabs.app.data.Analytics
import xyz.prognoslabs.app.ui.AppViewModel
import xyz.prognoslabs.app.ui.BiometricGate
import xyz.prognoslabs.app.ui.CrashReporter
import xyz.prognoslabs.app.ui.AuthScreen
import xyz.prognoslabs.app.ui.ChangePasswordScreen
import xyz.prognoslabs.app.ui.DashboardScreen
import xyz.prognoslabs.app.ui.Lime
import xyz.prognoslabs.app.ui.MarketDetailScreen
import xyz.prognoslabs.app.ui.MarketsScreen
import xyz.prognoslabs.app.ui.NotificationsScreen
import xyz.prognoslabs.app.ui.PortfolioScreen
import xyz.prognoslabs.app.ui.PrognosTheme
import xyz.prognoslabs.app.ui.SettingsScreen
import xyz.prognoslabs.app.ui.SplashScreen
import xyz.prognoslabs.app.ui.VoiceEnrollScreen
import xyz.prognoslabs.app.ui.VoiceSheetHost
import xyz.prognoslabs.app.ui.WalletScreen
import xyz.prognoslabs.app.ui.appColors
import xyz.prognoslabs.app.data.isLinkedReturnUrl

private data class Tab(val route: String, val label: String, val icon: ImageVector)

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    // v1.1.21: the mic permission launcher lives on the activity,
    // registered once here. It used to live in the Compose scaffold,
    // and on a fresh install the first tap on an enrollment mic
    // closed the app before the permission box ever appeared.
    // Launching from the activity is the safe pattern.
    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.onVoicePermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // v1.1.21: save the stack trace when the app dies, so the
        // next launch can show it in Settings.
        CrashReporter.install(this)
        // v1.1.9: edge-to-edge on every device. The app draws behind
        // the status bar and every camera cutout, with transparent
        // system bars; each screen lays its content out with window
        // insets (Scaffold padding, navigationBarsPadding on the
        // bottom bar and the voice sheet, statusBarsPadding on the
        // sign-in screen), so nothing hides behind a cutout.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // v1.1.10: Firebase Analytics. init only swaps in the
        // Firebase tracker; automatic events (installs, screen
        // views) start with the app. It never throws.
        Analytics.init(this)
        // Wallet link page sends the user back here with the trading
        // session in the URL (see WalletLink.kt). Catch it on launch.
        handleLinkedReturn(intent)
        // The ViewModel asks for a browser tab (wallet link page) by
        // emitting its URL; the activity opens it with a plain view
        // intent, so the user signs in their own browser and wallet.
        lifecycleScope.launch {
            vm.openBrowser.collect { url ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    // No browser on the device: the link screen in the
                    // Wallet tab still explains the next step.
                }
            }
        }
        // Launches the system biometric prompt only when the ViewModel
        // requests confirmation for a trade (after voice "yes" or tap Confirm).
        lifecycleScope.launch {
            vm.biometricTradeRequest.collect { request ->
                BiometricGate.authenticate(this@MainActivity) { result ->
                    vm.onBiometricTradeResult(request.requestId, result)
                }
            }
        }
        // Mic permission: the ViewModel asks, the activity launches
        // the system box, the answer goes back to the ViewModel. A
        // launch that fails shows a plain message instead of
        // closing the app (v1.1.21).
        lifecycleScope.launch {
            vm.voicePermissionNeeded.collect {
                try {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } catch (e: Exception) {
                    vm.onVoicePermissionResult(false)
                }
            }
        }
        setContent {
            val dark by vm.darkMode.collectAsState()
            PrognosTheme(darkTheme = dark) {
                App(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLinkedReturn(intent)
    }

    private fun handleLinkedReturn(intent: Intent?) {
        val url = intent?.dataString ?: return
        if (isLinkedReturnUrl(url)) vm.handleLinkedReturn(url)
    }
}

/**
 * Bottom bar, exactly as the preview draws it: icons + labels, NO
 * Material3 selected-indicator pill behind the active item. The active
 * tab has a bold label and a short lime LINE under its label; the rest
 * are grey. v8 used a 5dp lime dot under the active tab; Flint asked
 * for a line instead (17 Nov, v9). v7 used Material3 NavigationBar,
 * whose pill indicator is not in the preview. Icons are home, bar
 * chart, pie, wallet, as the preview.
 */
@Composable
private fun PrognosBottomBar(tabs: List<Tab>, selected: String, onSelect: (String) -> Unit) {
    val c = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(top = 8.dp, bottom = 12.dp)
            .navigationBarsPadding()
    ) {
        for (t in tabs) {
            val sel = selected == t.route
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(interactionSource = remember(t.route) { MutableInteractionSource() }, indication = null) { onSelect(t.route) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    t.icon,
                    contentDescription = t.label,
                    tint = if (sel) c.ink else c.grey2,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    t.label,
                    fontSize = 11.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) c.ink else c.grey2
                )
                Spacer(modifier = Modifier.height(3.dp))
                // Short lime line under the ACTIVE label only, centred.
                // v9 replaced the 5dp dot with this line (Flint).
                Box(
                    modifier = Modifier
                        .size(width = 24.dp, height = 3.dp)
                        .background(
                            if (sel) Lime else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

@Composable
fun App(vm: AppViewModel) {
    val auth by vm.auth.collectAsState()
    if (auth.loading) {
        SplashScreen()
        return
    }
    if (!auth.loggedIn) {
        AuthScreen(vm)
        return
    }
    MainScaffold(vm)
}

@Composable
fun MainScaffold(vm: AppViewModel) {
    val nav = rememberNavController()
    var tab by remember { mutableStateOf("dashboard") }
    val tabs = listOf(
        Tab("dashboard", "Dashboard", Icons.Filled.Home),
        Tab("markets", "Markets", Icons.Filled.BarChart),
        Tab("portfolio", "Portfolio", Icons.Filled.PieChart),
        Tab("wallet", "Wallet", Icons.Filled.AccountBalanceWallet)
    )
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route == "dashboard" || route == "markets" || route == "portfolio" || route == "wallet"

    fun goTab(route: String) {
        tab = route
        nav.navigate(route) {
            popUpTo(nav.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Voice navigation: a spoken "open Bitcoin" or "go to markets"
    // opens the market or the tab. Tab routes go through goTab so
    // the bottom bar follows; a market route navigates straight to
    // the market detail.
    LaunchedEffect(Unit) {
        vm.voiceNavRequest.collect { route ->
            if (route == null) return@collect
            if (route.startsWith("market/")) {
                nav.navigate(route)
            } else {
                goTab(route)
            }
            vm.clearVoiceNavRequest()
        }
    }

    // The voice sheet floats above everything, including
    // the bottom bar, so the mic works the same from every screen.
    // v1.1.15: the mic entry point is a small button in the header
    // of every screen, never a floating button over content. The
    // scaffold carries no mic button any more.
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            if (showBar) {
                PrognosBottomBar(tabs = tabs, selected = tab, onSelect = { goTab(it) })
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") {
                tab = "dashboard"
                DashboardScreen(
                    vm,
                    onOpenMarket = { id -> nav.navigate("market/$id") },
                    onBrowseMarkets = { goTab("markets") },
                    onOpenNotifications = { nav.navigate("notifications") },
                    onOpenSettings = { nav.navigate("settings") }
                )
            }
            composable("markets") {
                tab = "markets"
                MarketsScreen(vm, onOpenMarket = { id -> nav.navigate("market/$id") })
            }
            composable("portfolio") {
                tab = "portfolio"
                PortfolioScreen(
                    vm,
                    onBrowseMarkets = { goTab("markets") }
                )
            }
            composable("wallet") {
                tab = "wallet"
                WalletScreen(vm)
            }
            composable("notifications") {
                NotificationsScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onOpenVoiceEnroll = { nav.navigate("voiceEnroll") },
                    onOpenChangePassword = { nav.navigate("changePassword") }
                )
            }
            composable("changePassword") {
                ChangePasswordScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("voiceEnroll") {
                VoiceEnrollScreen(vm, onBack = { nav.popBackStack() })
            }
            composable(
                "market/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: return@composable
                LaunchedEffect(id) { vm.loadDetail(id) }
                // v1.1.2: the chart and panels tick live while this
                // screen is open (5s polls) and stop when it closes.
                DisposableEffect(id) {
                    vm.startDetailPolling(id)
                    onDispose { vm.stopDetailPolling() }
                }
                MarketDetailScreen(vm, marketId = id, onBack = { nav.popBackStack() })
            }
        }
    }
        VoiceSheetHost(
            vm,
            onOpenVoiceEnroll = { vm.startEnrollment(); nav.navigate("voiceEnroll") }
        )
    }
}
