package xyz.fruitslice.prognos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import xyz.fruitslice.prognos.ui.AppViewModel
import xyz.fruitslice.prognos.ui.AuthScreen
import xyz.fruitslice.prognos.ui.DashboardScreen
import xyz.fruitslice.prognos.ui.Lime
import xyz.fruitslice.prognos.ui.MarketDetailScreen
import xyz.fruitslice.prognos.ui.MarketsScreen
import xyz.fruitslice.prognos.ui.NotificationsScreen
import xyz.fruitslice.prognos.ui.PortfolioScreen
import xyz.fruitslice.prognos.ui.PrognosTheme
import xyz.fruitslice.prognos.ui.SettingsScreen
import xyz.fruitslice.prognos.ui.SplashScreen
import xyz.fruitslice.prognos.ui.WalletScreen
import xyz.fruitslice.prognos.ui.appColors
import xyz.fruitslice.prognos.data.isLinkedReturnUrl

private data class Tab(val route: String, val label: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    ) {
        for (t in tabs) {
            val sel = selected == t.route
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(t.route) },
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
                SettingsScreen(vm, onBack = { nav.popBackStack() })
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
}
