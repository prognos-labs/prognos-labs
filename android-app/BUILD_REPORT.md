# Prognos Labs Android app - Build report (v1.1.0, debug)

Built 20 Nov 2026, early morning WAT. Result: **BUILD SUCCESSFUL**.

v1.1.0: versionName 1.1.0, versionCode 10. Rename Prognosis to
Prognos Labs everywhere user-visible (zero "Prognosis" in
`app/src/main`, enforced by a unit test), server auth to
`https://prognoslabs.xyz/auth.php` (signup / login / OTP / password
reset, token in EncryptedSharedPreferences with a plain
SharedPreferences fallback), and the trading client for
`https://prognoslabs.xyz/api.php` (markets, link nonce/status,
balance, orders, positions, place order). Buy Up green, Sell Down
red, picked side keeps its colour. Both trade buttons buy the chosen
outcome's token (MARKET, total_cost), as the website trade panel
does. Trading stays locked ("Trading opens soon") on any 401 / 403 /
unreachable reply. Deposit and Withdraw stay disabled with a lock.
No GitHub release was made from this build, and nothing was
committed to git. The v1.0.0 APK is untouched
(SHA-256 37c980f04c0c2a4adefa7e075b92e13d986c052446eb9f97f38c003f3980d53c).

## Deliverable

- APK: `app/build/outputs/apk/debug/app-debug.apk` (19,358,802 bytes, ~18.5 MB)
- Copy: `~/workspace/prognos-labs/prognos-android-debug-v1.1.0.apk` (same file)
- SHA-256: `8dd172ef83a899410a65b7f41213b3d409cf40f2a995386a572476d64fe29514`
- Package: `xyz.fruitslice.prognos`, versionCode 10, versionName 1.1.0
- App name: Prognos Labs. Launcher activity: `xyz.fruitslice.prognos.MainActivity`
- Min SDK 26, target SDK 34, compile SDK 34
- Permissions: INTERNET, ACCESS_NETWORK_STATE (plus the system-added dynamic-receiver permission)
- Icon: adaptive icon from the ribbon-P logo (v3), all densities present
- Token storage: androidx.security:security-crypto 1.1.0-alpha06, in the
  APK (`META-INF/androidx.security_security-crypto.version`); the
  encrypted store has a plain-SharedPreferences fallback in code.

## Wallet-link mechanism (as implemented)

The app holds no wallet and no private key. Wallet tab: the user
enters their wallet address, the app requests a single-use nonce
(`api.php?action=link_nonce`), opens the link page on prognoslabs.xyz
in the system browser with `?app_link_nonce=<nonce>`, the user signs
"Link this wallet to Prognos Labs / Nonce: ..." in their own wallet
app, returns, and taps Check link; the app polls `link_status` for
that address. The trading session token is created by `link_wallet`
and returned to the browser page, so it lives in the browser. The app
gets it back only if the link page redirects to
`https://prognoslabs.xyz/linked?session=<token>&address=<wallet>` (or
`prognoslabs://linked`); the intent filter is in the manifest and
`MainActivity` saves the session to the encrypted store. Until the
site sends that redirect, a linked wallet shows as linked but trading
stays locked, because the app holds no session. That is the one gap:
it needs a one-line site redirect after a successful link (plus
assetlinks.json for chooser-free https return). Nothing is faked.

## Tests

- `gradle testDebugUnitTest assembleDebug --max-workers=1 --no-daemon`:
  **BUILD SUCCESSFUL in 1m 26s** on the final run. **75 passed, 0
  failed, 0 skipped**: 45 from v1 plus 30 new in AuthTradingTest
  (rename scan, OTP validation, auth response parsing for every
  contract shape, trading lock states, Buy/Sell colour persistence,
  order amount validation, wallet address + link message + return-URL
  parsing, lenient balance/position/order parsing, HTTP round trips
  against a stub server inside the test, and a dead-port run proving
  unreachable means locked, never a crash).
- Two compile/test fixes on the way: `JsonArray` needs `asJsonArray`
  (a boolean `isJsonArray()` method does not smart-cast), and the
  app_name string had become "Prognos" (the rename sed ran before
  the targeted `>Prognosis<` fix) plus one OTP test expectation that
  contradicted the trim behaviour.

## Static checks (shipped code, `app/src/main/`)

- Zero "Prognosis". Zero em dashes. Zero Sepolia / broker wording.
  Zero "live trading". Zero private keys in the DEX (0 matches for a
  PEM private key header). No key or secret is used or stored in the
  app; the trading service is only ever reached through api.php.
- Deep-link intent filter for the wallet-link return is in the
  merged manifest (verified with `aapt dump xmltree`).

## What was verified live vs not

- Verified over real HTTP against a local PHP stub answering the
  fixed contract: signup, verify OTP (login purpose returns token +
  user), login with wrong password (exact contract error), `me` with
  a good token (200) and a bad token (401 "Please sign in again."),
  link nonce, link status (linked false), balance 403 with the link
  action. The same shapes run in the unit-test stub server.
- NOT verified: the real backend. The auth backend directory was
  empty at build time and prognoslabs.xyz has the partner lock (401),
  so no live signup, login, OTP email, wallet link, deposit or trade
  has been proven. The EncryptedSharedPreferences path is in the APK
  but has not run on a real phone. Flint's phone test is the real
  proof. The v1.0.0 backup is at
  `~/workspace/prognos-labs/android-app-v1-backup/src` (plus
  build.gradle.kts).

## Files

- `build-log-v1.1.0.txt` / `build-log-testDebugUnitTest-v1.1.0.txt` -
  the successful v1.1.0 combined build + test log (75 passed)
- `README.md` - v1.1.0 section at the top

---

# Prognosis Android app - Build report (v1, debug)

Built 17 Nov 2026, late morning WAT. Result: **BUILD SUCCESSFUL**.

v1 is the release build: versionName 1.0.0, versionCode 9 (the 9th
build iteration). Flint asked (17 Nov ~10:45 WAT) for the release to
be v1 when it is ready. versionCode stays 9 on purpose: Android
refuses to install an APK with a lower versionCode over the v8
already on his phone, so lowering it to 1 would have forced an
uninstall first. Users see 1.0.0.

v1 fixes exactly what Flint listed after testing v8 on his phone
(17 Nov ~10:42 WAT, 5 cropped screenshots): "the app needs alot of
optimization." Seven points, nothing else redesigned. See "What
changed in v1" below.

v8 is a fidelity rebuild of the approved design, not a new design. Flint
tested v7 on his real phone (17 Nov ~10:18 WAT, dark-login screenshot)
and said: "look at the design preview and what you've sent me, they are
very different and do not look anything alike." He is right, and he then
clarified (~10:20 WAT) that even INSIDE the app is not the same as the
design preview: the dashboard, markets, portfolio, wallet and market
detail screens differ too, not just login. v8 was built from a MEASURED
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
v8 rebuilds every screen from the preview's measurements. See "What
changed in v8" below.

v5 is a design correction on top of v4. Flint rated v4 4/10: the hero sparkline was a live Bitcoin price on the portfolio dashboard that did not belong there, the logo on the log in and sign up page was a P drawn in code and not the real logo, and there was no real background design anywhere. v5 removes the sparkline and the LIVE pill from the balance cards, uses the real logo image on the splash and auth screens, and adds a proper background design built from lightweight static vectors.

## Deliverable

- APK: `app/build/outputs/apk/debug/app-debug.apk` (18 MB)
- Copy: `~/workspace/prognos-labs/prognos-android-debug.apk` (same file, v8 copy overwritten)
- SHA-256: `37c980f04c0c2a4adefa7e075b92e13d986c052446eb9f97f38c003f3980d53c`
- Package: `xyz.fruitslice.prognos`, versionCode 9, versionName 1.0.0 (v1 release)
- App name: Prognosis. Launcher activity: `xyz.fruitslice.prognos.MainActivity`
- Min SDK 26, target SDK 34, compile SDK 34
- Permissions: INTERNET, ACCESS_NETWORK_STATE (plus the system-added dynamic-receiver permission)
- Icon: adaptive icon from the ribbon-P logo (v3), all densities present

## What changed in v1 (v9 build iteration)

Flint's 7 fixes from his v8 phone test, in his order. Nothing else
redesigned; the v8 preview match is untouched.

1. **"Accounts on this device" note, gone.** The note under the
   primary button is deleted from both the log in and sign up
   screens, not replaced with other copy. The "No account yet?
   Create one" switch line stays.
2. **"Prediction markets, built for your phone.", gone.** Deleted
   from both the log in and sign up screens. Flint called it an
   awful keyword. The headline goes straight to the 3-column
   prices card with 22dp below it.
3. **Field labels fixed.** On Flint's phone the placeholder sat
   right up against the label ("you@example.com" against "Email",
   "At least 8 characters" against "Password"). The label now has
   an explicit 14sp line height, a fixed 5dp gap sits between label
   and input, and input and placeholder share a 20sp line height,
   inside the same 60dp field. The label is static, so its position
   never moves whether the field is empty, focused or filled. The
   eye icon stays on the Password field. Applies to the Name field
   too, and to the three password fields on the Settings screen.
4. **Bottom nav: dot replaced with a line.** The 5dp lime dot under
   the active tab is gone. A short lime line (24dp wide, 3dp tall,
   radius 2dp) now sits centred under the active tab's label, on
   the active tab only. Flint suggested "a small circle on them or
   like a line under it, idk"; the line is the clean standard
   treatment.
5. **Amount field: "$" next to the value.** v8 gave the value a
   fixed 90dp-wide field, so the "$" sat about 45dp away from
   "10.00". The value field now sizes to its content
   (`IntrinsicSize.Min`, 48dp minimum so it stays tappable when
   empty) with a 4dp gap after the "$", so "$ 10.00" reads as one
   right-aligned unit. The "About 20 shares at 0.50." line below
   it is unchanged. The input filter (digits and dots only) is now
   a pure function, `sanitizeAmountInput`, with unit tests.
6. **Profile avatar clicks: new Settings screen.** The black
   circle with the initial on the Dashboard (top right, next to
   the bell and the dark-mode moon) had no click handler in v8.
   It now opens a Settings screen, same design language (bundled
   Archivo / Plex Mono, black / lime / white, same card style):
   Profile (avatar + signed-in name and email); Authentication
   (change password: current password verified locally against
   the stored hash, new password minimum 8 characters, confirm
   must match, inline success / error message, fields cleared
   after submit; Sign out button); Appearance (dark mode toggle,
   same as the moon); Notifications (price-alerts toggle, stored
   on device, defaults to on); About (Prognosis, version 1.0.0
   from BuildConfig, "Trading opens soon" note). The toggle is
   real: with alerts off, the Notifications screen says "Price
   alerts are off" and the bell dot on the Dashboard hides. Log
   out stays at the bottom of Wallet too.
7. **General optimization pass.** Spacing, alignment and tap
   targets checked on every screen while in there: no overlaps,
   nothing cut off, no empty voids. No layout or approved-design
   change anywhere else.

Also in this build: `buildConfig = true` was set explicitly in
`app/build.gradle.kts`, because BuildConfig was not being generated
and Settings > About reads the version from it (the first build
failed on the unresolved reference; the flag fixed it).

## What changed in v8

Every screen rebuilt from the measured preview (`PREVIEW_DIFF.md`).

**Login (light + dark):** 36dp logo + 18sp/800 wordmark, no subtitle; top
spacer 14dp (was 56dp); headline 31sp/800; 3-column prices CARD
(BTC/ETH/SOL, dividers, no "LIVE PRICES" label, no scrolling strip);
AuthTabs as one pill container (52dp, radius 999, sliding active pill);
fields 60dp, radius 16, 1.5dp border, static label + placeholder, eye
icon on password; primary 56dp; note CENTRED 12.5sp; "No account yet?
Create one" link with lime underline (was missing entirely, which left
the bottom void). Background: solid motif IMAGE (new drawables
`motif_light.png` / `motif_dark.png`, rendered from the real logo v3
geometry) at 0.15 light / 0.18 dark, glow 340dp circle at 0.65 peak,
dots 15dp grid, 1.6dp radius. Verified by rendering the exact design
values in HTML and screenshotting them: the motif reads clearly in both
themes (mock at `/tmp/motif-check/mock.png`).

**Dashboard:** the dark header band is DELETED. Plain header: greeting
13sp grey + name 19sp/800, bell in a 40dp bordered circle with an 8dp
lime dot, theme toggle in a 40dp bordered circle, avatar circle 40dp
(first letter). Quick actions are 50dp circles with the preview's
colours and a swap-arrows Trade icon. Positions is a HORIZONTAL empty
state (icon left, text middle, button right). Top movers are 3 FIXED
cards in a row (BTC/ETH/SOL when present) with letter circles, not a
scrolling row. Watchlist rows use letter circles + name/code + price/
change pill. Log out moved to the bottom of the Wallet screen.

**Markets:** no title row, no refresh icon, no ticker strip, no sort
dropdown, no "N markets" line. Search 44dp, radius 14, bg surface2.
"All assets" dropdown pill + "N markets" text. Tabs are two WRAPPED
rows of outlined pills (FlowRow), black active pill. Rows are 64dp with
a letter circle + code below it, question 13.5sp/600, price 14.5sp/800 +
change pill. "Show more" unchanged.

**Portfolio:** title 22sp. P&L is a 3-column bordered card, labels
12.5sp/600 grey, values 16sp bold; the "Saved markets" row is deleted.
Holdings / Positions / History are HORIZONTAL empty states with the
preview's copy ("Your assets will show here once you deposit.", "No
history yet" / "Settled markets will show here.").

**Wallet:** title 22sp. Balance card WITHOUT buttons inside. Deposit /
Withdraw are two separate grey pills BELOW the card + centred note.
Transactions vertical empty state with the preview's copy. Wallet motif
is a solid 230dp image at right:-70, bottom:60 (the preview's own
placement, read from its markup), alpha 0.10. Verified by render: the
motif reads clearly in both themes (mock at
`/tmp/motif-check/wallet-mock3.png`). The first attempt used the wrong
placement (380dp at bottom:-120 right:-165), which a render check caught
because the visible window of that placement contains only a sliver of
the P.

**Market detail:** circle-outline back + "Markets" grey, no star.
Question 22sp. Meta row: letter circle + "Name · CODE" + green dot +
"X left". Big asset price + change pill (was two huge outcome
percentages). Forecast card is pale lime with a "FORECAST" pill +
verdict ("Up, 62%") + reason. Chart has no card border, "Win line"
label, dashed win line, lime fill, green end dot. Order book is ONE
table (asks red on top, "Spread" row, bids green below). Buy Up GREEN /
Buy Down GREY segmented control, inline. Amount row (label left, value
right), "About N shares" centred, big grey "Trading opens soon" button
inline. The ModalBottomSheet is DELETED.

**Bottom nav:** custom bar, no Material3 indicator pill. Active = bold
label + 5dp lime dot under the icon. Icons: home, bar chart, pie,
wallet, as the preview.

**Letter circles:** bright brand colours (BTC #F7931A, ETH #627EEA,
SOL #9945FF, XRP #23292F, DOGE #C2A633, BNB #F0B90B, HYPE #00C389,
ZEC #F4B400), white bold first letter.

Kept from v6/v7: bundled fonts (Archivo + IBM Plex Mono, 9 TTFs in the
APK), TradingLock (one source of truth, all flags false; Deposit/
Withdraw disabled with lock everywhere), dark balance card in both
themes, auth copy, 4-tab nav, markets 8 + Show more, asset dropdown,
search, button tabs, forecast cards, order book data, watchlist (device
only), notifications centre, no news on home, no docs, no emojis, no em
dashes, no AI/Gate/Sepolia wording, no fake numbers or trades, trading
always gated ("Trading opens soon").

**Tests:** 39/39 pass (AuthValidation 7, FontAndBackground 8,
MarketList 13, Parsing 8, TradingLock 3). FontAndBackgroundTest updated
for v8: the header-band test is gone (band deleted), a new test asserts
the motif drawables exist.

**Static checks:** no attribution markers,
zero em dashes, zero Gate/Sepolia/Broker wording (the only matches are
`nav.navigate`, a substring), zero code-drawn logo, zero Deposit/
Withdraw detail screens or routes, zero system-font fallbacks. The only
non-ASCII character in shipped code is the "·" middot in the detail
meta row, which matches the preview's own "Bitcoin · BTC".

**Honest limits:** not tested on a real phone or emulator here; Flint's
phone test is the real proof. Win line unseen live when no market window
has started; some markets show "-" honestly for empty prices.
Debug-signed only. The background renders are HTML mocks of the exact
app values (same PNG, size, position, alpha), not Compose screenshots:
no emulator is available here, so a Compose render was not possible.

## Toolchain versions

- Gradle 8.7 (standalone, `~/workspace/prognos-labs/gradle-8.7/`)
- Android Gradle Plugin 8.5.2
- Kotlin 2.0.21, Compose compiler plugin 2.0.21, Compose BOM 2024.09.00
- JDK 17.0.21 (Eclipse Temurin, persistent at `~/workspace/prognos-labs/jdk-17/`)
- Android SDK `~/workspace/prognos-labs/android-sdk/`: build-tools 34.0.0, platform-tools 37.0.1, platforms;android-34
- Retrofit 2.11.0, OkHttp 4.12.0, Gson 2.11.0, Navigation Compose 2.7.7, DataStore Preferences 1.1.1

## Build result

- `./gradlew assembleDebug` was NOT used: the wrapper cannot download Gradle in this VM (see below). The build ran with the standalone Gradle 8.7.
- Command (full env in README.md): `gradle assembleDebug --max-workers=1 --no-daemon`
- v1 (v9 build iteration): `BUILD SUCCESSFUL in 1m 33s` on the final full build (`assembleDebug testDebugUnitTest` in one run). One compile fix on the way: `BuildConfig` did not resolve, because BuildConfig generation was not enabled; fixed by setting `buildConfig = true` in `buildFeatures` in `app/build.gradle.kts`. A second full run with `--rerun-tasks` saved the logs (`build-log-assembleDebug-v1.txt`: BUILD SUCCESSFUL in 2m 15s; `build-log-testDebugUnitTest-v1.txt`: BUILD SUCCESSFUL in 1m 43s, 45/45 pass). The SHA above is the APK from that logged run.
- v8: `BUILD SUCCESSFUL in 1m 4s` on the final full build (after 5 compile fixes: Tab visibility, MoverCard weight scope, Adjust icon does not exist, StarOutline fully-qualified reference, one stray brace from the Markets rewrite), plus a comment-only fix and a wallet-motif placement fix, each followed by a rebuild and a full test run. The SHA above is the APK after the last fix.
- v7: `BUILD SUCCESSFUL in 57s` on the first full build after one fix (see below). Full log: `build-log-assembleDebug-v7.txt` in this folder.
- v6: `BUILD SUCCESSFUL in 2m 1s` on the first full build, no compile errors. Full log: `build-log-assembleDebug-v6.txt` in this folder. One rename after verification (see Static checks): `TradingGate` became `TradingLock` so the name itself cannot trip the banned-word check, followed by a clean rebuild and a full test run (`build-log-assembleDebug-v6-final.txt`); the SHA above is the APK after that rename. No behaviour change in the rebuild, rename only.
- v5: `BUILD SUCCESSFUL in 1m 34s` on the third build attempt. Full log: `build-log-assembleDebug-v5.txt` in this folder. Two compile fixes on the way, both mine: the Sparkline deletion in `Chart.kt` also ate the `PriceChart` declaration line, restored with its doc comment; and `Background.kt` was missing two imports (`StrokeCap`, `fillMaxWidth`). No other code changed to get the build green.
- v4: `BUILD SUCCESSFUL in 2m 6s` on the first full build, no compile errors. Full log: `build-log-assembleDebug-v4.txt` in this folder. One small cleanup after verification: the unused `NewsItem` import in `AppViewModel.kt` was removed, followed by a clean rebuild and a full test run (`build-log-assembleDebug-v4-final.txt`); the SHA above is the APK after that cleanup. No code change in the rebuild, import only.
- v3: `BUILD SUCCESSFUL in 1m 47s` on the first full v3 build after one fix, then `BUILD SUCCESSFUL in 32s` after the icon-colour fix below. Full log: `build-log-assembleDebug-v3.txt` in this folder.
- The first v3 build failed on two compile errors, both the same cause: the star icons were referenced by fully-qualified path (`androidx.compose.material.icons.Icons.Filled.Star`), but Compose icons are extension properties, so they need the package imports (`androidx.compose.material.icons.filled.Star`, `androidx.compose.material.icons.outlined.StarOutline`) and the `Icons.Filled.Star` / `Icons.Outlined.StarOutline` form. Fixed with the imports in `Design.kt` and `Screens.kt`.
- One more fix after verification: the launcher icon background was still the old ivory `#F5F2EB`, the only ivory left anywhere. Changed to white `#FFFFFF` so there is no ivory in the app at all. The SHA above is the APK after that fix.
- One warning, harmless: `Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so, libdatastore_shared_counter.so.`

## What changed in v7

A fidelity rebuild of the approved preview. Two failures from Flint's
phone test, fixed, plus design-skill polish. Nothing is invented beyond
the preview.

**1. Bundled fonts (the big fix).** v6 bundled no font and inherited
the phone's system font; on Flint's phone that is a handwriting font,
so the whole app rendered in it. v7 bundles real open-source fonts in
`res/font/`: Archivo (Regular 400, Medium 500, SemiBold 600, Bold 700,
ExtraBold 800) for text and IBM Plex Mono (Regular, Medium, SemiBold,
Bold) for prices, labels and countdowns. Both OFL licensed, from
Google Fonts (github.com/google/fonts). Archivo is a variable font
upstream, so static instances per weight were generated with
fontTools instancer and subset to Latin + punctuation (about 75 KB
per Archivo weight, 18 KB per Plex Mono weight, about 448 KB total).
`Theme.kt` defines `PrognosisFontFamily` / `PrognosisMonoFamily` and
a `PrognosisTypography` that sets Archivo as the default font family
for every Material3 text style, so any Text that does not name a
family still renders in Archivo. All 33 `FontFamily.Monospace`
usages in `Design.kt` / `Screens.kt` now use `PrognosisMonoFamily`.
No shipped code references the platform default or monospace families
at all. Verified: `aapt list` shows all 9 TTFs plus both family XMLs
inside the APK, and the unit tests assert the theme really uses the
bundled families.

**2. Background design made visible.** v6's login screen was plain
white with only a faint green tint (glow peak 0.30, motif 0.05, dots
0.10). v7, same positions as the approved preview, same lightweight
static Canvas: glow peak 0.60 light / 0.45 dark with a mid stop so the
visible edge reaches the form area; cropped ribbon-P motif 0.18 light
/ 0.22 dark; dot field 0.22, 5 rows x 8 columns, 2.6px dots. Dashboard
header band: glow 0.45, motif 0.18 (was 0.28 / 0.08). The Wallet
screen gets the cropped motif at the bottom right (0.12), as the
preview draws it, so it is no longer a large white void. The alphas
are named constants in `BackgroundDesign` (`Background.kt`) and a
unit test fails the build if they ever drift back below visibility.
The preview's own values were deliberately NOT copied literally: its
motif is 0.07 and its glow fades to transparent at 68%, and those are
the values that made v6 look plain white.

**3. Design-skill polish** (Taste Skill, Make Interfaces Feel Better,
Impeccable, UI UX Pro Max; the repos were read, 3-4 of them, not all
10 installed):
- Empty states are designed containers now, exactly as the preview
  draws them: dashed 1.5px border, 18dp radius, near-white surface,
  46dp lime-tinted icon circle, bold title, short body, action pill.
  Applied everywhere an empty state was a bare grey line: Markets no
  match, Notifications no alerts, market detail chart not available,
  Wallet transactions. The Dashboard / Portfolio designed empty states
  use the same container now.
- 44dp minimum touch targets: the market-row star was 32dp, now
  44dp (visible icon still 20dp). Header icon buttons are the 48dp
  Material default.
- Concentric radii: balance card 20dp, inner buttons 12dp, exactly as
  the preview. Balance value 33sp (was 36sp), login headline 32sp
  ExtraBold (was 34sp Bold), both exactly as the preview. The balance
  card also gains the preview's white dot field on its right edge.
- Tabular numbers: the bundled mono font is equal width, so a
  changing price never shifts the layout.
- A subtle 0.96 press scale on the auth primary button
  (`rememberPressScale` in `Design.kt`).
- Uizze's site is a paid product with no free skill content; UI UX
  Pro Max's README is mostly a design-system generator, and its
  usable rules (resilient text, compact UI, badges must not rely on
  colour alone) were already the app's behaviour.

Kept from v6: real logo image on splash and auth, TradingLock (one
source of truth, all flags false; Deposit/Withdraw disabled everywhere
with lock), dark balance card in both themes, auth copy "Prediction
markets, built for your phone.", 4-tab bottom nav, markets 8 at a time
with "Show more", asset dropdown, search, button tabs, forecast cards,
order book, watchlist (device only), notifications centre. No news on
home, no docs, no em dashes, no emojis, no AI/Gate/Sepolia wording in
shipped code.

## What changed in v6

The approved preview, built exactly. Nothing is invented beyond it.

- Deposit and Withdraw are disabled everywhere, the same treatment the preview fix applied to the mockups: greyed, with a lock icon, not enabled-looking, and they do nothing when tapped. Balance card (Dashboard, Portfolio, Wallet), quick actions on the Dashboard (Deposit and Withdraw disabled, Trade stays enabled and opens Markets), and the Portfolio Holdings "Deposit" button. The Deposit/Withdraw detail screens are deleted, and their navigation routes are gone; the Wallet note ("Deposits and withdrawals open with trading.") is the explanation now.
- One place owns the rule: new `data/TradingLock.kt` holds `tradingOpen`, `depositsOpen` and `withdrawalsOpen`, all false. The UI reads those flags, never a hardcoded true. A new `TradingLockTest` (3 tests) fails the build if any flag ever flips open by mistake. The Buy sheet Confirm button stays disabled and reads "Trading opens soon", as before.
- The balance card is a dark card in both themes (black background, white text, hairline border in dark mode so it still reads as a card). In v5 it flipped to white in dark mode; the preview calls it a dark card.
- Auth copy matches the preview: the supporting line is now "Prediction markets, built for your phone." and the primary button is lime in dark mode (black in light mode).
- Positions empty states on the Dashboard and Portfolio now read "Trading opens soon." exactly as in the preview.
- Kept from v5: real logo image on splash and auth, background design (lime glow, cropped ribbon motif, dot field, static Canvas), dark Dashboard header band, 4-tab bottom nav, markets 8 at a time with "Show more", asset dropdown, search, button tabs, forecast cards, order book, watchlist (device only), notifications centre. No news on home, no docs, no em dashes, no emojis, no AI/Gate/Sepolia wording in shipped code.

## What changed in v3

Design system first (`ui/Design.kt`, new file):
- One spacing scale (8pt), 16dp screen padding, 12dp card corners, one type scale (28sp screen title, 17sp section header, 15sp body, 13sp caption, 11sp mono label), one colour system. Section headers have a title on the left and a small "See all" action on the right. Every screen aligns to the same left edge, sections are separated by 24dp of space, and each screen has one primary thing on it.
- Shared pieces: `SectionHeader`, `EmptyState`, `AssetDot`, `BalanceCard`, `QuickAction`, `ChangeText`, `SlimTickerStrip`, `MarketRow`.

Dashboard, rebuilt from scratch:
- Header: greeting with first name and time of day ("Good morning/afternoon/evening/night") on the left; notification bell on the right with a red dot when there is an alert, dark mode toggle, log out.
- One balance card (total balance $0.00, Deposit and Withdraw inside it), a quick actions row (Deposit, Withdraw, Trade) as evenly spaced circular buttons, "Your positions" (honest empty state), "Top movers" as compact horizontal cards (asset, price, 24h change, tap to open Markets), "Watchlist" as compact rows.
- NO news anywhere on this screen. The top-stories strip is gone. No ticker tape.

Notifications centre (new screen, bell in the Dashboard header):
- Lists price alerts from the server watchdog, each with its market question. Empty state when there is none.

Bottom navigation, now 4 items: Dashboard, Markets, Portfolio, Wallet.

Portfolio (new tab):
- Total balance card, Holdings (honest empty state), Positions (honest empty state), Performance (total and all-time profit and loss, both $0.00), History (no transactions yet). No fake numbers.

Markets, rebuilt from scratch:
- Title and refresh, a slim ticker strip (small mono text, not a big tape), a rounded search field, small filter chips (All, Gainers, Losers, Watchlist), a compact asset dropdown and a compact sort menu (Ending soon, Highest price, Lowest price, Most one-sided). Gainers and Losers follow the asset's 24h move from the ticker.
- The market list is compact rows, one market per row, 64dp tall: asset dot and code, the question in up to two lines, price and change on the right, star to save. Rows are separated by hairline dividers. No big tabs, no website-style sector grid, no featured card.
- The list logic lives in `data/MarketList.kt` as pure functions (`filterMarkets`, `sortMarkets`, `topMovers`, `greetingForHour`), covered by unit tests.

Market detail, restyled:
- Cleaner header (question, asset and status, countdown), Up/Down prices with best bid and ask, forecast card, chart card, order book as a clean two-column table (each side shows its bids in green and asks in red, with a Price/Size header).
- Buy Up / Buy Down open a sheet with a segmented control to switch sides, an amount input, the price, and about how many shares that buys. Confirm is DISABLED and reads "Trading opens soon". No order is placed and no trade is faked.

Auth: kept from v2 (Splash -> Log in / Sign up -> Dashboard, local on-device only, SHA-256 password hash, note about server sign-in arriving with trading). No redesign, it was already clean.

Theme: crisp white light theme by default, dark mode toggle kept (dark #0B0B0B, white text, same lime accent). No ivory anywhere, including the launcher icon background now. No muted website palette.

Removed in v3:
- The news strip on the Dashboard and every news surface in the UI. The news API endpoint, its data model, and the saved news fixture remain only in the data layer and tests; nothing in the app fetches or shows news (`loadNews` has no callers).
- The big `MarketCard` and the featured market on the Markets screen. The `TickerTape` big tape is replaced by the slim strip. The `StatBox` row on the Dashboard is gone; the balance card and quick actions carry that job.

## What changed in v4

Visual design first, on top of v3's structure. No restart, no new screens, no new tabs.

Auth, fully designed (`AuthScreen`):
- Ribbon-P logo mark (new `RibbonLogo` in `ui/Design.kt`: black rounded square, white P, lime dot) next to the "Prognosis" wordmark and a "PREDICTION MARKET" kicker.
- Bold headline "Trade the question." (34sp) with the short line "Yes or No. Live prices. One question at a time."
- A "LIVE PRICES" strip from the real ticker, so the screen feels alive before you log in.
- Button tabs (new `SegmentedTabs`: scrollable pill row, selected is solid black) to switch Log in / Sign up, large rounded fields (14dp radius, 56dp tall), a big black primary button (54dp tall, 16sp bold). Auth is still local on-device only, SHA-256 hash, same note about server sign-in arriving with trading.

Dashboard, made interesting:
- New `HeroBalanceCard`: dark card, 20dp radius, 4dp elevation, a LIVE pill in lime, total balance in 40sp bold type, and a real sparkline (new `Sparkline` in `ui/Chart.kt`) of the first open BTC market's last-hour price, labelled honestly as market price with the current price. The chart is loaded once per market by `AppViewModel.loadHeroChart`; the market is picked by the pure, tested `heroMarketId` (first open BTC market, else first market). If the chart has not loaded yet, the card says "Live market chart loading." Balance is still $0.00, honest.
- Quick actions now have coloured circles: Deposit in lime with a black icon, Withdraw quiet, Trade in a black circle with a white icon (white circle with a black icon in dark mode).
- "Your positions" is now a designed empty state (`DesignedEmptyState`: icon in a tinted circle, bold title, short body, "Browse markets" button) instead of one grey line. Same for the Watchlist empty state.
- Top mover cards are wider (160dp), have a bigger price (17sp) and a change pill (`ChangePill`: +1.2% on a green tint, -0.8% on a red tint) instead of plain coloured text. Watchlist rows now show the same change pill.
- No news on the home screen, still. No ticker tape.

Portfolio, made interesting:
- Same hero balance card with the live sparkline as the Dashboard.
- Performance is now a card with three clear numbers in a row (`StatCell`): Total value, Today, All time, all $0.00, plus the saved markets count below it.
- Holdings, Positions and History are designed empty states with action buttons (Deposit, Browse markets). `PortfolioScreen` takes a new `onBrowseMarkets` callback; `MainActivity` passes it.

Markets, no more endless scroll:
- The list now shows 8 markets at a time (`PAGE_SIZE = 8` in `data/MarketList.kt`), then a full-width "Show more (N more)" button loads the next 8. When all are shown it says "Showing all N markets." The page resets to 8 whenever the search, filter, asset or sort changes.
- Filter chips are replaced by button tabs: All, Gainers, Losers, Watchlist, Ending soon. "Ending soon" sorts by ending soon. The asset dropdown menu (All assets + 8 assets with counts) and the sort menu are unchanged, and are the dropdown Flint asked for to find the rest by searching or picking an asset.
- Market rows now show the asset's 24h change as a pill on the right. The list logic stays in `data/MarketList.kt` as pure functions (`pageMarkets`, `remainingCount`, `heroMarketId`), covered by unit tests.

Market detail, restyled to match: forecast and chart cards now use 16dp radius and 2dp elevation. Buy sheet, order book, forecast and gating are unchanged.

Theme: `AppColors` gained an `isDark` flag so the hero sparkline uses lime on the black card in light mode and green on the white card in dark mode. No ivory, no muted website palette, no new colours.

## What changed in v5

Removals first, because Flint asked for them directly:

- The hero sparkline and the LIVE pill are gone from the Dashboard and Portfolio balance cards. `HeroBalanceCard` is deleted; both screens now use the clean `BalanceCard` (title, big balance, note, Deposit/Withdraw, nothing else). The `Sparkline` composable is deleted from `ui/Chart.kt`. The `loadHeroChart` state and the `heroMarketId` helper are deleted from `AppViewModel.kt` and `data/MarketList.kt`, and the two hero-market unit tests were removed with the feature (not faked). No BTC live price appears on the portfolio dashboard anywhere. The market detail chart stays: that is a chart that makes sense on its own screen.
- The code-drawn logo is gone. `RibbonLogo` is deleted from `ui/Design.kt`. The splash and auth screens now show the real logo image: `prognos_logo.png` (light, black ribbon P on white) and `prognos_logo_dark.png` (dark, white ribbon P on black), the v3 logo files copied into `app/src/main/res/drawable/`, picked by theme in `LogoImage` (`ui/Background.kt`). The launcher icon already used the real logo and is unchanged.

Background design (new `ui/Background.kt`):

- Auth and splash screens sit on `AuthBackground`: a lime glow top right, a giant ribbon-P motif cropped off the top right edge, and a dot field in the bottom left corner only, so the form stays readable. The motif is the real logo geometry drawn as a vector path (top bar, semicircular bowl, inner corner, dot) at 5% alpha in light mode, 7% in dark mode.
- The Dashboard header sits in `DashboardHeaderBand`: a dark band with rounded bottom corners, the same ribbon motif cropped off the right edge at 8% alpha, a lime glow, and a short lime rule at the bottom left. Greeting and icons inside the band are white in both themes. The list below stays on a clean surface.
- Cost: one static Canvas per screen, drawn once per composition and then still. No bitmap backgrounds, no blur, no animation, no per-frame work. The only bitmap in the app is the 16 KB logo itself. On an average phone the background costs a handful of draw calls at composition time and nothing after that.

Kept from v4: designed login/signup form (now with the background design and the real logo), Dashboard with balance card, quick actions, positions, top movers, watchlist; Portfolio with balance card and P&L summary; Markets showing 8 at a time with "Show more", asset dropdown, search, button tabs (All, Gainers, Losers, Watchlist, Ending soon); 4-tab bottom nav; forecast cards, order book, watchlist (device only), notifications centre; trading/deposit/withdraw gated and disabled with "Trading opens soon". No news on home, no docs, no emojis, no em dashes, no AI/Gate/Sepolia wording in shipped code. No new element was added that does not make sense on its own screen.

## Tests

- `gradle testDebugUnitTest --max-workers=1 --no-daemon`: **BUILD SUCCESSFUL** on the final run (45 passed, 0 failed, 0 skipped).
- ParsingTest, 8 tests, **8 passed, 0 failed, 0 skipped**, against real live fixtures saved 16 Nov 17:31-17:32 UTC in `app/src/test/resources/` (ticker, markets, market, book, chart, news, watchdog, learning).
- AuthValidationTest, 7 tests, **7 passed, 0 failed, 0 skipped** (pure JVM, no Android needed).
- MarketListTest, 13 tests, **13 passed, 0 failed, 0 skipped** (pure JVM): gainers/losers follow the asset move, watchlist filter, search spans question and asset, sort by ending soon / highest price / most one-sided, top movers take the biggest move, greeting follows the time of day, empty filter result is empty, page size is 8, pages show the first 8 only, remaining count. The 2 hero-market tests from v4 were removed with the hero sparkline feature.
- TradingLockTest, 3 tests, **3 passed, 0 failed, 0 skipped** (v6, pure JVM): trading, deposits and withdrawals are all closed.
- FontAndBackgroundTest, 8 tests, **8 passed, 0 failed, 0 skipped** (updated in v8, pure JVM): the theme's type scale uses the bundled Archivo family on all five checked styles; the 9 font resource IDs are distinct and non-zero; the auth glow (0.65/0.65), motif (0.15/0.18, now a SOLID image), dot field (0.22) and wallet motif (0.10) are all above their visibility minimums; the motif drawables `motif_light` / `motif_dark` exist; the mono family differs from the sans family. The header-band test was removed with the band.
- SettingsTest, 6 tests, **6 passed, 0 failed, 0 skipped** (new in v1, pure JVM): a new password needs 8 characters; change-password validation rejects a wrong current password, a short new password, and a mismatch, and accepts a valid change; the amount input keeps digits and dots only ("10.00" stays "10.00", "10a.00" becomes "10.00", "1,000" becomes "1000", "$10.00" becomes "10.00"). Settings navigation itself is a Compose NavHost route ("settings" in MainActivity) and is covered by the build, not by a unit test; tapping it needs an instrumented test.
- Total: **45 passed, 0 failed, 0 skipped.**
- The tests ran on the code in this APK. Five compile fixes on the way (Tab visibility, MoverCard weight scope, Adjust icon does not exist, StarOutline fully-qualified reference, one stray brace from the Markets rewrite), then the test run above passed clean.

## Static checks (shipped code, `app/src/main/`)

- No attribution markers in shipped code.
- Zero em dashes. Zero emojis. The only non-ASCII character in shipped code is the "·" middot in the detail meta row, which matches the preview's own "Bitcoin · BTC".
- Zero API keys, secrets, passwords, key references. The app uses no key at all; base URL is `https://prognoslabs.fruitslice.xyz/` (https only). Only a SHA-256 hash of the locally entered password is stored on the device, never the password itself.
- No Gate, Sepolia or Broker wording in shipped code (the only "gate" match is the word "navigate" inside code). The lock object was first named `TradingGate` in v6; that name itself tripped the check, so it was renamed to `TradingLock` and the word "gated" was removed from code comments. The lock is documented here and in the README only, not in the app.
- No ivory anywhere, including resources. No documentation strings, no Docs screen, no news surface in the UI.
- No LIVE pill, no "LIVE PRICES" label, no hero sparkline, no hero-chart code anywhere in the app. The only "live" wording left is the honest sentence "There is no live price to work from" on the forecast card. No code-drawn logo anywhere: the logo is the real `prognos_logo.png` / `prognos_logo_dark.png` drawable, and the motif is the real `motif_light.png` / `motif_dark.png` image rendered from the logo v3 geometry, not a drawing in code.
- No system font fallback anywhere: zero `FontFamily.Default`, `FontFamily.Monospace`, `FontFamily.SansSerif`, `FontFamily.Serif` or `FontFamily.Cursive` in shipped code. Every text element uses `PrognosisFontFamily` or `PrognosisMonoFamily`, or inherits them from `PrognosisTypography`.
- DEX verified to contain the app classes; APK contains the launcher icons in all densities, both real logo drawables `prognos_logo.png` / `prognos_logo_dark.png`, both motif drawables `motif_light.png` / `motif_dark.png`, and all 9 bundled font files plus both font-family XMLs (`res/font/archivo.xml`, `res/font/plex_mono.xml`, `archivo_regular/medium/semibold/bold/extrabold.ttf`, `plexmono_regular/medium/semibold/bold.ttf`). Deposit/Withdraw are disabled everywhere: `BalanceCard` buttons are `enabled = false` with lock icons, the Dashboard quick actions pass `enabled = false`, the Portfolio Holdings button reads `TradingLock.depositsOpen` (false), the Wallet Deposit/Withdraw pills do nothing, and no Deposit/Withdraw detail screen or route remains.

## What was verified live vs not

- Verified: the APK builds, signs (debug key), installs structurally (aapt badging clean: package `xyz.fruitslice.prognos`, versionCode 9, versionName 1.0.0, min SDK 26, target SDK 34, INTERNET + ACCESS_NETWORK_STATE), parses real live API responses (fixtures are real responses captured from the live site), and the forecast thresholds match the website rule. Auth validation, market-list filtering/sorting/pagination, and the honest empty states are covered by unit tests. The background design is static image + vector work: one Canvas plus one Image per screen, drawn once per composition, no animation, no per-frame work, no bitmap backgrounds, so it costs a handful of draw calls at composition time and nothing after that. The auth and wallet backgrounds were verified by rendering the exact app values (same PNG, size, position, alpha) in HTML and screenshotting them: the motif reads clearly in both themes on both screens.
- NOT verified: launching the app on a real phone or emulator. No emulator was run in this VM (RAM too tight: 7.7 GB total, ~500 MB free during the build). **Flint's phone test is the real proof.** Install the APK, sign up once, and confirm the Dashboard loads with live markets. Download it in Chrome, not DuckDuckGo: DuckDuckGo saves it as a `.bin` file and its file provider will not let a file manager copy it out, so the installer never appears (seen on v1, 16 Nov ~21:12 WAT).
- NOT verified live: the win line on a real market (same caveat as the website: no listed market window had started at build time; the chart shows the last hour with "Win line not set yet" and a neutral fill until one starts).
- The learning endpoint reported 0 scored forecasts at fixture time, so no challenger has been promoted; the forecast uses the starting formula.

## Known limits

- Same as the website: win line unseen live when no window has started; some markets have no live price yet (empty best bid/ask, volume 0) and the app shows "-" honestly, with no forecast for those markets.
- Auth is local only. An account made on one phone does not exist on another. Server sign-in arrives with trading.
- Trading, deposits and withdrawals are all gated and disabled in this build. They open with trading. The trade panel is inline on the detail screen, and its "Trading opens soon" button is disabled; no order is placed.
- Debug build only, signed with the debug key. Not for the Play Store. No release keystore was created.

## Environment notes (this VM only, keep for rebuilds)

Building here needed three workarounds, all documented in README.md and already in place:

1. **Daemon/client hang (IPv6):** Java defaults to IPv6-mapped sockets (`::ffff:127.0.0.1`), which this sandbox resets/hangs. The Gradle daemon therefore never received builds. Fix: `JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"` (the flag in `org.gradle.jvmargs` does NOT reach the daemon JVM command line, so it must come through `JAVA_TOOL_OPTIONS`). Proven with a Java localhost socket test.
2. **Maven Central unreachable from Java directly** (read timeouts) and `plugins.gradle.org` unreachable entirely (TLS handshake hangs), while `dl.google.com` works direct. Fix: `~/.gradle/init.d/proxy.gradle` sets the egress proxy from the `https_proxy` env var at build time (no credential stored; the proxy accepts unauthenticated requests and resets connections that present Proxy-Authorization), with `dl.google.com` bypassing the proxy (the proxy MITMs it with a cert Java does not trust). `settings.gradle.kts` redirects the two Kotlin plugin markers to their real Maven Central modules (`kotlin-gradle-plugin` and `compose-compiler-gradle-plugin`).
3. **JDK wiped by a VM restart mid-build** (apt-installed JDK lives outside the home dir and does not survive). Fix: persistent Temurin JDK 17 tarball at `~/workspace/prognos-labs/jdk-17/`. The Android SDK, Gradle 8.7 and the Gradle caches all live under `~/workspace` or `~/.gradle` and survived.
4. **Gradle wrapper unusable here:** `./gradlew --version` fails downloading `gradle-8.7-bin.zip` from services.gradle.org (PKIX: proxy intercept cert not trusted by Java). Use the standalone Gradle.

## Files

- Source: this folder (`app/src/main/java/xyz/fruitslice/prognos/`, `app/src/main/res/`, `app/src/test/`)
- `README.md` - what v1 includes/excludes, install steps, rebuild steps, Play Store needs
- `build-log-assembleDebug-v1.txt` / `build-log-testDebugUnitTest-v1.txt` - successful v1 logs (45 passed)
- `build-log-assembleDebug-v6.txt` / `build-log-assembleDebug-v6-final.txt` - successful v6 assembleDebug logs (first build, then the clean rebuild after the TradingLock rename)
- `build-log-testDebugUnitTest-v6.txt` - successful v6 unit test log (31 passed)
- `build-log-assembleDebug-v5.txt` - successful v5 assembleDebug log (after two compile fixes)
- `build-log-testDebugUnitTest-v5.txt` - successful v5 unit test log (28 passed)
- `build-log-assembleDebug-v4.txt` / `build-log-assembleDebug-v4-final.txt` / `build-log-testDebugUnitTest-v4.txt` - the v4 logs, kept for reference
- `build-log-assembleDebug-v3.txt` / `build-log-testDebugUnitTest-v3.txt` - the v3 logs, kept for reference
- `build-log-assembleDebug-v2.txt` / `build-log-testDebugUnitTest-v2.txt` - the v2 logs, kept for reference
- `build-log-assembleDebug.txt` / `build-log-testDebugUnitTest.txt` - the v1 logs, kept for reference
- `BUILD_REPORT.md` - this file
