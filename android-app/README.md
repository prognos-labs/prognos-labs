# Prognos Labs - Android app (v1.1.0)

Native Android app for Prognos Labs, in Kotlin with Jetpack Compose.
Package: `xyz.prognoslabs.app`. App name: Prognos Labs.
Min SDK 26, target SDK 34.

v1.1.0 is the upgrade build: versionName 1.1.0, versionCode 10. It
renames Prognosis to Prognos Labs everywhere the user can see it,
wires server auth (signup / login / OTP / password reset) to
`https://prognoslabs.xyz/auth.php`, and adds the trading client for
`https://prognoslabs.xyz/api.php` (markets, link, balance, orders,
positions, place order). Trading stays locked until the server says
otherwise: any 401 / 403 / unreachable reply shows "Trading opens
soon", never a raw error and never fake data. No GitHub release was
made from this build; that is the coordinator's step after audit.
The v1.0.0 APK is untouched at
`~/workspace/prognos-labs/prognos-android-debug.apk` (SHA-256
37c980f04c0c2a4adefa7e075b92e13d986c052446eb9f97f38c003f3980d53c);
this build is `prognos-android-debug-v1.1.0.apk`.

## What v1.1.0 changes

- Rename: "Prognosis" is gone from `app/src/main` (zero occurrences,
  enforced by a unit test that scans the tree). App name, strings,
  splash, auth wordmark, dashboard fallback name, Settings profile
  and About all read "Prognos Labs". Identifiers were renamed too
  (`PrognosApi`, `PrognosFontFamily`, `PrognosTheme`, ...). Package
  `xyz.prognoslabs.app` stays.
- Server auth (`data/AuthApi.kt`, `data/TokenStore.kt`,
  `data/Repository.kt` AuthStore): signup (name + email + password)
  sends an OTP, the OTP screen takes 6 digits, then the app signs in
  with the same password, which sends the login code; login (email +
  password) also goes through the OTP screen; forgot password is
  email, OTP, new password. The token is stored in
  EncryptedSharedPreferences (AES256-GCM, Android Keystore) and falls
  back to plain SharedPreferences only if the encrypted store cannot
  be created on a device. The token is sent as
  `Authorization: Bearer` on `me`, and on app start the app calls
  `me`, refreshes the token first when it is older than 20 hours, and
  retries once after a refresh when `me` says "Please sign in again."
  Logout calls the server and clears the token and the trading
  session. Until the backend answers, every auth call fails plainly
  with "Accounts are not ready yet. Please try again later." No
  password is ever stored. Settings change password goes through the
  reset flow (the server has no change-password call): a code goes to
  the signed-in email, then the code plus the new password.
- Trading client (`data/TradingApi.kt`, `data/WalletLink.kt`):
  markets and prices come from `api.php?action=markets` on
  prognoslabs.xyz (same data as the website); link status, balance,
  orders, positions and place order use the server session token.
  Buy Up is green and Sell Down is red, and the picked side keeps its
  colour until the other side is tapped (the pick lives in the
  ViewModel, keyed by market). Both buttons buy the chosen outcome's
  token for the amount (MARKET, total_cost), exactly as the website
  trade panel does: buying the Down token is the down bet. Amounts
  are checked client-side and server errors are shown in plain
  language. The real balance shows on the Dashboard, Portfolio and
  Wallet when trading is ready; real positions show on the Dashboard
  and Portfolio. Deposit and Withdraw stay disabled with a lock: the
  deposit flow needs an onchain signature in the user's own wallet,
  and the app does not build that in v1.1.0.
- Wallet linking, and the one gap to know about. The app holds no
  wallet and no private key. The flow is: the user enters their
  wallet address in Wallet, the app requests a single-use nonce from
  `api.php?action=link_nonce`, opens the link page on prognoslabs.xyz
  in the system browser with the nonce in the URL
  (`?app_link_nonce=`), the user signs the link message there with
  their own wallet app, then comes back and taps Check link, and the
  app polls `link_status` for that address. The trading session token
  is created by `link_wallet` and returned to whoever called it,
  which is the browser page, so the session lives in the browser.
  The app catches it back only if the link page redirects the user to
  `https://prognoslabs.xyz/linked?session=<token>&address=<wallet>`
  (or `prognoslabs://linked` with the same query): the intent filter
  for that return is in the manifest and `MainActivity` hands the URL
  to the ViewModel, which stores the session only after two checks:
  the wallet must be the one this app asked to link (or one the
  server already confirmed as linked), and the server must confirm
  the session belongs to that wallet (`api.php?action=session_status`,
  a read-only check with no broker call). A planted token, a token
  for a different wallet, or a token the server does not recognise is
  dropped and the user is told to start the link again. Until the site
  sends that redirect, a linked wallet shows as linked in the app but
  trading stays locked, because the app holds no session. Nothing is
  faked: the Wallet screen says so plainly. The site change needed is
  one redirect after a successful link, plus assetlinks.json if the
  https return should open the app without a chooser. One thing the
  app cannot fix on its own: the session still travels in the redirect
  URL, so it lands in browser history and server logs; the lasting fix
  is a site-side one-time exchange code instead of the raw session in
  the URL (see AUDIT_REPORT_v1.1.0.md).
- All server addresses live in one place, `data/AppConfig.kt`
  (`AUTH_BASE_URL`, `TRADING_BASE_URL`, `LINK_PAGE_URL`), so they can
  change without touching a screen.
- Kept from v1: login/signup then dashboard; no docs screen; no news
  on home (notification bell instead); compact markets (top 8 + Show
  more, asset dropdown, search, button tabs); real logo v3; bundled
  Archivo + IBM Plex Mono; vector background; no chart line; no LIVE
  pill; white or darks. Copy rules hold everywhere user-visible: no
  "Prognosis", no Sepolia, no chain names or IDs, no "live trading"
  hype, no test/prototype wording, no em dashes, no AI-sounding
  words, never the word "broker" (zero matches in `app/src/main`).

Tests: 77/77 pass (45 from v1 plus 33 in AuthTradingTest, 6 in
AuthValidationTest: rename scan (case-insensitive), OTP validation,
auth response parsing for every contract shape, trading lock states,
Buy/Sell colour persistence, order amount validation, wallet address
+ link message + return-URL parsing, lenient balance/position/order
parsing, and live HTTP round trips against a stub server in the test
itself, including a dead-port run that proves unreachable means
locked, never a crash, plus session validation against the stub and
a linked-return test that rejects a wallet the app did not ask to
link). Token
storage is EncryptedSharedPreferences; the encrypted store is in the
APK (`META-INF/androidx.security_security-crypto.version`) and the
code falls back to plain SharedPreferences with no silent upgrade if
creation fails on a device.

## What was verified live vs not

- Verified: the APK builds, signs (debug key), installs structurally
  (aapt badging clean: package `xyz.prognoslabs.app`,
  versionCode 10, versionName 1.1.0, min SDK 26, target SDK 34,
  INTERNET + ACCESS_NETWORK_STATE, label "Prognos Labs"), the auth
  and trading calls run over real HTTP against a local PHP stub that
  answers the fixed contract (signup, verify OTP login, wrong
  credentials, `me` with a good and a bad token, link nonce, link
  status, balance 403), and the same contract runs against a stub
  server inside the unit tests. The locked trading state renders
  when api.php is unreachable (dead-port test).
- NOT verified: the real backend, because it is not live yet: the
  auth backend directory is empty at build time, and prognoslabs.xyz
  has the partner lock (401 on root/api.php). No live signup, login,
  OTP email, wallet link, deposit or trade has been proven. The
  EncryptedSharedPreferences path is in the APK but has not run on a
  real phone; the fallback exists if it fails there. Flint's phone
  test is the real proof, as always.

# Prognosis - Android app (v1)

Native Android app for Prognos Labs, in Kotlin with Jetpack Compose.
Package: `xyz.prognoslabs.app`. App name: Prognosis.
Min SDK 26, target SDK 34.

v1 is the release build: versionName 1.0.0, versionCode 9 (the 9th
build iteration). Flint asked for the release to be v1 when it is
ready. versionCode stays 9 on purpose: Android refuses to install an
APK with a lower versionCode over the v8 already on his phone, so
lowering it to 1 would have forced an uninstall first. Users see
1.0.0.

v1 fixes exactly what Flint listed after testing v8 on his phone
(17 Nov ~10:42 WAT, 5 cropped screenshots): "the app needs alot of
optimization." Seven points, nothing else redesigned:

1. The "Accounts on this device for now. Server sign-in arrives
   with trading." note is deleted from the log in and sign up
   screens, not replaced with other copy.
2. The "Prediction markets, built for your phone." line is deleted
   from the log in and sign up screens. Flint called it an awful
   keyword.
3. The field labels are fixed: the label has an explicit 14sp line
   height, a fixed 5dp gap sits between label and input, and input
   and placeholder share a 20sp line height, inside the same 60dp
   field, so the placeholder can never crowd the label again. The
   eye icon stays on the Password field.
4. The bottom nav selected indicator is now a short lime line
   (24dp wide, 3dp tall) centred under the active tab's label,
   on the active tab only. The 5dp lime dot is gone.
5. The Amount field on the market detail screen: the "$" now sits
   directly next to the amount as one right-aligned unit ("$ 10.00"),
   instead of ~45dp away. The "About 20 shares at 0.50." line below
   it is unchanged.
6. The profile avatar on the Dashboard now opens a Settings screen:
   Profile (avatar + signed-in name and email); Authentication
   (change password, verified locally, new password minimum 8
   characters, plus Sign out); Appearance (dark mode toggle);
   Notifications (price-alerts toggle, real: it silences the bell
   dot and the alerts list); About (Prognosis, version 1.0.0,
   "Trading opens soon"). Log out stays at the bottom of Wallet
   too.
7. General optimization pass on every screen: spacing, alignment
   and tap targets checked, no overlaps, nothing cut off, no
   empty voids. No layout or approved-design change anywhere else.

Tests: 45/45 pass (39 from v8 plus 6 new SettingsTest: change
password validation and the amount input filter). The Settings
screen navigation is a Compose NavHost route, covered by the build.

Native Android app for Prognos Labs, in Kotlin with Jetpack Compose.
Package: `xyz.prognoslabs.app`. App name: Prognosis.
Min SDK 26, target SDK 34.

v8 is a fidelity rebuild of the approved design, not a new design. Flint
tested v7 on his real phone (17 Nov ~10:18 WAT, dark-login screenshot)
and said the preview and the app "are very different and do not look
anything alike", then clarified that even INSIDE the app is not the same
as the design preview: dashboard, markets, portfolio, wallet and market
detail all differ too, not just login. v8 was built from a MEASURED
side-by-side comparison of all 8 preview mockups against the app
(`PREVIEW_DIFF.md`, written before any code changed). The comparison
found the real differences: v6/v7 kept structures the preview never had
(a dark dashboard header band, a scrolling ticker strip, separate tab
pills, a two-column order book, a bottom-sheet trade panel, vertical
centred empty states) and invented others ("PREDICTION MARKET" subtitle,
"LIVE PRICES" label, Markets title row with refresh icon, sort dropdown,
"Saved markets" row). The background motif was drawn as a thin STROKE
outline on top of the brightest part of the glow, so it washed out
completely on a real phone, while the preview's motif is a SOLID image.
v8 rebuilds every screen from the preview's measurements: 36dp logo and
18sp wordmark on login, 3-column prices card, pill-container auth tabs,
60dp fields with a static label and an eye icon, the "No account yet?
Create one" link v7 was missing, a plain dashboard header with an avatar
circle and no dark band, 3 fixed mover cards, horizontal empty states,
wrapped outlined filter pills, letter circles on every market row, a
one-table order book, and the trade panel inline on the detail screen.
The motif is now a solid image (`motif_light.png` / `motif_dark.png`,
rendered from the real logo v3 geometry) at 0.15 light / 0.18 dark on
login and 0.10 at 230dp on Wallet, verified by rendering the exact values
and screenshotting them: the motif reads clearly in both themes.

v7 is a fidelity rebuild of the approved design, not a new design. Flint
tested v6 on his real phone (17 Nov ~09:55 WAT, 58s recording) and said
the app is nothing compared to the approved design. Two failures were
visible, and v7 fixes both:

1. **The font.** v6 bundled no font, so the app inherited the phone's
   system font. On Flint's phone that is a handwriting font, and the
   whole app rendered in it: login, dashboard, wallet, market detail,
   every screen. v7 bundles real open-source fonts in the APK (Archivo
   for text, IBM Plex Mono for prices and labels, both OFL licensed,
   static instances per weight in `res/font/`) and sets them as the
   app-wide typeface in the theme, so the app looks identical on any
   phone. Nothing in shipped code falls back to the system font.
2. **The background design was invisible.** v6's login screen was plain
   white with only a faint green tint: the lime glow peaked at 0.30,
   the cropped logo motif at 0.05 and the dot field at 0.10. v7 makes
   it actually visible: glow peak 0.60 light / 0.45 dark, motif 0.18
   light / 0.22 dark, dots 0.22, and the dashboard header motif and
   glow are stronger too. Same positions as the approved preview, same
   lightweight static Canvas, no animation, no per-frame work. The
   Wallet screen also gets the cropped motif from the preview, so it
   is no longer a large white void.

On top of that, v7 applies concrete rules from the design skills Flint
sent (Taste Skill, Make Interfaces Feel Better, Impeccable, UI UX Pro
Max): one system everywhere (one button pair, one card style, one
radius scale); empty states are designed containers with a dashed
border and a lime-tinted icon circle, never bare grey lines; 44dp
minimum touch targets (the market-row star was 32dp); concentric
radii (balance card 20dp, inner buttons 12dp, exactly as the preview);
tabular numbers (the bundled mono font is equal width, so changing
prices never shift the layout); a subtle 0.96 press scale on primary
buttons; headline 32sp and balance value 33sp, exactly as the preview.
The preview's own background values were deliberately NOT copied
literally: its motif is 0.07 and its glow fades to transparent at 68%,
and those are the values that made v6 look plain white.

v6 builds exactly the design preview Flint approved on 17 Nov 2026 ~02:28 WAT ("I love this so much. build this now."). The preview is the source of truth: no new design inventions. Against v5, the changes are: Deposit and Withdraw are disabled, greyed with lock icons, everywhere (balance card, quick actions, Portfolio Holdings) instead of bright buttons that opened explanation screens; those Deposit/Withdraw detail screens are gone. The balance card is a dark card in both themes. The auth subline is now "Prediction markets, built for your phone." and the auth button is lime in dark mode. Positions empty states read "Trading opens soon." The Wallet note reads "Deposits and withdrawals open with trading." A single TradingLock object is now the one place that says trading, deposits and withdrawals are closed, and a unit test fails the build if any of them ever flips open by mistake.

v5 is a design correction on top of v4. Flint rated v4 4/10: the hero sparkline on the balance card was a live Bitcoin price that did not belong on the portfolio dashboard, the logo on the log in and sign up page was a P drawn in code and not the real logo, and there was no real background design anywhere. v5 removes the sparkline and the LIVE pill from the balance cards, uses the real logo image on the splash and auth screens, and adds a proper background design: a lime glow, a giant ribbon-P motif cropped off an edge, and a dot field, all drawn as lightweight static vectors, plus a designed dark header band on the Dashboard.

## What v5 includes

- Background design (`ui/Background.kt`, new in v5, made visible in v7, made a solid image in v8): a lime glow, a giant ribbon-P motif cropped off an edge, and a dot field. The auth and splash screens get the full background; the Wallet screen gets the cropped motif at the bottom right, as the approved preview draws it. The motif is a SOLID image (`motif_light.png` / `motif_dark.png`, rendered from the real logo v3 geometry), not a stroke drawn in code: v7's stroked outline on top of the brightest part of the glow washed out completely on Flint's phone. v8: the glow peaks at 0.65 in both themes (a 340dp circle, centre 0.85w / 0.045h), the motif is at 0.15 light / 0.18 dark (380dp, top:-120, right:-165), the dots are 1.6dp on a 15dp grid at 0.22, and the Wallet motif is 230dp at right:-70, bottom:60, alpha 0.10 (the preview's own placement, read from its markup; v7's 380dp placement showed almost nothing because its visible window contains only a sliver of the P). The alphas are named constants in `BackgroundDesign` and a unit test fails the build if they ever drift back below visibility. No bitmap backgrounds, no blur, no animation, no per-frame work, so it costs almost nothing on an average phone. The real logo is a drawable: `prognos_logo.png` (light) and `prognos_logo_dark.png` (dark), the v3 logo files, used as the image on the splash and auth screens and already used for the launcher icon. No logo is drawn in code anywhere. There is NO dark header band on the Dashboard: the preview's header is plain, and the band was deleted in v8.
- Fonts (`res/font/`, new in v7): Archivo for text and IBM Plex Mono for prices, labels and countdowns, both OFL licensed, bundled in the APK so the app never inherits the phone's system font. Static instances per weight (Archivo 400/500/600/700/800, Plex Mono 400/500/600/700), subset to Latin + punctuation. `Theme.kt` sets them as the app-wide typeface; every text element uses `PrognosisFontFamily` or `PrognosisMonoFamily`, or inherits them from the theme. Mono digits are equal width, so changing prices never shift the layout.
- Design system (`ui/Design.kt`): one 8pt spacing scale, 16dp screen padding, 12dp card corners, one type scale (22sp screen title, 17sp section header, 15sp body, 13sp caption, 11sp mono label), one colour system, section headers with a title on the left and a small "See all" action on the right. Every screen aligns to the same left edge and has one primary thing on it. Change pills, auth tabs (one pill container with a sliding active pill), wrapped outlined filter pills, letter circles with bright brand colours, a clean balance card, designed empty states (horizontal: icon left, text middle, action right) and a P&L card.
- Splash screen, then a designed Log in / Sign up screen: the real logo image (36dp) and "Prognosis" wordmark (18sp/800, no subtitle), the headline "Trade the question.", the line "Prediction markets, built for your phone.", a 3-column live prices card (BTC / ETH / SOL from the real ticker, no label, no scrolling strip), pill-container tabs to switch between Log in and Sign up, 60dp fields with a static label and placeholder and an eye icon on the password, and a big primary button (black in light mode, lime in dark mode), plus the centred note and the "No account yet? Create one" link with a lime underline. The whole screen sits on the designed background (lime glow, solid cropped ribbon-P motif, dot field). Auth is local on-device only for now (DataStore Preferences, only a SHA-256 hash of the password is stored, never the password). The auth screen says plainly: "Accounts on this device for now. Server sign-in arrives with trading." If already logged in, the app goes straight to the Dashboard. Log out is at the bottom of the Wallet screen.
- Bottom navigation, 4 items: Dashboard, Markets, Portfolio, Wallet. Custom bar, no Material3 indicator pill: the active tab has a bold label and a lime dot under its icon.
- Dashboard (redesigned): a plain header with the greeting and your first name, notification bell in a bordered circle (with a lime dot when there is an alert), dark mode toggle in a bordered circle, and an avatar circle with your first letter. No dark band. Below it, a clean dark balance card (total balance $0.00 in large bold type, Deposit and Withdraw inside it as disabled, greyed buttons with lock icons, no chart line, no LIVE pill), a quick actions row (Deposit and Withdraw as disabled circles with lock icons, Trade as an enabled circle with swap arrows), "Your positions" (horizontal empty state with a Browse markets button), "Top movers" as 3 fixed cards in a row (letter circle, code, price, change pill, tap to open Markets), "Watchlist" as rows with letter circles, name and code, price and change pill. No news anywhere on this screen. No ticker tape. Balance is $0.00 and positions are 0. No fake numbers anywhere.
- Notifications centre (bell): lists price alerts from the server watchdog, each with its market question. Empty state when there is none.
- Markets (redesigned): a rounded search field (44dp, radius 14), wrapped rows of outlined filter pills (All, Gainers, Losers, Watchlist, Ending soon), an asset dropdown pill (All assets + the 8 assets with counts) with the market count beside it. No title row, no refresh icon, no ticker strip, no sort dropdown. The list shows 8 markets at a time, then a "Show more (N more)" button loads the next 8, instead of one endless scroll. The market list is compact rows, one market per row, 64dp tall: letter circle with the asset code below it, the question in up to two lines, price and a 24h change pill on the right, star to save. Rows are separated by hairline dividers. No big tabs, no website-style sector grid, no featured card. Tap a row for the detail screen.
- Portfolio (redesigned): the same clean dark balance card (no chart line, no LIVE pill, Deposit and Withdraw disabled with lock icons), a Performance card with three clear numbers (Total value, Today, All time, all $0.00), Holdings / Positions / History as horizontal empty states with action buttons (Deposit shown disabled, Browse markets). No fake numbers.
- Forecast on the market detail screen and in the market list price. Same rule as the website: price is the middle of the best bid and ask, 60% or more leans one way, 40% or less leans the other, otherwise about even. Reason covers price, time left, spread and volume, in plain words. It also records forecasts to the server so the forecast can learn from scored results, same as the website.
- Market detail: circle-outline back button and "Markets" grey text, question, letter circle with asset name and code, green dot and time left, big asset price with a change pill, forecast card (pale lime, "FORECAST" pill, verdict and reason), chart full width (price over the window, "Win line" label, dashed win line at the window-start price when started, lime fill, green end dot, current price, average, which side is ahead), order book as ONE table (asks red on top, "Spread" divider row, bids green below). Buy Up (green) / Buy Down (grey) segmented control, an amount row, about how many shares that amount buys, and a big grey "Trading opens soon" button, all inline on the screen. No order is placed, no trade is faked, no success is shown. Trading opens when the trading service is ready.
- Wallet: balance $0.00 in a card with no buttons inside it, Deposit and Withdraw as two separate grey pills below the card with lock icons, with the note "Deposits and withdrawals open with trading.", empty transactions list ("No transactions yet.", "Deposits, withdrawals and settled trades will show here."), and the solid ribbon-P motif at the bottom right. There are no Deposit/Withdraw detail screens; the pills do nothing until trading opens. Nothing is deposited, withdrawn, or faked. Log out is at the bottom of this screen.
- Theme: crisp white light theme by default (white background, near-black text, black primary buttons, lime accent). Dark mode toggle in the Dashboard header (dark background, white text, same lime accent). No ivory anywhere, no muted website palette.
- Watchlist: star markets, saved on the device only (DataStore). Price alerts live in the Notifications centre.

## What v4 does NOT include

- No Docs tab and no documentation content anywhere. Removed in v2, still gone.
- No news anywhere in the app. The top-stories strip on the Dashboard is gone, and there is no News tab. (The news API endpoint and its data model still exist in the data layer for compatibility, and a unit test still parses a saved news fixture, but nothing in the app fetches or shows news.)
- No trading. There are no working Buy or Sell buttons anywhere. The trade panel on the detail screen is inline (Buy Up / Buy Down segmented control, amount, about how many shares), and its "Trading opens soon" button is disabled. Trading is blocked until the trading service is ready, and none exists yet. Do not expect to place orders from this build.
- No push notifications yet. Price alerts are in the Notifications centre in the app. Telegram is still the alert channel that works outside the app.
- No iOS build. This is Android only.

## Backend

The app reuses the website backend, it does not have its own. Base URL:

`https://prognoslabs.fruitslice.xyz/api.php`

Endpoints used: `ticker`, `markets`, `market`, `book`, `chart`, `watchdog`, `learning`, `record_forecasts` (POST). No key or secret is used or stored in the app. Only https is allowed.

## How to install the APK on Android

1. Copy `prognos-android-debug.apk` (or `app/build/outputs/apk/debug/app-debug.apk`) to the phone, for example by sending it to yourself or downloading it from a file link.
2. On the phone, tap the APK file to install it. If you open it from DuckDuckGo, download it in Chrome instead: DuckDuckGo saves it as a `.bin` file and its file provider will not let a file manager copy it out, so the installer never appears.
3. If Android asks, allow "Install unknown apps" for the app you are installing from (browser or file manager), then tap Install.
4. Open Prognosis from the home screen. Sign up once (name, email, password), then you go straight to the Dashboard. It needs internet to load markets.

This is a debug build, signed with the debug key. It is for testing, not for the Play Store.

## How to rebuild

Needs JDK 17 and the Android SDK (command-line tools) with platform-tools, platforms;android-34 and build-tools;34.0.0. On this machine the SDK lives at `~/workspace/prognos-labs/android-sdk/` and a persistent JDK 17 (Temurin) at `~/workspace/prognos-labs/jdk-17/`.

Use the standalone Gradle 8.7 at `~/workspace/prognos-labs/gradle-8.7/bin/gradle`, not `./gradlew`: the wrapper tries to download Gradle from services.gradle.org and that download fails in this VM (the egress proxy presents an intercept certificate Java does not trust). The standalone Gradle is already on disk and is the proven path.

The `JAVA_TOOL_OPTIONS` line is not optional: without `-Djava.net.preferIPv4Stack=true` the Gradle daemon's sockets use IPv6-mapped addresses and every localhost/proxy connection is reset, so the build hangs and then fails. `org.gradle.jvmargs` in gradle.properties does not pass this flag to the daemon JVM command line, so it has to come through `JAVA_TOOL_OPTIONS`.

```sh
export JAVA_HOME=$HOME/workspace/prognos-labs/jdk-17
export ANDROID_HOME=$HOME/workspace/prognos-labs/android-sdk
export GRADLE_USER_HOME=$HOME/.gradle
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"
export JAVA_OPTS="-Djava.net.preferIPv4Stack=true"
export GRADLE_OPTS="-Djava.net.preferIPv4Stack=true"
cd ~/workspace/prognos-labs/android-app
~/workspace/prognos-labs/gradle-8.7/bin/gradle assembleDebug --max-workers=1 --no-daemon
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Run the unit tests (they parse real live fixtures saved in `app/src/test/resources/`, plus pure auth validation and market-list logic tests):

```sh
~/workspace/prognos-labs/gradle-8.7/bin/gradle testDebugUnitTest --max-workers=1 --no-daemon
```

Two environment-specific pieces the build depends on, both already in place on this machine:

- `~/.gradle/init.d/proxy.gradle` reads the proxy from the `https_proxy` env var at build time (no credential stored) and sets it for Gradle's HTTP client, with Google Maven (`dl.google.com`) excluded so it goes direct. Without it, Maven Central is unreachable from Java in this VM.
- `settings.gradle.kts` redirects the two Kotlin plugin markers to their real Maven Central modules, because Java cannot reach `plugins.gradle.org` from this VM at all. Do not remove that block unless the network setup changes.

## Play Store release (not done here)

A Play Store release needs Flint, not the builder:

- A Google Play developer account ($25 once, his Google account, his ID check).
- A release keystore, created once and kept safe. Do not create it in this debug build, and do not lose it: losing it means a new app listing.
- A privacy policy page and store listing text, screenshots and icon.

When those are ready, build a release APK/AAB with the release keystore and upload it in Play Console.

## Known limits

- The win line has not been seen on a live market yet when the window has not started. Until a listed market window starts, the chart shows the last hour with "Win line not set yet" and a neutral fill. Same caveat as the website.
- Some markets have no live price yet (empty best bid/ask, volume 0). The app shows "-" honestly instead of inventing a price, and shows no forecast for those markets.
- The forecast uses the starting formula. The learning endpoint reports 0 scored forecasts so far, so no challenger has been promoted.
- Auth is local only. There is no server sign-in yet, so an account made on one phone does not exist on another. Server sign-in arrives with trading.
- Trading, deposits and withdrawals are all gated and disabled in this build. They open with trading.
- The app was not tested on a real phone in this build. Flint's phone test is the real proof.
