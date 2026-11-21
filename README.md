# Prognos Labs

Prognos Labs is a prediction market with AI forecasting built in. Every market asks one question about what happens next, with two outcomes. The price shows the chance traders give each outcome, and it moves with every trade. Every market carries a forecast with a plain-language reason for it. When a market settles on the real outcome, the forecast it gave is scored against that outcome, so the next forecast is sharper than the last.

Live site: https://prognoslabs.xyz

## What is in this repo

- `website/` - the prediction market website. It shows live markets, the forecast on every market, price alerts, market news, and a watchlist.
- `android-app/` - the native Android app (Kotlin, Jetpack Compose), currently v1.1.5. It signs up and signs in against the website auth backend, shows live markets with the forecast, and links a wallet by signature.

## Running the website

The website is plain PHP and HTML. Put the contents of `website/` in a web folder with PHP, and open `index.html`. The `api.php` file fetches live market data and records forecasts for scoring. The `cache/` folder is blocked from the web by its `.htaccess` file, so learning records and price caches are never readable over the web. Do not remove that block.

## Running the app

Open `android-app/` in Android Studio and run it. APKs are published as GitHub releases, not committed to the repo.

## How markets settle

A market settles on the real outcome of the event its question describes. A person confirms the outcome. The forecast never creates a market and never settles one.

## License

MIT.
