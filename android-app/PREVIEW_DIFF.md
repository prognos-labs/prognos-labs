# Preview vs app: measured side-by-side comparison

Flint, 17 Nov 2026 ~10:18 WAT: "look at the design preview and what you've sent me, they are very different and do not look anything alike."
Flint, 17 Nov 2026 ~10:20 WAT: even INSIDE the app is not the same as the design preview.

He is right. This file is the measured comparison of the APPROVED design preview
(artifact `prognos-app-design-preview`, 8 phone mockups, exported and rendered locally
with Playwright Firefox, geometry probed from the live DOM + CSS) against the v7 app
(Flint's dark-login screenshot `SmartSelect_20261117_101707_Prognosis.png`, 1440x3020,
measured with a pixel scan at 3.692 px/dp; his v6 58s screen recording, frames at
`/tmp/v6video/`; and the v7 source in `app/src/main/java/xyz/prognoslabs/app/`).

Phone frame in the preview: 390 CSS px wide, 892 tall (includes a 9:41 status bar).
All preview positions below are CSS px in that frame. All v7 positions are dp on
Flint's phone (1080x2400 logical, 3.692 px/dp, app content starts ~30dp below the
top of the screenshot).

## How v6/v7 went wrong, in one paragraph

v6/v7 were built from the preview's *description* (glow + motif + dots, dark balance
card, 8 markets, gated trading) but never from its *measurements*. The builders kept
v1-v5 structures that the preview never had (dark dashboard header band, scrolling
ticker strip, separate tab pills, two-column order book, bottom-sheet trade panel,
vertical centred empty states) and invented others (a "PREDICTION MARKET" subtitle,
a "LIVE PRICES" label, a Markets title row with refresh icon, a sort dropdown, a
"Saved markets" row). The background motif was drawn as a thin STROKE outline on top
of the brightest part of the glow, so it washed out completely on a real phone,
while the preview's motif is a SOLID image with a blend mode. v8 rebuilds every
screen from the measurements below.

## 1. Login light + dark (preview phones 1-2 vs v7)

### Preview, measured (both themes; dark values in brackets)
- Status bar inside the frame: y12-48.
- Brand row: x32, y62, 326x36. Logo image 36x36, radius 9px. Wordmark "Prognosis"
  18px / weight 800, letter-spacing -0.02em. NO tile, NO "PREDICTION MARKET" subtitle.
- Headline "Trade the question.": y128, 31px / 800, letter-spacing -0.025em,
  line-height 1.12.
- Sub "Prediction markets, built for your phone.": y172.7, 15px, #6A6A66 [#A9A9A3].
- Prices: a 3-column CARD at y216.5, 326x78, radius 16, bg #F7F8F2 [#141414],
  border 1px #ECEEE2 [#23261C]. 3 cells, each ~103px wide, centred text, 1px
  dividers #E7E9DC [#262626] between cells. Cell: code 11px/700 grey mono, price
  14.5px/700, change 11.5px/700 green #1E7D32 / red #C62828 [dark #7BC98A / #E88A8A].
  Assets BTC / ETH / SOL. NO "LIVE PRICES" label, no scrolling, nothing cut off.
- Tabs: ONE container at y318.5, 326x52, bg #EFEFEC [#1B1B1B], radius 999, padding
  4px. Active pill 159x44 at (36,322.5), 15px/700, black bg + white text [white bg +
  black text], radius 999. Inactive 15px/700 #7A7A74.
- Fields: y384.5 and y458.5, 326x60, radius 16, border 1.5px #E4E4DF [#2A2A2A],
  bg white [#131313], padding 10px 16px 12px. Static label 11.5px/600 #9A9A94 at the
  top INSIDE the field, placeholder/value 15.5px below it. Eye icon on the password
  field, right 14px, vertically centred.
- Primary button: y538.5, 326x56, radius 16, black bg + white text [lime #C8F542 bg
  + black text], 16.5px/700.
- Note: y610.5, 12.5px, CENTRED, #8A8A84 [#7E7E78], line-height 1.5.
  "Accounts on this device for now. Server sign-in arrives with trading."
- "No account yet? Create one": y658, 14px, centred, "Create one" bold with a
  2px lime underline.
- Glow: 340x340 circle, top:-130 right:-110 (centre ~ (330,40) in the 390x892
  frame), radial lime 0.65 peak fading to 0 at 68%.
- Motif: a SOLID PNG image of the ribbon P, 380x380, top:-120 right:-165,
  opacity 0.07 light with mix-blend-mode multiply / 0.12 dark with screen.
  It reads as a shape because it is a solid silhouette, not an outline.
- Dots: 170x120 at left:0 bottom:0, 1.6px dots, rgba(17,17,17,0.20) [rgba(255,255,255,0.22)],
  15px grid.
- Content ends at y~693 ("Create one"); dots fill the bottom to y880.

### v7, measured (Flint's dark-login screenshot + source)
- Logo: 56dp TILE (white square + black P in light; pure-black square + white P
  in dark), radius 14dp, at ~(31,58)dp. 20dp bigger than the preview, and on dark
  the #000000 tile on the #0B0B0B background nearly disappears. PLUS a
  "PREDICTION MARKET" mono subtitle under the wordmark that the preview does NOT have.
  Wordmark 26sp bold (preview 18px).
- Top spacer 56dp (source) vs preview ~14px below the status bar. Everything is
  pushed down: headline measured at 146-166dp vs preview y128.
- Headline: 32sp ExtraBold (preview 31px) — close, but 18-38dp lower.
- Sub: 15sp grey — matches.
- Prices: a "LIVE PRICES" 11sp mono label (preview has NO label) + SlimTickerStrip:
  a horizontal SCROLLING strip, mono 11sp on surface2, all 8 assets. In Flint's
  screenshot the strip is CUT OFF at the right edge ("+1.4%" and "SO" half visible).
  Preview: fixed 3-column card, BTC/ETH/SOL, nothing cut off.
- Tabs: SegmentedTabs — separate pills in a scrolling row, NO container, 13sp
  bold, radius 20, selected = solid primary, unselected = surface2. Preview: one
  pill container + sliding pill, 15sp/700.
- Fields: OutlinedTextField 56dp (measured 56dp), radius 14dp, Material floating
  label that animates, NO eye icon on password, NO static label + placeholder
  two-line layout. Preview: 60px, radius 16, 1.5px border, static label inside +
  placeholder, eye icon.
- Primary button: 54dp high (measured 55dp), radius 16, colours match the preview
  (black/white light, lime/black dark). Position y492-547dp vs preview y538.5-594.5:
  the v7 button sits ~46dp higher, because the strip/tabs/fields stack is shorter.
- Note: LEFT-aligned, 13sp, at 565-586dp. Preview: CENTRED 12.5px at y610.5.
- "No account yet? Create one": MISSING entirely in v7.
- Glow: peak 0.60 light / 0.45 dark, centre (0.92w, -0.05h), radius 1.05w. Visible
  in Flint's screenshot as a green wash top-right, but bigger and more off-screen
  than the preview's 340px circle (0.65 peak).
- Motif: drawn as a STROKE outline (120-unit stroke) of the ribbon P at alpha
  0.18 light / 0.22 dark, offset (0.35w, -0.28h), size 1.15w. In Flint's screenshot
  NO motif shape is visible at all. Root cause: (a) it is an outline, not the
  preview's solid silhouette; (b) it sits on top of the brightest part of the
  glow, white-on-lime at low alpha, so there is no contrast; (c) the preview's
  motif uses multiply/screen blend, which is what makes a faint shape read.
- Dots: 5 rows x 8 cols, 18dp steps, 2.6px radius, alpha 0.22, measured in
  Flint's screenshot at 696-770dp. Preview: 170x120, 1.6px dots, 15px grid,
  alpha 0.20/0.22. v7's dots are bigger (2.6px radius = 5.2px diameter vs 1.6px)
  and sparser.
- Fill: v7 content ends at the note (~586dp) on an 818dp-tall screen, leaving
  ~230dp of near-empty black below (only faint dots at 696-770dp). The missing
  "Create one" line, the taller logo stack and no bottom anchoring make the void.
  The preview's content ends at "Create one" (~693) with the dot field filling
  760-880.

## 2. Dashboard light + dark (preview phones 3-4 vs v7 DashboardScreen + v6 frame_038)

### Preview, measured
- Header: NO dark band. Plain background. "Good evening" 13px grey + name "Flint"
  19px/800 on the left. Right: bell icon-button 40px circle (1px border, white /
  #161616 bg) with an 8px lime notification dot (2px border); moon/sun toggle
  40px circle; avatar circle 40px ("F", black bg + lime text [lime bg + black text]).
- Balance card: black #111111 [dark #1B1B1B], radius 20, padding 18px. Label
  12.5px/600 #B9B9B3, value 33px/800, Deposit/Withdraw buttons inside (row, gap
  10px). Dot field on the right edge, 130px wide, full height.
- Quick actions: 3 circles 50px, gap 8px, labels 12.5px/600 below. Deposit /
  Withdraw circles: light #F1F6DE (pale lime) with lock; dark: Deposit #232B12
  with lime lock, Withdraw #1C1C1C with white lock. Trade: black circle with lime
  swap-arrows icon [lime circle with black arrows].
- "Your positions": section header 17px bold. Empty state = dashed-border
  container, HORIZONTAL layout: icon circle 46px (lime tint #EFF5D8, target icon
  #5B7014) on the LEFT, text in the middle ("No open positions yet" 15.5px/700 +
  "Trading opens soon." 13px grey), "Browse markets" pill button on the RIGHT
  (black bg + white text [lime bg + black text], min-height 40px, radius 999,
  padding 0 20px).
- "Top movers": 3 cards in ONE row (flex:1, not scrolling), each bordered,
  radius 14, padding 10px 12px: letter circle + code 12.5px/800, price 13.5px/700,
  change pill 10.5px.
- "Watchlist": rows with letter circle + name 14px/700 + code 11.5px grey, price
  14px/700 + change pill on the right, star. Divider 1px #F1F1ED between rows.
- Bottom nav: icons + labels, active = bold label + 5px lime dot under the icon.
  Icons: home, bar chart, pie, wallet.

### v7, measured
- Header: DashboardHeaderBand — a DARK band (black #111111 light / #1F1F1F dark)
  with rounded bottom corners, lime glow, ribbon motif and a lime rule at the
  bottom left. Content white in both themes. THIS BAND DOES NOT EXIST IN THE
  PREVIEW. Greeting 13sp + name 22sp bold white; bell / moon / logout icon
  buttons with no borders and no avatar. NO avatar circle at all. A logout icon
  the preview does not have.
- Balance card: matches the preview closely (dark card, 33sp value, dots on the
  right edge, disabled Deposit/Withdraw inside). v7 Deposit button uses a lime
  container colour (disabled to #2E2E2E); preview's card buttons are grey with locks.
- Quick actions: circles 56dp (preview 50px), Deposit/Withdraw = surface2 grey
  circles with lock (preview light: pale-lime #F1F6DE circles), Trade = black
  circle with a ShowChart icon (preview: swap-arrows icon, lime on black).
  Arrangement SpaceEvenly (preview: flex:1 columns).
- Positions: DesignedEmptyState = VERTICAL centred column (icon circle on top,
  title, body, button below). Preview: HORIZONTAL (icon left, text middle, button
  right). v7 icon is ShowChart; preview's is a target icon.
- Top movers: horizontal SCROLLING row of 160dp-wide cards, elevation 2dp, small
  asset dot + code, 17sp price, ChangePill. Preview: 3 FIXED cards in a row,
  letter circles, 13.5px price, 10.5px pill. v7's row scrolls and only some cards
  are visible.
- Watchlist: v7 reuses MarketRow (10dp dot + code inline, question, price +
  outcome label, star) with a HorizontalDivider; empty state is the vertical
  DesignedEmptyState. Preview: letter circles + name + code, price + change pill,
  star, #F1F1ED dividers.
- Bottom nav: Material3 NavigationBar with the default selected-indicator pill
  behind the active item. Preview: no pill, active = bold label + lime dot.

## 3. Markets (preview phone 5 vs v7 MarketsScreen)

### Preview, measured
- Search: rounded field, magnifier icon, "Search markets", bg #F4F4F0, border
  #ECECE8, radius 14, min-height 44px.
- Row: "All assets" dropdown pill (1.5px border, radius 999, min-height 38px,
  13.5px/700, chevron) + "100 markets" grey text to its right.
- Tabs: TWO WRAPPED rows of outlined pills: "All" (black active), "Gainers",
  "Losers", "Watchlist" on row 1, "Ending soon" on row 2. Pills have 1px borders,
  radius 999.
- Rows: 64px tall, hairline divider #F1F1ED. Left: letter circle (coloured, first
  letter) with the asset code BELOW it (9.5px/800 grey). Middle: question
  13.5px/600, up to 2 lines. Right: price 14.5px/800 + change pill 10.5px below.
  Star at the far right (gold filled / grey outline).
- "Show more (92 more)": full-width OUTLINED button, bold text.
- NO "Markets" title at the top, NO refresh icon, NO ticker strip, NO sort
  dropdown, NO "N markets" count line.

### v7, measured
- Title "Markets" 28sp bold + refresh icon button at the top. The preview has
  no title row.
- SlimTickerStrip (scrolling mono strip) under the title. The preview has no
  strip on Markets.
- Search: OutlinedTextField, radius 24dp, single-line, placeholder "Search
  markets", leading search icon. Preview: radius 14, bg #F4F4F0, min-height 44.
- Tabs: SegmentedTabs — ONE scrolling row of pills (13sp bold, radius 20,
  selected = solid primary, unselected = surface2). Preview: two wrapped rows of
  OUTLINED pills with a black active pill.
- Dropdowns: sector OutlinedButton AND a sort OutlinedButton ("Ending soon" /
  "Highest price" / "Lowest price" / "Most one-sided"). The preview has NO sort
  dropdown; "Ending soon" is a TAB. Plus a "N markets" mono count line the
  preview does not have.
- Rows: MarketRow — 10dp asset DOT + code inline ("BTC  -  OPEN"), question 15sp
  Medium, price 16sp bold mono + outcome label ("UP") + optional ChangePill,
  star 44dp hit area. Preview: letter CIRCLE + code below, question 13.5px/600,
  price 14.5px/800 + change pill ALWAYS under the price.
- Show more: OutlinedButton 48dp, radius 12. Preview: outlined button. Close.

## 4. Portfolio (preview phone 6 vs v7 PortfolioScreen)

### Preview, measured
- Title "Portfolio" ~22px bold.
- Same black balance card with dots on the right edge.
- P&L: 3-column bordered card (Total value / Today / All time, each $0.00),
  hairline dividers, radius ~16. Labels 12.5px/600 grey, values 16px bold.
- Holdings: dashed container, HORIZONTAL: layers icon in a lime-tinted circle,
  "No holdings yet" + "Your assets will show here once you deposit.", grey
  "Deposit" pill button with a lock on the right.
- Positions: dashed container, HORIZONTAL: target icon, "No open positions
  yet" + "Trading opens soon.", black "Browse markets" pill on the right.
- History: dashed container, HORIZONTAL: clock icon, "No history yet" +
  "Settled markets will show here." (no button).
- Section titles 17px bold ("Holdings", "Positions", "History").

### v7, measured
- Title "Portfolio" 28sp bold (preview ~22px).
- Balance card matches.
- Performance: bordered Card with StatCell row — labels "TOTAL VALUE" / "TODAY" /
  "ALL TIME" in 11sp mono CAPS + 16sp mono values, elevation 2dp. Preview: labels
  12.5px/600 grey (not mono caps), values 16px bold. PLUS a "Saved markets / N"
  row below it that the preview does NOT have.
- Holdings / Positions / History: vertical centred DesignedEmptyState (icon on
  top, title, body, button below). Preview: HORIZONTAL (icon left, text middle,
  button right).
- Copy differs: v7 Holdings body "Holdings appear when you own a position."
  (preview: "Your assets will show here once you deposit."); v7 History title
  "No transactions yet" + "Your deposits, withdrawals and trades will show
  here." (preview: "No history yet" + "Settled markets will show here.").

## 5. Wallet (preview phone 7 vs v7 WalletScreen + v6 frame_058)

### Preview, measured
- Title "Wallet" ~22px bold.
- Balance card: "Wallet balance" + "$0.00". NO buttons inside the card. Dots on
  the right edge.
- Deposit / Withdraw: two separate GREY pills BELOW the card (lock icons,
  greyed), side by side, ~48px tall, radius ~14.
- Note centred: "Deposits and withdrawals open with trading." grey.
- "Transactions" section header; empty state = VERTICAL centred dashed container:
  receipt icon in a lime circle, "No transactions yet" bold, "Deposits,
  withdrawals and settled trades will show here." grey.
- A ribbon-P motif at the BOTTOM RIGHT, measured from the preview's own
  markup: a separate 230x230 image at right:-70px, bottom:60px, opacity
  0.05, multiply blend. It reads as a large light-grey shape because the
  whole P fits inside the visible window. (The login motif is a different
  element: 380x380 at top:-120, right:-165.)
- Bottom nav Wallet active.

### v7, measured
- Title "Wallet" 28sp (preview ~22px).
- BalanceCard REUSED — it includes Deposit/Withdraw buttons INSIDE the card.
  The preview wallet card has NO buttons inside; they are separate pills below.
  Wrong component reuse.
- No separate Deposit/Withdraw pills below the card, no centred note (the note
  lives inside the card).
- Transactions: vertical centred DesignedEmptyState — layout matches the preview,
  but the body copy differs ("Your deposits, withdrawals and trades will show
  here." vs preview "Deposits, withdrawals and settled trades will show here.").
- WalletBackground motif: a stroked outline at 0.12 alpha, width 0.62w at the
  bottom right. Preview: a separate 230x230 solid image at right:-70,
  bottom:60, opacity 0.05 multiply. In the v6 video (frame_058) the wallet
  had NO visible motif at all (plain white). v7's 380dp stroked outline at
  bottom:-120 right:-165 would show almost nothing: the visible window of
  that placement contains only a sliver of the P's top-left corner.
- The screen below the empty state is a void in v7; the preview fills the bottom
  right with the huge motif.

## 6. Market detail (preview phone 8 vs v7 MarketDetailScreen + v6 frames 045/052)

### Preview, measured
- Top row: back button in a CIRCLE OUTLINE (40px) + "Markets" grey text 15px.
  NO star.
- Question "Bitcoin Up or Down, 5 min" 22px/800.
- Meta row: orange letter circle "B" + "Bitcoin · BTC" grey + green dot +
  "3:24 left" mono, all in ONE row.
- Big price "$97,240" ~30px/800 + change pill "+2.4%".
- Forecast card: PALE LIME bg (#EFF5D8-ish), radius ~16: a "FORECAST" black pill
  with lime text + "Up, 62%" bold, reason text grey below.
- Chart: "Win line" label top-left, dashed win line, price line with a lime fill
  below it and a green dot at the end. Below: "Average 0.50" left, "Now 0.52,
  above the win line" right, grey mono.
- Order book: ONE rounded table (border, radius ~14): header "Price / Size";
  asks in RED on top (0.55/1,240, 0.54/860, 0.53/2,310); a "Spread 0.02" grey
  divider row; bids in GREEN below (0.52/3,480, 0.51/1,975, 0.50/4,120).
- Buy Up / Buy Down: a segmented control — "Buy Up" GREEN solid (#1E7D32) white
  text, "Buy Down" grey (#F4F4F0) dark text, in one rounded container.
- Amount: an outlined rounded field row: "Amount" label left, "$10.00" bold right.
- "About 19 shares at 0.52" centred grey.
- "Trading opens soon": a big GREY disabled button (full width, ~56px, radius 16)
  as the final element. Everything is INLINE on the screen; there is no bottom sheet.

### v7, measured
- Top row: back arrow with NO circle outline + "Market" 18sp bold + a star
  button on the right. Preview: circle-outline back + "Markets" grey, NO star.
- Question 24sp bold (preview 22px). Close.
- Meta: 10dp dot + "ETH  -  OPEN" mono 11sp, then a separate line "CLOSES IN
  23h 10m 12s" mono 12sp. Preview: letter circle + "Bitcoin · BTC" + green dot +
  "3:24 left" in ONE row, plus a big price + change pill.
- NO big price row. Instead two columns of 36sp percentages (UP 0.52 / DOWN
  0.48) with "Best bid - / Best ask -" below. The preview shows the underlying
  asset price ($97,240) + change pill; v7 shows the two outcome prices as huge
  numbers.
- Forecast: a white Card with a "Forecast" 16sp bold title + plain text. Preview:
  pale lime card with a "FORECAST" pill + "Up, 62%" + reason.
- Chart: inside a white Card titled "Price over the window" (16sp bold) with
  "Price now / Average" + "Win line not set yet" + extra text below. Preview: no
  card border, "Win line" label, dashed line, lime fill, green end dot,
  "Average 0.50 / Now 0.52, above the win line" below. (v7's PriceChart does draw
  a fill + dashed win line + end dot, but the end dot is ink-coloured, not lime/
  green, and the fill is green 0.15 / red 0.15 instead of the preview's lime.)
- Order book: TWO columns (BookSide Up | BookSide Down), each with a "Price /
  Size" header, a "Bids" green label + rows, an "Asks" red label + rows, and
  "No bids yet" / "No asks yet" text. Preview: ONE table, asks red on top, spread
  row, bids green below, no "Bids/Asks" labels, no "No bids yet" text.
- Buy buttons: "Buy Up" LIME solid + "Buy Down" RED solid, full-width row.
  Preview: "Buy Up" GREEN + "Buy Down" GREY segmented control.
- Trade panel: a ModalBottomSheet ("Buy" title, Up/Down segmented, Price row,
  Amount (USD) field, "About N shares", disabled "Trading opens soon" button,
  note text). Preview: everything INLINE on the detail screen itself, no sheet.
- v7 also has a small centred "Trading opens soon. Orders open when trading
  opens." line under the buy buttons; the preview has the big grey "Trading
  opens soon" button as the final element.

## 7. What v7 got right (keep it)
- Bundled fonts: Archivo + IBM Plex Mono in the APK, app-wide typeface, never the
  phone's system font. Flint's v7 screenshot renders in clean sans. KEEP.
- Primary button colours (black/white light, lime/black dark) and radius 16.
- Dark balance card in both themes, 33sp value, dots on the right edge.
- TradingLock: one source of truth, all flags false; Deposit/Withdraw disabled
  with lock everywhere; no Deposit/Withdraw detail screens or routes. KEEP.
- Markets show 8 at a time with "Show more", search, asset dropdown, button tabs.
- Forecast cards, order book data, watchlist (device only), notifications centre.
- No news on home, no docs, no emojis, no em dashes, no AI/Gate/Sepolia wording,
  no fake numbers or trades, trading always gated.

## 8. v8 build spec (from the measurements above)
- Auth: logo 36dp bare P (radius 9dp) + wordmark 18sp/800, NO subtitle; top
  spacer 14dp; headline 31sp/800; sub 15sp; PricesCard 3-col (BTC/ETH/SOL from
  the live ticker, dividers, no label, no scrolling); AuthTabs container
  326dp-wide, 52dp, radius 999, active pill 15sp/700; fields 60dp, radius 16,
  1.5dp border, static label + placeholder, eye icon on password; primary 56dp;
  centred note 12.5sp; "No account yet? Create one" link with lime underline
  (tapping it switches to Sign up); background = solid motif IMAGE with
  multiply/screen blend at 0.15 light / 0.18 dark (above the preview's 0.07/0.12
  so it is clearly visible on a real phone), glow 340dp circle, 0.65 peak, centre
  (0.85w, 0.045h); dots 15dp grid, 1.6dp radius, alpha 0.20/0.22, 170x120dp
  bottom-left.
- Dashboard: DELETE the dark header band. Plain header: greeting 13sp grey +
  name 19sp/800, bell (40dp bordered circle + 8dp lime dot), theme toggle (40dp
  bordered circle), avatar circle 40dp (first letter, black bg + lime text
  [lime bg + black text]). Quick actions 50dp circles with the preview's colours
  and the swap-arrows Trade icon. Positions + Portfolio sections: HORIZONTAL
  empty states (icon left, text middle, action right). Top movers: 3 FIXED cards
  in a row with letter circles, 13.5sp price, 10.5sp pill. Watchlist: letter
  circles + name/code, price + pill, star.
- Markets: NO title row, NO refresh icon, NO ticker strip, NO sort dropdown, NO
  "N markets" line. Search 44dp, radius 14, bg surface2. "All assets" dropdown
  pill + "N markets" text. Tabs as TWO WRAPPED rows of outlined pills (FlowRow),
  black active pill. Rows 64dp with letter circle + code below, question
  13.5sp/600, price 14.5sp/800 + change pill. "Show more" outlined button.
- Portfolio: title 22sp. P&L 3-col bordered card, labels 12.5sp/600 grey, values
  16sp bold, NO mono caps, NO "Saved markets" row. Holdings/Positions/History as
  HORIZONTAL empty states with the preview's copy.
- Wallet: title 22sp. Balance card WITHOUT buttons inside. Deposit/Withdraw as
  two separate grey pills BELOW the card + centred note. Transactions vertical
  empty state with the preview's copy. Wallet motif as a SOLID image,
  230dp at right:-70, bottom:60 (the preview's own placement), alpha 0.10.
- Market detail: circle-outline back + "Markets" grey, NO star. Question 22sp.
  Meta row: letter circle + "Name · CODE" + green dot + "X left". Big asset price
  + change pill (from the live ticker). Forecast card pale lime with "FORECAST"
  pill + verdict + reason. Chart without a card border, "Win line" label, dashed
  win line, lime fill, green end dot, "Average / Now ..." below. Order book as
  ONE table (asks red top, spread row, bids green bottom). Buy Up GREEN /
  Buy Down GREY segmented control. Amount row (label left, value right). "About
  N shares" centred. Big grey "Trading opens soon" button inline. DELETE the
  bottom sheet.
- Bottom nav: custom bar, no Material3 indicator pill. Active = bold label +
  5px lime dot under the icon. Icons: home, bar chart, pie, wallet.
- Letter circles: BTC #F7931A, ETH #627EEA, SOL #9945FF, XRP #23292F,
  DOGE #C2A633, BNB #F0B90B, HYPE #00C389, ZEC #F4B400; white bold first letter.
- Keep every v6/v7 fix listed in section 7, and all standing rules (no emojis,
  no em dashes, no AI/Gate/Sepolia wording, no fake numbers or trades, trading
  always gated).
