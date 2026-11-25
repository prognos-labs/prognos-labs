package xyz.prognoslabs.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.prognoslabs.app.data.AppConfig
import xyz.prognoslabs.app.data.DisplayCurrency
import xyz.prognoslabs.app.data.MarketFilter
import xyz.prognoslabs.app.data.PAGE_SIZE
import xyz.prognoslabs.app.data.TradeSide
import xyz.prognoslabs.app.data.TradingLock
import xyz.prognoslabs.app.data.TradingState
import xyz.prognoslabs.app.data.btcEquivalentText
import xyz.prognoslabs.app.data.buildForecastContext
import xyz.prognoslabs.app.data.dashboardShowsSpinner
import xyz.prognoslabs.app.data.filterMarkets
import xyz.prognoslabs.app.data.fmtMoney
import xyz.prognoslabs.app.data.fmtPrice
import xyz.prognoslabs.app.data.fmtTickerPrice
import xyz.prognoslabs.app.data.fmtUsd
import xyz.prognoslabs.app.data.formatResendCountdown
import xyz.prognoslabs.app.data.greetingForHour
import xyz.prognoslabs.app.data.pageMarkets
import xyz.prognoslabs.app.data.remainingCount
import xyz.prognoslabs.app.data.sanitizeAmountInput
import xyz.prognoslabs.app.data.topMovers
import xyz.prognoslabs.app.model.ASSET_LABELS
import java.time.LocalTime

@Composable
fun SplashScreen() {
    val c = appColors()
    Box(modifier = Modifier.fillMaxSize()) {
        AuthBackground()
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LogoImage(96)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Prognos Labs", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(color = c.ink)
        }
    }
}

@Composable
fun AuthScreen(vm: AppViewModel) {
    val c = appColors()
    val error by vm.authError.collectAsState()
    val notice by vm.authNotice.collectAsState()
    val busy by vm.authBusy.collectAsState()
    val pending by vm.otpPending.collectAsState()
    val resetEmail by vm.resetStep.collectAsState()
    val showForgot by vm.showForgotPassword.collectAsState()
    val resendLeft by vm.resendSecondsLeft.collectAsState()
    val home by vm.home.collectAsState()
    val dark by vm.darkMode.collectAsState()
    var isSignup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var resetEmailInput by remember { mutableStateOf("") }
    // A fresh code field whenever the OTP step changes (after signup is
    // verified the app moves straight to the login code).
    LaunchedEffect(pending) { otp = "" }

    Box(modifier = Modifier.fillMaxSize()) {
        AuthBackground()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp)
                .statusBarsPadding()
        ) {
            // Preview: brand row at y62 (status bar ends ~48), logo 36dp,
            // wordmark 18sp/800. No subtitle: the preview has none, and
            // v7's "PREDICTION MARKET" line pushed everything down.
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoImage(36)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Prognos Labs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.ink,
                    letterSpacing = (-0.4).sp
                )
            }
            // Preview: headline y128 (30px below the brand row), 31sp/800.
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                "Trade the question.",
                fontSize = 31.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 35.sp,
                color = c.ink,
                letterSpacing = (-0.8).sp
            )
            // v9: the supporting line is gone (Flint: awful keyword).
            // 22dp below the headline, straight to the prices card.
            Spacer(modifier = Modifier.height(22.dp))
            PricesCard(home.ticker)
            Spacer(modifier = Modifier.height(24.dp))

            if (resetEmail != null) {
                // Step 3 of a password reset: the code was verified, now
                // set the new password.
                Text(
                    "Set a new password.",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.ink
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "For $resetEmail.",
                    fontSize = 14.sp,
                    color = c.grey
                )
                Spacer(modifier = Modifier.height(16.dp))
                AuthField(
                    label = "New password",
                    value = newPassword,
                    onValueChange = { newPassword = it; vm.clearAuthError() },
                    placeholder = "At least 8 characters",
                    isPassword = true,
                    keyboardType = KeyboardType.Password
                )
                Spacer(modifier = Modifier.height(14.dp))
                AuthField(
                    label = "Confirm new password",
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; vm.clearAuthError() },
                    placeholder = "Repeat the new password",
                    isPassword = true,
                    keyboardType = KeyboardType.Password
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error!!, color = AskRed, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(20.dp))
                val (pressScale, pressSource) = rememberPressScale()
                Button(
                    onClick = { vm.confirmPasswordReset(newPassword, confirmPassword) },
                    enabled = !busy,
                    interactionSource = pressSource,
                    modifier = Modifier.fillMaxWidth().height(56.dp).graphicsLayer(scaleX = pressScale, scaleY = pressScale),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dark) Lime else c.primary,
                        contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                    )
                ) { Text("Save new password", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Back to log in",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable {
                        vm.cancelOtp()
                        vm.clearAuthError()
                        vm.clearAuthNotice()
                    }
                )
            } else if (pending != null) {
                // OTP step: signup verify, login, or password reset.
                val purpose = pending!!.purpose
                Text(
                    "Enter the code.",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.ink
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    when (purpose) {
                        "login" -> "A 6-digit code was sent to ${pending!!.email}. Enter it to sign in."
                        "reset" -> "A 6-digit code was sent to ${pending!!.email}. Enter it to reset your password."
                        else -> "A 6-digit code was sent to ${pending!!.email}. Enter it to verify your account."
                    },
                    fontSize = 14.sp,
                    color = c.grey
                )
                Spacer(modifier = Modifier.height(16.dp))
                AuthField(
                    label = "Code",
                    value = otp,
                    onValueChange = { otp = it.filter { ch -> ch.isDigit() }.take(6); vm.clearAuthError() },
                    placeholder = "6-digit code",
                    keyboardType = KeyboardType.NumberPassword
                )
                if (notice != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(notice!!, color = BidGreen, fontSize = 14.sp)
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error!!, color = AskRed, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(20.dp))
                val (pressScale, pressSource) = rememberPressScale()
                Button(
                    onClick = { vm.verifyOtp(otp) },
                    enabled = !busy && otp.length == 6,
                    interactionSource = pressSource,
                    modifier = Modifier.fillMaxWidth().height(56.dp).graphicsLayer(scaleX = pressScale, scaleY = pressScale),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dark) Lime else c.primary,
                        contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                    )
                ) { Text("Verify", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Spacer(modifier = Modifier.height(16.dp))
                // Resend stays disabled for 60 seconds after a code was
                // sent, and shows the wait. The server caps sends at 3
                // per email per hour and stays silent past that, so the
                // screen must not invite rapid taps.
                if (resendLeft > 0) {
                    Text(
                        formatResendCountdown(resendLeft),
                        fontSize = 14.sp,
                        color = c.grey,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "Send a new code",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { otp = ""; vm.resendOtp() }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Back",
                    fontSize = 14.sp,
                    color = c.grey,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable {
                        otp = ""
                        vm.cancelOtp()
                        vm.clearAuthError()
                    }
                )
            } else if (showForgot) {
                // Forgot password: enter the account email, get a code,
                // then the OTP step (purpose "reset") and a new password.
                Text(
                    "Reset your password.",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.ink
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Enter the email for your account. We will send a 6-digit code.",
                    fontSize = 14.sp,
                    color = c.grey
                )
                Spacer(modifier = Modifier.height(16.dp))
                AuthField(
                    label = "Email",
                    value = resetEmailInput,
                    onValueChange = { resetEmailInput = it; vm.clearAuthError() },
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error!!, color = AskRed, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(20.dp))
                val (pressScale, pressSource) = rememberPressScale()
                Button(
                    onClick = { vm.requestPasswordReset(resetEmailInput) },
                    enabled = !busy,
                    interactionSource = pressSource,
                    modifier = Modifier.fillMaxWidth().height(56.dp).graphicsLayer(scaleX = pressScale, scaleY = pressScale),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dark) Lime else c.primary,
                        contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                    )
                ) { Text("Send reset code", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Back to log in",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable {
                        resetEmailInput = ""
                        vm.hideForgotPassword()
                        vm.clearAuthError()
                        vm.clearAuthNotice()
                    }
                )
            } else {
                // Preview: tabs container at y318.5 (24px below the card).
                AuthTabs(
                    options = listOf("Log in", "Sign up"),
                    selected = if (isSignup) 1 else 0,
                    onSelect = { i -> isSignup = i == 1; vm.clearAuthError(); vm.clearAuthNotice() }
                )
                // Preview: fields at y384.5 and y458.5, 60dp, 14px apart.
                Spacer(modifier = Modifier.height(14.dp))
                if (isSignup) {
                    AuthField(
                        label = "Name",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Your name"
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
                AuthField(
                    label = "Email",
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email
                )
                Spacer(modifier = Modifier.height(14.dp))
                AuthField(
                    label = "Password",
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "At least 8 characters",
                    isPassword = true,
                    keyboardType = KeyboardType.Password
                )
                if (notice != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(notice!!, color = BidGreen, fontSize = 14.sp)
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error!!, color = AskRed, fontSize = 14.sp)
                }
                // Preview: primary button at y538.5 (20px below the last field),
                // 326x56, radius 16.
                Spacer(modifier = Modifier.height(20.dp))
                val (pressScale, pressSource) = rememberPressScale()
                Button(
                    onClick = {
                        if (isSignup) vm.signUp(name, email, password) else vm.logIn(email, password)
                    },
                    enabled = !busy,
                    interactionSource = pressSource,
                    modifier = Modifier.fillMaxWidth().height(56.dp).graphicsLayer(scaleX = pressScale, scaleY = pressScale),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dark) Lime else c.primary,
                        contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                    )
                ) { Text(if (isSignup) "Create account" else "Log in", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                if (!isSignup) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Forgot your password?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { vm.showForgotPassword() }
                    )
                }
                // v9: the "Accounts on this device" note is gone (Flint).
                // Preview: "No account yet? Create one" at y658, 14sp, centred,
                // "Create one" bold with a lime underline. v7 was missing this
                // line entirely, which left a void at the bottom of the screen.
                Spacer(modifier = Modifier.height(16.dp))
                val switchText = if (isSignup) {
                    androidx.compose.ui.text.AnnotatedString.Builder().apply {
                        append("Already have an account? ")
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = c.ink,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            )
                        )
                        append("Log in")
                        pop()
                    }.toAnnotatedString()
                } else {
                    androidx.compose.ui.text.AnnotatedString.Builder().apply {
                        append("No account yet? ")
                        pushStyle(
                            androidx.compose.ui.text.SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = c.ink,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            )
                        )
                        append("Create one")
                        pop()
                    }.toAnnotatedString()
                }
                Text(
                    switchText,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable {
                        isSignup = !isSignup
                        vm.clearAuthError()
                        vm.clearAuthNotice()
                    }
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** Short "X left" countdown for the detail meta row, as the preview
 *  writes it ("3:24 left"): hours and minutes when long, minutes and
 *  seconds when short. */
fun shortTimeLeft(diffSec: Long): String {
    if (diffSec <= 0) return "closed"
    val h = diffSec / 3600
    val m = (diffSec % 3600) / 60
    val s = diffSec % 60
    return if (h > 0) "${h}h ${m}m left" else "$m:${String.format("%02d", s)} left"
}

/**
 * v1.1.8: the market countdown ("3:24 left"), the one text on the
 * detail screen that ticks every second. It collects the per-second
 * clock itself, so only this one line recomposes each second; the
 * rest of the screen reads the per-minute clock and stays still.
 */
@Composable
fun TimeLeftText(vm: AppViewModel, endSec: Long) {
    val nowSec by vm.nowSec.collectAsState()
    val c = appColors()
    Text(
        shortTimeLeft(if (endSec > 0) endSec - nowSec else 0),
        fontSize = 12.sp, fontFamily = PrognosMonoFamily, color = c.grey
    )
}

/**
 * Quick action, exactly as the preview draws it: a 50dp circle with a
 * 12.5sp/600 label below it. Deposit / Withdraw are pale-lime circles
 * with a lock in light mode; in dark mode Deposit is dark olive with a
 * lime lock and Withdraw is near-black with a white lock. Trade is a
 * black circle with lime swap arrows in light mode, lime with black
 * arrows in dark mode. v7 used 56dp grey circles and a chart icon.
 */
@Composable
fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, bg: Color? = null, tint: Color? = null, enabled: Boolean = true) {
    val c = appColors()
    val click = if (enabled) Modifier.clickable { onClick() } else Modifier
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = click) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(if (enabled) (bg ?: c.surface2) else c.surface2, RoundedCornerShape(25.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (enabled) (tint ?: c.ink) else c.grey, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) c.ink else c.grey)
    }
}

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onOpenMarket: (String) -> Unit,
    onBrowseMarkets: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val c = appColors()
    val home by vm.home.collectAsState()
    // v1.1.8: the per-second clock was collected here and never used,
    // so the whole dashboard recomposed every second for nothing.
    val auth by vm.auth.collectAsState()
    val dark by vm.darkMode.collectAsState()
    val alertsOn by vm.alertsEnabled.collectAsState()
    val watchIds by vm.watchlist.ids.collectAsState(initial = emptySet())

    val saved = home.markets.filter { watchIds.contains(it.id) }
    // Preview: exactly 3 mover cards, BTC / ETH / SOL when the ticker has
    // them, otherwise the three biggest movers. v7 scrolled a row of
    // 160dp cards, so only some were ever visible.
    val movers = remember(home.ticker) {
        val preferred = listOf("BTC", "ETH", "SOL").mapNotNull { code ->
            home.ticker.find { it.asset == code }
        }
        if (preferred.size == 3) preferred else topMovers(home.ticker, 3)
    }
    val firstName = auth.name.trim().split(" ").firstOrNull().orEmpty()
    val greeting = greetingForHour(LocalTime.now().hour)
    // The bell dot only shows when price alerts are on and there is
    // actually an alert. Turning alerts off in Settings silences it.
    val hasAlerts = alertsOn && home.flags.isNotEmpty()
    val tickerChg = remember(home.ticker) { home.ticker.mapNotNull { a -> a.chg24?.let { a.asset to it } }.toMap() }
    val balance by vm.balance.collectAsState()
    val positions by vm.positions.collectAsState()
    val currency by vm.displayCurrency.collectAsState()
    val rates by vm.rates.collectAsState()
    val balanceText = fmtMoney(balance, currency, rates)
    // BTC equivalent at the LIVE price from the market ticker, so it
    // moves when the ticker refreshes. Never a hardcoded price.
    val btcPrice = home.ticker.find { it.asset == "BTC" }?.price
    val btcText = btcEquivalentText(balance, btcPrice)

    // v1.1.15: the voice mic lives in the header row of every
    // screen, one small button like the others, never floating
    // over content. The scaffold carries no mic button any more.
    Box(modifier = Modifier.fillMaxSize().background(c.background)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Preview header: NO dark band. Plain background, greeting 13sp
        // grey + name 19sp/800 on the left; bell (40dp bordered circle with
        // an 8dp lime dot), theme toggle (40dp bordered circle) and an
        // avatar circle (40dp, first letter, black bg + lime text light,
        // lime bg + black text dark) on the right. v7's dark band, white
        // text and logout icon do not exist in the preview; log out moved
        // to the bottom of the Wallet screen.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPad, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(greeting, fontSize = 13.sp, color = c.grey)
                Text(
                    if (firstName.isNotBlank()) firstName else "Prognos Labs",
                    fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink
                )
            }
            val (bellScale, bellSource) = rememberPressScale()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, c.border, RoundedCornerShape(20.dp))
                    .graphicsLayer(scaleX = bellScale, scaleY = bellScale)
                    .clickable(interactionSource = bellSource, indication = null) { onOpenNotifications() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = c.ink, modifier = Modifier.size(20.dp))
                if (hasAlerts) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(8.dp)
                            .background(Lime, RoundedCornerShape(4.dp))
                            .border(2.dp, c.background, RoundedCornerShape(4.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            val (themeScale, themeSource) = rememberPressScale()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, c.border, RoundedCornerShape(20.dp))
                    .graphicsLayer(scaleX = themeScale, scaleY = themeScale)
                    .clickable(interactionSource = themeSource, indication = null) { vm.toggleDarkMode() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode",
                    tint = c.ink,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // v1.1.15: the mic is a header button like the bell and
            // the theme toggle, never a floating button over
            // content.
            VoiceMicHeaderButton(onClick = { vm.onMicTapped() })
            Spacer(modifier = Modifier.width(8.dp))
            val (avatarScale, avatarSource) = rememberPressScale()
            // v9: the avatar opens Settings. v8 drew it with no click
            // handler, so tapping it did nothing on Flint's phone.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (dark) Lime else Color(0xFF111111),
                        RoundedCornerShape(20.dp)
                    )
                    .graphicsLayer(scaleX = avatarScale, scaleY = avatarScale)
                    .clickable(onClickLabel = "Settings", interactionSource = avatarSource, indication = null) { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (firstName.take(1).ifBlank { "P" }),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (dark) Color(0xFF111111) else Lime
                )
            }
        }
        // v1.1.14: the spinner shows only when there is no data at
        // all. The ticker lands first and carries the top movers,
        // so a dashboard with the ticker in shows the movers at
        // once while the markets list still loads.
        if (dashboardShowsSpinner(home.loading, home.markets.isEmpty(), home.ticker.isEmpty())) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.ink)
            }
            return@Column
        }
        if (home.error && home.markets.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Markets could not be loaded. Check your connection and try again.", textAlign = TextAlign.Center, color = c.ink)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { vm.refreshAll() }, colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)) {
                    Text("Try again")
                }
            }
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPad)) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                BalanceCard(
                    title = "Total balance",
                    note = "Deposits open with trading.",
                    balanceText = balanceText,
                    btcText = btcText,
                    onDeposit = { vm.openTradingSite() },
                    onWithdraw = { vm.openTradingSite() }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickAction(
                        Icons.Filled.ArrowDownward, "Deposit", { vm.openTradingSite() }, enabled = TradingLock.depositsOpen,
                        bg = if (dark) Color(0xFF232B12) else Color(0xFFF1F6DE),
                        tint = if (dark) Lime else c.grey
                    )
                    QuickAction(
                        Icons.Filled.ArrowUpward, "Withdraw", { vm.openTradingSite() }, enabled = TradingLock.withdrawalsOpen,
                        bg = if (dark) Color(0xFF1C1C1C) else Color(0xFFF1F6DE),
                        tint = if (dark) Color.White else c.grey
                    )
                    QuickAction(
                        Icons.Filled.SwapHoriz, "Trade", onBrowseMarkets,
                        bg = if (dark) Lime else Color(0xFF111111),
                        tint = if (dark) Color(0xFF111111) else Lime
                    )
                }
                Spacer(modifier = Modifier.height(SectionGap))
            }
            item {
                SectionHeader("Your positions")
                Spacer(modifier = Modifier.height(8.dp))
                if (positions.isNotEmpty()) {
                    for (pos in positions.take(3)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pos.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                                if (pos.detail.isNotBlank()) {
                                    Text(pos.detail, fontSize = 13.sp, color = c.grey)
                                }
                            }
                        }
                        HorizontalDivider(color = c.border)
                    }
                } else {
                    HorizontalEmptyState(
                        Icons.Filled.ShowChart,
                        "No open positions yet",
                        "Your positions will show here.",
                        "Browse markets",
                        onBrowseMarkets
                    )
                }
                Spacer(modifier = Modifier.height(SectionGap))
            }
            if (movers.isNotEmpty()) {
                item {
                    SectionHeader("Top movers", "See all", onBrowseMarkets)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (a in movers) {
                            Box(modifier = Modifier.weight(1f)) {
                                MoverCard(a) { onBrowseMarkets() }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(SectionGap))
                }
            }
            item {
                SectionHeader("Watchlist", "See all", onBrowseMarkets)
                Spacer(modifier = Modifier.height(4.dp))
                if (saved.isEmpty()) {
                    DesignedEmptyState(
                        Icons.Outlined.StarOutline,
                        "No markets saved yet",
                        "Tap the star on a market to keep it here.",
                        "Browse markets",
                        onBrowseMarkets
                    )
                } else {
                    for (m in saved.take(3)) {
                        WatchlistRow(
                            market = m,
                            starred = true,
                            onStar = { vm.toggleWatch(m.id) },
                            onClick = { onOpenMarket(m.id) },
                            chg24 = tickerChg[m.asset]
                        )
                        HorizontalDivider(color = c.border)
                    }
                }
                Spacer(modifier = Modifier.height(SectionGap))
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
    }
}

@Composable
fun MarketsScreen(vm: AppViewModel, onOpenMarket: (String) -> Unit) {
    val c = appColors()
    val home by vm.home.collectAsState()
    // v1.1.8: the per-second clock was collected here and passed to
    // every market row, which never used it, so the whole list
    // recomposed every second for nothing. Rows now read the
    // per-minute clock only where a time is shown.
    val watchIds by vm.watchlist.ids.collectAsState(initial = emptySet())
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(MarketFilter.ALL) }
    var sector by remember { mutableStateOf("all") }
    var sectorMenu by remember { mutableStateOf(false) }
    var tabIdx by remember { mutableStateOf(0) }
    var shown by remember { mutableStateOf(PAGE_SIZE) }
    LaunchedEffect(query, filter, sector) { shown = PAGE_SIZE }

    val sectors = remember(home.markets) {
        val counts = linkedMapOf<String, Pair<String, Int>>()
        for (m in home.markets) {
            val key = m.sectorKey
            val cur = counts[key]
            counts[key] = Pair(m.sectorLabel, (cur?.second ?: 0) + 1)
        }
        counts.entries.sortedByDescending { it.value.second }
            .map { Triple(it.key, it.value.first, it.value.second) }
    }
    val tickerChg = remember(home.ticker) { home.ticker.mapNotNull { a -> a.chg24?.let { a.asset to it } }.toMap() }
    // The preview's "Ending soon" is a TAB, not a sort dropdown: the list
    // is always ordered by what ends soonest. v7 added a sort dropdown
    // the preview does not have, and a "Markets" title row, a refresh icon
    // and a scrolling ticker strip it does not have either. All gone.
    val visible = remember(home.markets, query, filter, sector, watchIds, tickerChg) {
        xyz.prognoslabs.app.data.sortMarkets(
            filterMarkets(home.markets, query, filter, sector, watchIds, tickerChg),
            xyz.prognoslabs.app.data.MarketSort.ENDING_SOON
        )
    }
    val displayed = remember(visible, shown) { pageMarkets(visible, shown) }
    val sectorLabel = if (sector == "all") "All assets" else sectors.find { it.first == sector }?.second ?: "All assets"

    Box(modifier = Modifier.fillMaxSize().background(c.background)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // v1.1.15: the mic is a header button in the layout, never
        // a floating button over the rows. The header sits above
        // the loading and error gates, like the dashboard header,
        // so the mic is there even while the list loads.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPad).padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Markets", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = c.ink, modifier = Modifier.weight(1f))
            VoiceMicHeaderButton(onClick = { vm.onMicTapped() })
        }
        if (home.loading && home.markets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.ink)
            }
            return@Column
        }
        if (home.error && home.markets.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Markets could not be loaded. Check your connection and try again.", textAlign = TextAlign.Center, color = c.ink)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { vm.refreshAll() }, colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)) {
                    Text("Try again")
                }
            }
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPad)) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                // Preview: search field, bg #F4F4F0, radius 14, min-height 44.
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search markets", color = c.grey) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = c.grey) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = c.surface2,
                        focusedContainerColor = c.surface2,
                        unfocusedBorderColor = c.border,
                        focusedBorderColor = c.border
                    ),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Preview: wrapped rows of outlined pills. "Ending soon" is
                // the last pill, not a dropdown.
                FilterPills(
                    options = listOf("All", "Gainers", "Losers", "Watchlist", "Ending soon"),
                    selected = tabIdx,
                    onSelect = { i ->
                        tabIdx = i
                        when (i) {
                            0 -> filter = MarketFilter.ALL
                            1 -> filter = MarketFilter.GAINERS
                            2 -> filter = MarketFilter.LOSERS
                            3 -> filter = MarketFilter.WATCHLIST
                            4 -> filter = MarketFilter.ALL
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Preview: "All assets" dropdown pill + "N markets" text.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        OutlinedButton(
                            onClick = { sectorMenu = true },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.height(38.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Text(sectorLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = c.ink)
                        }
                        DropdownMenu(expanded = sectorMenu, onDismissRequest = { sectorMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("All assets (${home.markets.size})") },
                                onClick = { sector = "all"; sectorMenu = false }
                            )
                            for ((key, label, n) in sectors) {
                                DropdownMenuItem(
                                    text = { Text("$label ($n)") },
                                    onClick = { sector = key; sectorMenu = false }
                                )
                            }
                        }
                    }
                    Text(
                        "${visible.size} markets",
                        fontSize = 13.sp,
                        color = c.grey
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (displayed.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    DesignedEmptyState(
                        Icons.Filled.Search,
                        "No markets match",
                        "Try a different search or filter."
                    )
                }
            } else {
                items(displayed, key = { it.id }) { m ->
                    MarketRow(
                        market = m,
                        starred = watchIds.contains(m.id),
                        onStar = { vm.toggleWatch(m.id) },
                        onClick = { onOpenMarket(m.id) },
                        chg24 = tickerChg[m.asset]
                    )
                    HorizontalDivider(color = c.border)
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (remainingCount(visible.size, shown) > 0) {
                        OutlinedButton(
                            onClick = { shown += PAGE_SIZE },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Show more (${remainingCount(visible.size, shown)} more)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (visible.size > PAGE_SIZE) {
                        Text(
                            "Showing all ${visible.size} markets.",
                            fontSize = 13.sp,
                            color = c.grey,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
    }
}

@Composable
fun PortfolioScreen(vm: AppViewModel, onBrowseMarkets: () -> Unit) {
    val c = appColors()
    val home by vm.home.collectAsState()
    val balance by vm.balance.collectAsState()
    val positions by vm.positions.collectAsState()
    val currency by vm.displayCurrency.collectAsState()
    val rates by vm.rates.collectAsState()
    val balanceText = fmtMoney(balance, currency, rates)
    val btcPrice = home.ticker.find { it.asset == "BTC" }?.price
    val btcText = btcEquivalentText(balance, btcPrice)

    Column(
        modifier = Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(horizontal = ScreenPad)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        // Preview: title ~22px, not 28sp.
        // v1.1.15: the mic is a header button in the layout,
        // never a floating button over content.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Portfolio", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
            VoiceMicHeaderButton(onClick = { vm.onMicTapped() })
        }
        Spacer(modifier = Modifier.height(16.dp))
        BalanceCard(
            title = "Total balance",
            note = "Deposits open with trading.",
            balanceText = balanceText,
            btcText = btcText,
            onDeposit = { vm.openTradingSite() },
            onWithdraw = { vm.openTradingSite() }
        )
        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader("Performance")
        Spacer(modifier = Modifier.height(12.dp))
        // Preview: 3-column P&L card, labels 12.5sp/600 grey, values 16sp
        // bold. No mono caps, and NO "Saved markets" row below it: v7
        // invented that row, the preview does not have it.
        PnlCard()
        Spacer(modifier = Modifier.height(SectionGap))
        SectionHeader("Holdings")
        Spacer(modifier = Modifier.height(8.dp))
        // Preview: HORIZONTAL empty states (icon left, text middle, action
        // right), with the preview's copy.
        HorizontalEmptyState(
            Icons.Filled.AccountBalanceWallet,
            "No holdings yet",
            "Your assets will show here once you deposit.",
            "Deposit",
            { vm.openTradingSite() },
            actionEnabled = TradingLock.depositsOpen,
            actionPrimary = false
        )
        Spacer(modifier = Modifier.height(SectionGap))
        SectionHeader("Positions")
        Spacer(modifier = Modifier.height(8.dp))
        if (positions.isNotEmpty()) {
            for (pos in positions) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pos.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                        if (pos.detail.isNotBlank()) {
                            Text(pos.detail, fontSize = 13.sp, color = c.grey)
                        }
                    }
                }
                HorizontalDivider(color = c.border)
            }
        } else {
            HorizontalEmptyState(
                Icons.Filled.ShowChart,
                "No open positions yet",
                "Your positions will show here.",
                "Browse markets",
                onBrowseMarkets
            )
        }
        Spacer(modifier = Modifier.height(SectionGap))
        SectionHeader("History")
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalEmptyState(
            Icons.Filled.History,
            "No history yet",
            "Settled markets will show here."
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun NotificationsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = appColors()
    val home by vm.home.collectAsState()
    val alertsOn by vm.alertsEnabled.collectAsState()
    val flags = home.flags

    Column(modifier = Modifier.fillMaxSize().background(c.background).padding(horizontal = ScreenPad)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = c.ink) }
            Text("Notifications", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
            // v1.1.15: the mic is a header button in the layout,
            // never a floating button over content.
            VoiceMicHeaderButton(onClick = { vm.onMicTapped() })
        }
        Spacer(modifier = Modifier.height(16.dp))
        // v9: the price-alerts toggle in Settings is real. When alerts
        // are off, this screen says so instead of showing old alerts.
        if (!alertsOn) {
            DesignedEmptyState(
                Icons.Filled.Notifications,
                "Price alerts are off",
                "Turn price alerts back on in Settings."
            )
        } else if (flags.isEmpty()) {
            DesignedEmptyState(
                Icons.Filled.Notifications,
                "No alerts yet",
                "Price alerts will appear here."
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(flags, key = { (it.marketId ?: "") + (it.kind ?: "") + (it.reason ?: "") }) { f ->
                    val market = home.markets.find { it.id == f.marketId }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = c.surface),
                        shape = RoundedCornerShape(CardRadius),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                (f.kind ?: "Alert").replaceFirstChar { it.uppercase() },
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink
                            )
                            if (market != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(market.question, fontSize = 14.sp, color = c.ink)
                            }
                            if (!f.reason.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(f.reason, fontSize = 13.sp, color = c.grey)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Settings, opened from the profile avatar on the Dashboard (v9).
 *  Profile, Authentication (change password + sign out), Appearance
 *  (dark mode), Notifications (price-alerts toggle), About. Same card
 *  style, spacing and fonts as the rest of the app. */
/**
 * v1.1.14: the four account settings that live as compact drop-down
 * rows in Settings, in order. Each row is one small header that
 * expands in place; the content behind it is unchanged.
 */
enum class SettingsRowId(val title: String) {
    WALLET("Link wallet"),
    CHANGE_PASSWORD("Change password"),
    CURRENCY("Currency"),
    TRADE_SIZE("Default trade size")
}

/** The four drop-down rows, in the order Settings shows them. */
fun settingsDropDownRows(): List<SettingsRowId> = SettingsRowId.entries.toList()

/**
 * One compact settings row: a single header line with the setting
 * name, its current value and a chevron, expanding in place to the
 * setting itself. Closed it is one short row; open it shows only
 * that setting, never a large menu block.
 */
@Composable
fun SettingsDropDownRow(
    row: SettingsRowId,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val c = appColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.surface),
        shape = RoundedCornerShape(CardRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                Spacer(modifier = Modifier.weight(1f))
                Text(summary, fontSize = 14.sp, color = c.grey)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = c.grey,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = if (expanded) 180f else 0f }
                )
            }
            if (expanded) {
                HorizontalDivider(color = c.border)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenVoiceEnroll: () -> Unit = {},
    onOpenChangePassword: () -> Unit = {}
) {
    val c = appColors()
    val auth by vm.auth.collectAsState()
    val dark by vm.darkMode.collectAsState()
    val voiceEnrolled by vm.voiceEnrolled.collectAsState()
    val alertsOn by vm.alertsEnabled.collectAsState()
    val link by vm.link.collectAsState()
    val currency by vm.displayCurrency.collectAsState()
    val defaultAmount by vm.defaultTradeAmount.collectAsState()
    val feedbackOptIn by vm.voiceFeedbackOptIn.collectAsState()
    val voiceLanguage by vm.voiceLanguage.collectAsState()
    val crashReport by vm.lastCrashReport.collectAsState()
    var showLanguagePicker by remember { mutableStateOf(false) }
    var crashCopied by remember { mutableStateOf(false) }
    var tradeSizeInput by remember(defaultAmount) { mutableStateOf(defaultAmount) }
    var expandedRow by remember { mutableStateOf<SettingsRowId?>(null) }

    val initial = (auth.name.trim().take(1).ifBlank { auth.email.trim().take(1) }.ifBlank { "P" }).uppercase()
    val displayName = auth.name.trim().ifBlank { "Prognos Labs" }

    Column(modifier = Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(horizontal = ScreenPad)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp)
                    .border(1.dp, c.border, RoundedCornerShape(20.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = c.ink, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
            // v1.1.15: the mic is a header button in the layout,
            // never a floating button over the voice toggle.
            Spacer(modifier = Modifier.weight(1f))
            VoiceMicHeaderButton(onClick = { vm.onMicTapped() })
        }
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader("Profile")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = c.surface),
            shape = RoundedCornerShape(CardRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (dark) Lime else Color(0xFF111111),
                            RoundedCornerShape(28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initial,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (dark) Color(0xFF111111) else Lime
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(auth.email, fontSize = 14.sp, color = c.grey)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = c.surface),
            shape = RoundedCornerShape(CardRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Sign out",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AskRed,
                    modifier = Modifier.clickable { vm.logOut() }
                )
            }
        }
        Spacer(modifier = Modifier.height(SectionGap))

        // v1.1.14: wallet, change password, currency and default trade
        // size are small compact drop-down rows that expand in place,
        // not large menu blocks. One row open at a time.
        SectionHeader("Account")
        Spacer(modifier = Modifier.height(8.dp))
        val linkedShort = link.linkedAddress?.let {
            if (it.length > 12) it.take(6) + "..." + it.takeLast(4) else it
        }
        SettingsDropDownRow(
            row = SettingsRowId.WALLET,
            summary = linkedShort ?: "Not linked",
            expanded = expandedRow == SettingsRowId.WALLET,
            onToggle = { expandedRow = if (expandedRow == SettingsRowId.WALLET) null else SettingsRowId.WALLET }
        ) {
            if (link.linkedAddress != null) {
                Text("Linked wallet", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                Spacer(modifier = Modifier.height(2.dp))
                Text(linkedShort ?: "", fontSize = 13.sp, fontFamily = PrognosMonoFamily, color = c.grey)
                Spacer(modifier = Modifier.height(12.dp))
            }
            AuthField(
                label = "Wallet address",
                value = link.address,
                onValueChange = { vm.setLinkAddress(it) },
                placeholder = "0x..."
            )
            if (link.message != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    link.message!!,
                    color = if (link.isError) AskRed else BidGreen,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { vm.startWalletLink() },
                enabled = !link.checking,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (dark) Lime else c.primary,
                    contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                )
            ) { Text(if (link.linkedAddress != null) "Change wallet" else "Link wallet", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.checkWalletLink() },
                enabled = !link.checking,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink)
            ) { Text("Check link", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SettingsDropDownRow(
            row = SettingsRowId.CHANGE_PASSWORD,
            summary = "Code by email",
            expanded = expandedRow == SettingsRowId.CHANGE_PASSWORD,
            onToggle = { expandedRow = if (expandedRow == SettingsRowId.CHANGE_PASSWORD) null else SettingsRowId.CHANGE_PASSWORD }
        ) {
            // The request flow itself lives on its own screen: a code
            // goes to the signed-in email, then the code step, then
            // the new password. It is never a tab on this page.
            Text(
                "A code goes to ${auth.email}. Enter it, then set your new password.",
                fontSize = 14.sp,
                color = c.grey
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onOpenChangePassword() },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (dark) Lime else c.primary,
                    contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                )
            ) { Text("Change password", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SettingsDropDownRow(
            row = SettingsRowId.CURRENCY,
            summary = currency.label,
            expanded = expandedRow == SettingsRowId.CURRENCY,
            onToggle = { expandedRow = if (expandedRow == SettingsRowId.CURRENCY) null else SettingsRowId.CURRENCY }
        ) {
            for (opt in DisplayCurrency.entries) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        expandedRow = null
                        vm.setDisplayCurrency(opt.code)
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(opt.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Spacer(modifier = Modifier.weight(1f))
                    if (opt == currency) {
                        Text("Selected", fontSize = 13.sp, color = BidGreen)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SettingsDropDownRow(
            row = SettingsRowId.TRADE_SIZE,
            summary = "$" + defaultAmount,
            expanded = expandedRow == SettingsRowId.TRADE_SIZE,
            onToggle = { expandedRow = if (expandedRow == SettingsRowId.TRADE_SIZE) null else SettingsRowId.TRADE_SIZE }
        ) {
            AuthField(
                label = "Amount (USD)",
                value = tradeSizeInput,
                onValueChange = { tradeSizeInput = sanitizeAmountInput(it); vm.clearSettingsMessage() },
                placeholder = "10.00",
                keyboardType = KeyboardType.Decimal
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { vm.setDefaultTradeAmount(tradeSizeInput) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (dark) Lime else c.primary,
                    contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                )
            ) { Text("Save default size", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        Spacer(modifier = Modifier.height(SectionGap))

        // Voice trading (v1.1.7): enrollment, voice lock, history.
        SectionHeader("Voice")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = c.surface),
            shape = RoundedCornerShape(CardRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = { vm.startEnrollment(); onOpenVoiceEnroll() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dark) Lime else c.primary,
                        contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                    )
                ) { Text(if (voiceEnrolled) "Re-do voice setup" else "Set up your voice", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { showLanguagePicker = true }
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Column(modifier = Modifier.weight(1f)) {
        Text("Voice language", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            voiceLanguage.displayName + if (voiceLanguage.code == "ig") " (On-device)" else " (Cloud ASR)",
            fontSize = 13.sp,
            color = c.grey
        )
    }
    Text("Change", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (dark) Lime else c.ink)
}

if (showLanguagePicker) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { showLanguagePicker = false },
        title = { Text("Voice language", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                xyz.prognoslabs.app.data.SUPPORTED_VOICE_LANGUAGES.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.setVoiceLanguage(lang)
                                showLanguagePicker = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lang.displayName,
                            fontSize = 15.sp,
                            fontWeight = if (lang == voiceLanguage) FontWeight.Bold else FontWeight.Normal,
                            color = c.ink,
                            modifier = Modifier.weight(1f)
                        )
                        if (lang == voiceLanguage) {
                            Text("Selected", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Lime)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { showLanguagePicker = false }) {
                Text("Close")
            }
        }
    )
}
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = c.border)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Help improve voice", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "When on, the app sends back only the text of phrases the voice did not understand, so the voice can be improved. No audio, no name, nothing that identifies you.",
                            fontSize = 13.sp,
                            color = c.grey
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = feedbackOptIn,
                        onCheckedChange = { vm.setVoiceFeedbackOptIn(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF111111),
                            checkedTrackColor = Lime,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = c.grey2
                        )
                    )
                }
                if (voiceEnrolled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Remove voice setup",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AskRed,
                        modifier = Modifier.clickable { vm.resetVoiceEnrollment() }
                    )
                }
                // v1.1.21: when the app died on an earlier launch,
                // the saved crash report shows here so it can be
                // copied out and sent to us. Nothing is sent on its own.
                val report = crashReport
                if (report != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = c.border)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Last crash report", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "The app closed unexpectedly once. Copy this report and send it to us so the crash can be fixed.",
                        fontSize = 13.sp,
                        color = c.grey
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val clipboard = LocalClipboardManager.current
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(report))
                                crashCopied = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (dark) Lime else c.primary,
                                contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                            )
                        ) { Text(if (crashCopied) "Copied" else "Copy report", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Clear",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AskRed,
                            modifier = Modifier.clickable {
                                vm.clearCrashReport()
                                crashCopied = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(SectionGap))

        SectionHeader("Appearance")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = c.surface),
            shape = RoundedCornerShape(CardRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark mode", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = dark,
                    onCheckedChange = { vm.toggleDarkMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF111111),
                        checkedTrackColor = Lime,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = c.grey2
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(SectionGap))

        SectionHeader("Notifications")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = c.surface),
            shape = RoundedCornerShape(CardRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Price alerts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Get alerted when a market moves.", fontSize = 13.sp, color = c.grey)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = alertsOn,
                    onCheckedChange = { vm.setAlertsEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF111111),
                        checkedTrackColor = Lime,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = c.grey2
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(SectionGap))

        SectionHeader("About")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = c.surface),
            shape = RoundedCornerShape(CardRadius),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Prognos Labs", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Change password, as its own request flow (v1.1.12: no longer a
 * section on the profile page). Step 1: a code goes to the
 * signed-in email. Step 2: the code. Step 3: the new password.
 * Leaving the flow clears the code state.
 */
@Composable
fun ChangePasswordScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = appColors()
    val dark by vm.darkMode.collectAsState()
    val auth by vm.auth.collectAsState()
    val pending by vm.otpPending.collectAsState()
    val resetEmail by vm.resetStep.collectAsState()
    val message by vm.settingsMessage.collectAsState()
    val isError by vm.settingsMessageIsError.collectAsState()
    val busy by vm.authBusy.collectAsState()
    val done by vm.changePasswordDone.collectAsState()
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { vm.clearChangePasswordFlow() } }

    Column(modifier = Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(horizontal = ScreenPad)) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp)
                    .border(1.dp, c.border, RoundedCornerShape(20.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = c.ink, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Change password", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
            // v1.1.15: the mic is a header button in the layout,
            // never a floating button over content.
            Spacer(modifier = Modifier.weight(1f))
            VoiceMicHeaderButton(onClick = { vm.onMicTapped() })
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (done) {
            Text("Password changed.", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Use your new password next time you sign in.",
                fontSize = 14.sp,
                color = c.grey
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { onBack() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (dark) Lime else c.primary,
                    contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                )
            ) { Text("Done", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        } else if (resetEmail != null) {
            // Step 3: the code was verified, now set the new password.
            Text("Set a new password.", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(modifier = Modifier.height(6.dp))
            Text("For $resetEmail.", fontSize = 14.sp, color = c.grey)
            Spacer(modifier = Modifier.height(16.dp))
            AuthField(
                label = "New password",
                value = newPassword,
                onValueChange = { newPassword = it; vm.clearSettingsMessage() },
                placeholder = "At least 8 characters",
                isPassword = true,
                keyboardType = KeyboardType.Password
            )
            Spacer(modifier = Modifier.height(14.dp))
            AuthField(
                label = "Confirm new password",
                value = confirmPassword,
                onValueChange = { confirmPassword = it; vm.clearSettingsMessage() },
                placeholder = "Repeat the new password",
                isPassword = true,
                keyboardType = KeyboardType.Password
            )
            if (message != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(message!!, color = if (isError) AskRed else BidGreen, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { vm.saveNewPasswordAfterChange(newPassword, confirmPassword) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (dark) Lime else c.primary,
                    contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                )
            ) { Text("Save new password", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        } else if (pending?.purpose == "reset") {
            // Step 2: the code from the email.
            Text("Enter the code.", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "A 6-digit code was sent to ${pending!!.email}. Enter it to change your password.",
                fontSize = 14.sp,
                color = c.grey
            )
            Spacer(modifier = Modifier.height(16.dp))
            AuthField(
                label = "Code",
                value = code,
                onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(6); vm.clearSettingsMessage() },
                placeholder = "6-digit code",
                keyboardType = KeyboardType.NumberPassword
            )
            if (message != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(message!!, color = if (isError) AskRed else BidGreen, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { vm.verifyChangePasswordCode(code) },
                enabled = !busy && code.length == 6,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (dark) Lime else c.primary,
                    contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                )
            ) { Text("Verify code", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Send a new code",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { code = ""; vm.requestChangePasswordCode() }
            )
        } else {
            // Step 1: request the code.
            Text("Change your password.", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "We will send a 6-digit code to ${auth.email}. Enter it, then set your new password.",
                fontSize = 14.sp,
                color = c.grey
            )
            if (message != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(message!!, color = if (isError) AskRed else BidGreen, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { vm.requestChangePasswordCode() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (dark) Lime else c.primary,
                    contentColor = if (dark) Color(0xFF111111) else c.onPrimary
                )
            ) { Text("Send code", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun MarketDetailScreen(vm: AppViewModel, marketId: String, onBack: () -> Unit) {
    val c = appColors()
    val detail by vm.detail.collectAsState()
    val home by vm.home.collectAsState()
    // v1.1.8: the detail screen reads the per-minute clock. The
    // forecast text only changes once a minute, so the forecast card
    // recomputes at most once a minute instead of every second. The
    // countdown text below reads the per-second clock through its own
    // small composable, so only that one line ticks each second.
    val nowMinute by vm.nowMinute.collectAsState()
    val tradeSides by vm.tradeSides.collectAsState()
    val tradingState by vm.tradingState.collectAsState()
    val tradeMessage by vm.tradeMessage.collectAsState()
    val tradeIsError by vm.tradeMessageIsError.collectAsState()
    val tradeBusy by vm.tradeBusy.collectAsState()
    val market = detail.market
    val defaultAmount by vm.defaultTradeAmount.collectAsState()
    // The amount starts at the default trade size from Settings, not a
    // hardcoded number. It is local state after that, so typing in it
    // does not rewrite the preference.
    var amount by remember(defaultAmount) { mutableStateOf(defaultAmount) }

    Box(modifier = Modifier.fillMaxSize().background(c.background)) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPad)) {
        Spacer(modifier = Modifier.height(12.dp))
        // Preview: back button in a circle outline + "Markets" grey text.
        // No star: the preview has none.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp)
                    .border(1.dp, c.border, RoundedCornerShape(20.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = c.ink, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Markets", fontSize = 15.sp, color = c.grey)
            // v1.1.15: the mic is a header button in the layout,
            // never a floating button over content.
            Spacer(modifier = Modifier.weight(1f))
            VoiceMicHeaderButton(onClick = { vm.onMicTapped() })
        }
        if (detail.loading && market == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.ink)
            }
            return@Column
        }
        if (market == null) {
            Text("This market could not be loaded.", color = c.ink)
            return@Column
        }
        // The picked side stays picked until the other side is tapped;
        // the pick lives in the ViewModel, not in this screen, so it
        // survives leaving the screen and coming back.
        val selectedSide = tradeSides[market.id] ?: TradeSide.UP
        val price = if (selectedSide == TradeSide.UP) market.p0 else market.p1
        val tokenId = if (selectedSide == TradeSide.UP) market.outcomes[0].tokenId else market.outcomes[1].tokenId
        val asset = home.ticker.find { it.asset == market.asset }

        Spacer(modifier = Modifier.height(16.dp))
        Text(market.question, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 27.sp, color = c.ink)
        Spacer(modifier = Modifier.height(8.dp))
        // v1.1.4: real asset logo + "Bitcoin · BTC" + green dot + "3:24 left".
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssetLogo(market.asset, sizeDp = 24)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "${market.asset?.let { ASSET_LABELS[it] ?: it } ?: "Market"} · ${market.asset ?: ""}",
                fontSize = 13.sp, color = c.grey
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.size(8.dp).background(BidGreen, RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.width(6.dp))
            TimeLeftText(vm, market.end)
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Preview: big asset price + change pill. v7 showed the two outcome
        // prices as huge numbers instead.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtTickerPrice(asset?.price), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
            Spacer(modifier = Modifier.width(10.dp))
            ChangePill(asset?.chg24)
        }
        Spacer(modifier = Modifier.height(16.dp))
        ForecastCard(
            market, nowMinute * 60,
            buildForecastContext(market, home.ticker, home.news, detail.bookUp, detail.bookDown, detail.chart)
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Chart, no card border: "Win line" label, dashed win line, lime
        // fill, green end dot.
        val chart = detail.chart
        if (chart != null && chart.available == true && !chart.points.isNullOrEmpty()) {
            if (chart.line != null) {
                Text("Win line", fontSize = 12.sp, fontFamily = PrognosMonoFamily, color = c.grey)
                Spacer(modifier = Modifier.height(6.dp))
            }
            PriceChart(points = chart.points, line = chart.line)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Average ${fmtUsd(chart.average)}", fontSize = 12.sp, fontFamily = PrognosMonoFamily, color = c.grey)
                Text(
                    "Now ${fmtUsd(chart.current)}" + if (chart.line != null && chart.current != null) {
                        if (chart.current >= chart.line) ", above the win line" else ", below the win line"
                    } else "",
                    fontSize = 12.sp, fontFamily = PrognosMonoFamily, color = c.grey
                )
            }
        } else {
            DesignedEmptyState(
                Icons.Filled.ShowChart,
                "Chart not available yet",
                "The chart shows the last hour of price once the market has quotes."
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader("Order book")
        Spacer(modifier = Modifier.height(12.dp))
        OrderBookTable(detail.bookUp, detail.bookDown)
        Spacer(modifier = Modifier.height(24.dp))
        // Buy Up GREEN / Sell Down RED segmented control, inline.
        // The picked side keeps its colour until the other side is
        // tapped. v7 used lime + red buttons and opened a bottom sheet.
        // Deleted.
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(c.surface2, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (side in TradeSide.values()) {
                val selected = side == selectedSide
                val label = if (side == TradeSide.UP) "Buy Up" else "Sell Down"
                Box(
                    modifier = Modifier.weight(1f)
                        .background(
                            if (selected) {
                                if (side == TradeSide.UP) BidGreen else AskRed
                            } else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { vm.selectTradeSide(market.id, side) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (selected) Color.White else c.ink
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Preview: Amount row, label left, value right.
        Box(
            modifier = Modifier.fillMaxWidth()
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Amount", fontSize = 14.sp, color = c.grey)
                Spacer(modifier = Modifier.weight(1f))
                // v9: the "$" sits directly next to the amount as one
                // right-aligned unit. v8 gave the value field a fixed
                // 90dp width, so the "$" sat ~45dp away from the digits.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Spacer(modifier = Modifier.width(4.dp))
                    BasicTextField(
                        value = amount,
                        onValueChange = { amount = sanitizeAmountInput(it) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = PrognosFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.End),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(c.ink),
                        modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 48.dp)
                    )
                }
            }
        }
        val amt = amount.toDoubleOrNull()
        if (amt != null && price != null && price > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "About ${String.format("%,.0f", amt / price)} shares at ${fmtPrice(price)}.",
                fontSize = 13.sp, color = c.grey, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // The trade button: grey and locked while trading is closed,
        // green for Buy Up / red for Sell Down once it opens. Both
        // sides buy the chosen outcome's token for the amount, exactly
        // as the website trade panel does.
        val tradingReady = tradingState is TradingState.Ready
        Button(
            onClick = { if (tradingReady) vm.requestTappedOrder(market.id, tokenId, selectedSide, amount) },
            enabled = tradingReady && !tradeBusy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedSide == TradeSide.UP) BidGreen else AskRed,
                contentColor = Color.White,
                disabledContainerColor = if (c.isDark) Color(0xFF2A2A2A) else Color(0xFFE5E7EB),
                disabledContentColor = c.grey
            )
        ) {
            Text(
                when {
                    tradeBusy -> "Placing order..."
                    tradingReady -> if (selectedSide == TradeSide.UP) "Buy Up" else "Sell Down"
                    else -> AppConfig.TRADING_OPENS_SOON
                },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        if (tradeMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                tradeMessage!!,
                color = if (tradeIsError) AskRed else BidGreen,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
    }
}

@Composable
fun WalletScreen(vm: AppViewModel) {
    val c = appColors()
    val home by vm.home.collectAsState()
    val balance by vm.balance.collectAsState()
    val currency by vm.displayCurrency.collectAsState()
    val rates by vm.rates.collectAsState()
    val balanceText = fmtMoney(balance, currency, rates)
    val btcPrice = home.ticker.find { it.asset == "BTC" }?.price
    val btcText = btcEquivalentText(balance, btcPrice)
    val link by vm.link.collectAsState()
    val tradingState by vm.tradingState.collectAsState()
    WalletBackground {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPad)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            // Preview: title ~22px, not 28sp.
            // v1.1.15: the mic is a header button in the layout,
            // never a floating button over content.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Wallet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
                VoiceMicHeaderButton(onClick = { vm.onMicTapped() })
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Preview: balance card with NO buttons inside it.
            BalanceCard(
                title = "Wallet balance",
                note = null,
                showButtons = false,
                balanceText = balanceText,
                btcText = btcText
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Preview: Deposit / Withdraw as two separate grey pills BELOW
            // the card, with locks, plus a centred note.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                for (label in listOf("Deposit", "Withdraw")) {
                    Box(
                        modifier = Modifier.weight(1f)
                            .background(if (c.isDark) Color(0xFF1C1C1C) else Color(0xFFEFEFEC), RoundedCornerShape(14.dp))
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = c.grey, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.grey)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Deposits and withdrawals open with trading.",
                fontSize = 13.sp, color = c.grey, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(SectionGap))
            SectionHeader("Wallet link")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = c.surface),
                shape = RoundedCornerShape(CardRadius),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    if (link.linkedAddress != null) {
                        val short = link.linkedAddress!!.let {
                            if (it.length > 12) it.take(6) + "..." + it.takeLast(4) else it
                        }
                        Text("Wallet linked", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(short, fontSize = 14.sp, fontFamily = PrognosMonoFamily, color = c.grey)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (tradingState is TradingState.Ready) "Trading is open." else "Trading in the app opens soon.",
                            fontSize = 13.sp, color = c.grey
                        )
                    } else {
                        Text("Link your wallet to start trading.", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Linking is a free signature in your own wallet app. Nothing is deposited yet.",
                            fontSize = 13.sp, color = c.grey
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AuthField(
                            label = "Wallet address",
                            value = link.address,
                            onValueChange = { vm.setLinkAddress(it) },
                            placeholder = "0x..."
                        )
                        if (link.message != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                link.message!!,
                                color = if (link.isError) AskRed else BidGreen,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { vm.startWalletLink() },
                            enabled = !link.checking,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (c.isDark) Lime else c.primary,
                                contentColor = if (c.isDark) Color(0xFF111111) else c.onPrimary
                            )
                        ) { Text("Link wallet", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { vm.checkWalletLink() },
                            enabled = !link.checking,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink)
                        ) { Text("Check link", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(SectionGap))
            SectionHeader("Transactions")
            Spacer(modifier = Modifier.height(8.dp))
            DesignedEmptyState(
                Icons.Filled.History,
                "No transactions yet",
                "Deposits, withdrawals and settled trades will show here."
            )
            Spacer(modifier = Modifier.height(32.dp))
            // Log out lives here now: the preview's dashboard header has no
            // logout button, and the dashboard header band is gone.
            Text(
                "Log out",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = c.grey,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { vm.logOut() }
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
