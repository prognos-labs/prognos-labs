# Prognos Labs app v1.1.4 — site files to upload

These files go on prognoslabs.xyz (public_html). Nothing here is deployed
automatically; Flint uploads them from his phone with Cx File Explorer, the same
way as the trading and auth files.

## What changed in v1.1.4

- Every listed asset now shows its real official logo next to its name:
  market cards, the featured market, the sector tabs, the price ticker,
  the market page and the watchlist rows. The logos live in `asset-icons/`
  (BTC, ETH, SOL, ZEC, XRP, DOGE, BNB, HYPE), bundled with the site,
  nothing hotlinked. An asset with no bundled logo keeps the coloured
  dot instead of a made-up mark.
- Fixed: `logo-light.png` and `logo-dark.png` were missing from the
  v1.1.3 folder even though index.html loads them (favicon, header
  logo, footer logo, featured ribbon). Both are now bundled here, copied
  from the real Prognos Labs logo v3: the black mark for light
  backgrounds, the white mark on black for dark backgrounds.
- Everything else is the v1.1.3 build unchanged: the new forecast, the
  app link patch, the partner lock, linked.html and assetlinks.json.

## What each file is

- `htaccess.txt` — the partner lock, narrowed. Locks ONLY index.html now,
  so the app can call api.php while the website stays locked. Rename to
  `.htaccess` on upload (dotfiles are hidden in Cx File Explorer, so it
  travels as htaccess.txt and is renamed on the server). This is safe:
  the live api.php is the hardened build (104 kB, session token required
  for every trading call, orders capped at 1,000,000 USDT).
- `index.html` — the trading site. Two things in it: the NEW forecast
  (a short analyst note with a market label like "Tense" or "Quiet",
  built from the price move, the headlines, the mood, the order book
  and the time left, ending on what comes next; a wall is only named
  at $1,000 or more, a thin book is skipped), and the app link patch
  (it uses the app's nonce (?app_link_nonce=) when the page was opened
  from the app, and after a successful link it redirects to
  /linked?session=...&address=... so the app gets the session back).
  Replaces the live index.html. The website stays behind the partner
  lock; only the app API is freed.
- `asset-icons/` — the official asset logos (see above). Upload the whole
  folder as `asset-icons/` in public_html, beside index.html.
- `logo-light.png` — the Prognos Labs logo v3, black mark, for the
  favicon and the header (light backgrounds).
- `logo-dark.png` — the Prognos Labs logo v3, white mark on black, for
  the footer and the featured-market ribbon (dark backgrounds).
- `linked.html` — the page /linked serves. Shows "Wallet linked", then
  hands the session back to the app (prognoslabs://linked deep link).
  Upload as `linked.html` in public_html (the clean-address rule serves
  it at /linked). Public, not locked.
- `.well-known/assetlinks.json` — App Links verification for the
  /linked return. Upload so it is reachable at
  https://prognoslabs.xyz/.well-known/assetlinks.json (create the
  .well-known folder in public_html if it is not there). The fingerprint
  is the SHA-256 of the debug keystore that signs the v1.1.3 APK
  (~/workspace/prognos-labs/debug.keystore, also at
  /root/.android/debug.keystore). If the VM is replaced before the APK
  is built, recreate that keystore from the workspace copy first, or
  the fingerprint will not match the APK.

## Upload order

1. Upload `htaccess.txt`, rename to `.htaccess` (replaces the live one).
   Check the app can now load prices (the lock on index.html stays).
2. Upload `index.html` (replaces the live one).
2a. Upload the `asset-icons/` folder, `logo-light.png` and `logo-dark.png`
   (new in v1.1.4; without them the asset logos and the site logo
   will not show).
3. Upload `linked.html` to public_html.
4. Upload `.well-known/assetlinks.json` (create the .well-known folder).
5. Install the v1.1.4 APK in Chrome, then test:
   - Sign up with a fresh email, enter the code, and confirm the
     "Registration complete" note shows and the login code step follows.
   - Log in, tap "Forgot your password?", send a code, enter it, set a
     new password, and log in with it.
   - On the OTP screen, confirm "Send a new code" is replaced by a
     60-second countdown after a code is sent.
   - In Settings, change the wallet (sign in the browser), change the
     currency, and set a default trade size. Confirm the dashboard
     balance shows the BTC equivalent beside the dollars.
   - On the dashboard, Markets and the market page, confirm every
     asset shows its real logo (Bitcoin orange B, Ethereum diamond,
     Solana bars, ZCash Z, XRP X, Dogecoin D, BNB diamond,
     Hyperliquid mark), not a letter circle.
