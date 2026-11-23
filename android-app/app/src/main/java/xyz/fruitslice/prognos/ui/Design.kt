package xyz.fruitslice.prognos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import xyz.fruitslice.prognos.data.ForecastContext
import xyz.fruitslice.prognos.data.forecastFor
import xyz.fruitslice.prognos.data.fmtPct
import xyz.fruitslice.prognos.data.fmtTickerPrice
import xyz.fruitslice.prognos.model.ASSET_LABELS
import xyz.fruitslice.prognos.model.assetLogoRes
import xyz.fruitslice.prognos.model.BookResponse
import xyz.fruitslice.prognos.model.Market
import xyz.fruitslice.prognos.model.TickerAsset

/**
 * Design system, v8. One spacing scale, one type scale, one colour system.
 * Every screen aligns to the same 16dp left edge, sections are separated
 * by 24dp of space, and there is one primary thing per screen.
 *
 * v8 rebuilt every component from the MEASURED approved preview (see
 * PREVIEW_DIFF.md). v6/v7 kept structures the preview never had (dark
 * dashboard header band, scrolling ticker strip, separate tab pills,
 * two-column order book, bottom-sheet trade panel, vertical centred empty
 * states) and invented others ("PREDICTION MARKET" subtitle, "LIVE PRICES"
 * label, Markets title row, sort dropdown). Flint was right: they did not
 * look anything alike. v8 matches the preview's measurements: 36dp logo,
 * 3-column prices card, pill-container auth tabs, 60dp fields with a static
 * label and an eye icon, horizontal empty states, wrapped outlined filter
 * pills, letter circles, one-table order book, inline trade panel, and a
 * custom bottom bar with a lime dot under the active tab.
 *
 * v7 rules kept from the design skills Flint sent (Taste Skill + Make
 * Interfaces Feel Better + Impeccable): one system everywhere (one button
 * pair, one card style, one radius scale); every visual draws, never an
 * empty ground, so empty states are designed containers, not bare lines;
 * concentric radii (outer = inner + padding); 44dp minimum touch targets;
 * tabular numbers (the bundled mono font is equal width); a subtle 0.96
 * press scale on primary buttons; text contrast kept; no AI tells (no
 * beige, no italic serif, no pulsing dot).
 */
val ScreenPad = 16.dp
val SectionGap = 24.dp
val CardRadius = 12.dp

/** Dashed border, as the approved preview draws its empty states:
 *  1.5px dashed border, 18dp radius, on a near-white surface. */
fun Modifier.dashedBorder(color: Color, radiusDp: Int = 18): Modifier = this.drawBehind {
    val stroke = 1.5.dp.toPx()
    val r = radiusDp.dp.toPx()
    drawRoundRect(
        color = color,
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        ),
        cornerRadius = CornerRadius(r, r)
    )
}

/** Subtle press feedback: scale to 0.96 while pressed. Use it with a
 *  Material3 Button by passing the returned interactionSource to the
 *  Button and the returned scale to Modifier.graphicsLayer. Applied to
 *  primary buttons only; never on high-frequency controls, and never
 *  the only feedback (colour and label stay). */
@Composable
fun rememberPressScale(): Pair<Float, MutableInteractionSource> {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "pressScale"
    )
    return scale to interactionSource
}

/** Change text, e.g. +1.2% in green, -0.8% in red. */
@Composable
fun ChangeText(chg: Double?, fontSize: Int = 13) {
    val c = appColors()
    if (chg == null) {
        Text("-", fontSize = fontSize.sp, fontFamily = PrognosMonoFamily, color = c.grey)
        return
    }
    Text(
        (if (chg >= 0) "+" else "") + String.format("%.1f", chg * 100) + "%",
        color = if (chg >= 0) BidGreen else AskRed,
        fontSize = fontSize.sp,
        fontFamily = PrognosMonoFamily,
        fontWeight = FontWeight.Bold
    )
}

/** Section header: bold title on the left, small action on the right. */
@Composable
fun SectionHeader(title: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    val c = appColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
        if (actionText != null && onAction != null) {
            Text(
                actionText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = c.grey,
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
}

/** One clean empty state. No wall of text. */
@Composable
fun EmptyState(text: String) {
    val c = appColors()
    Text(text, fontSize = 14.sp, color = c.grey)
}

/** Change pill: +1.2% on a green tint, -0.8% on a red tint. Preview size:
 *  10.5sp, tight padding, radius 999. */
@Composable
fun ChangePill(chg: Double?) {
    if (chg == null) {
        Text("-", fontSize = 11.sp, fontFamily = PrognosMonoFamily, color = appColors().grey)
        return
    }
    val up = chg >= 0
    Box(
        modifier = Modifier
            .background(
                if (up) Color(0xFFE6F4EA) else Color(0xFFFDECEA),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            (if (up) "+" else "") + String.format("%.1f", chg * 100) + "%",
            color = if (up) BidGreen else AskRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PrognosMonoFamily
        )
    }
}

/** Auth tabs, exactly as the preview draws them: ONE container, 52dp tall,
 *  radius 999, bg #EFEFEC light / #1B1B1B dark, padding 4dp, with a sliding
 *  active pill (black bg + white text light, white bg + black text dark),
 *  15sp/700. v7 used separate pills in a scrolling row with no container,
 *  which is not what the preview shows. */
@Composable
fun AuthTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                if (c.isDark) Color(0xFF1B1B1B) else Color(0xFFEFEFEC),
                RoundedCornerShape(999.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(
                        if (sel) c.primary else Color.Transparent,
                        RoundedCornerShape(999.dp)
                    )
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sel) c.onPrimary else Color(0xFF7A7A74)
                )
            }
        }
    }
}

/** Filter pills for the Markets screen, exactly as the preview draws them:
 *  outlined pills that WRAP to two rows, 1px border, radius 999. The active
 *  pill is solid black with white text (white with black text in dark mode).
 *  v7 used one scrolling row of solid pills, which is not what the preview
 *  shows, and v7 had a sort dropdown the preview does not have at all. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPills(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = appColors()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                modifier = Modifier
                    .background(
                        if (sel) c.primary else Color.Transparent,
                        RoundedCornerShape(999.dp)
                    )
                    .border(
                        1.dp,
                        if (sel) Color.Transparent else c.border,
                        RoundedCornerShape(999.dp)
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sel) c.onPrimary else c.ink
                )
            }
        }
    }
}

/** Designed empty state: icon in a tinted circle, bold title, short
 *  body, optional action. Drawn as a designed container (dashed
 *  border, near-white surface, lime-tinted icon circle), exactly as
 *  the approved preview draws its empty states: an empty screen is a
 *  designed surface, never a blank void or a bare line of grey text. */
@Composable
fun DesignedEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true
) {
    val c = appColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (c.isDark) Color(0xFF121212) else Color(0xFFFCFCFA),
                RoundedCornerShape(18.dp)
            )
            .dashedBorder(if (c.isDark) Color(0xFF2A2A2A) else Color(0xFFE0E0DA))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    if (c.isDark) Color(0xFF232B12) else Color(0xFFEFF5D8),
                    RoundedCornerShape(23.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (c.isDark) Lime else c.grey, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(body, fontSize = 13.sp, color = c.grey, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(onClick = onAction, enabled = actionEnabled) { Text(actionLabel, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Horizontal empty state, exactly as the preview draws the Dashboard
 *  positions and the Portfolio sections: a dashed-border container with
 *  the icon circle on the LEFT, the text in the MIDDLE and the action pill
 *  on the RIGHT. v7 stacked these vertically (icon on top, text, button
 *  below), which is not what the preview shows. */
@Composable
fun HorizontalEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
    actionPrimary: Boolean = true
) {
    val c = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (c.isDark) Color(0xFF121212) else Color(0xFFFCFCFA),
                RoundedCornerShape(18.dp)
            )
            .dashedBorder(if (c.isDark) Color(0xFF2A2A2A) else Color(0xFFE0E0DA))
            .padding(vertical = 16.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    if (c.isDark) Color(0xFF232B12) else Color(0xFFEFF5D8),
                    RoundedCornerShape(23.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (c.isDark) Lime else Color(0xFF5B7014), modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(modifier = Modifier.height(2.dp))
            Text(body, fontSize = 13.sp, color = c.grey)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.width(12.dp))
            val bg = if (actionPrimary) c.primary else if (c.isDark) Color(0xFF1C1C1C) else Color(0xFFEFEFEC)
            val fg = if (actionPrimary) c.onPrimary else c.ink
            Box(
                modifier = Modifier
                    .background(bg, RoundedCornerShape(999.dp))
                    .clickable(enabled = actionEnabled) { onAction() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!actionEnabled) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
                }
            }
        }
    }
}

/** P&L summary card, exactly as the preview draws it: a bordered card with
 *  three columns (Total value / Today / All time), hairline dividers, labels
 *  12.5sp/600 grey and values 16sp bold. Not mono caps, not a stat row. */
@Composable
fun PnlCard() {
    val c = appColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, c.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            val cells = listOf("Total value" to "$0.00", "Today" to "$0.00", "All time" to "$0.00")
            cells.forEachIndexed { i, (label, value) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.grey)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
                }
                if (i < cells.size - 1) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(c.border)
                    )
                }
            }
        }
    }
}

/** Letter circle: a coloured circle with the asset's first letter in white
 *  bold, exactly as the preview draws market rows, movers, watchlist rows
 *  and the detail meta row. Bright brand colours, not the muted sector
 *  accents v7 used for its dots. */
fun assetLetterColor(code: String?): Color {
    return when (code) {
        "BTC" -> Color(0xFFF7931A)
        "ETH" -> Color(0xFF627EEA)
        "SOL" -> Color(0xFF9945FF)
        "XRP" -> Color(0xFF23292F)
        "DOGE" -> Color(0xFFC2A633)
        "BNB" -> Color(0xFFF0B90B)
        "HYPE" -> Color(0xFF00C389)
        "ZEC" -> Color(0xFFF4B400)
        else -> Color(0xFF6B7280)
    }
}

@Composable
fun LetterCircle(code: String?, sizeDp: Int = 36) {
    val letter = (code ?: "?").take(1)
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .background(assetLetterColor(code), RoundedCornerShape((sizeDp / 2).dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, fontSize = (sizeDp * 0.45f).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/**
 * v1.1.4: the real official logo for the asset, bundled in the app.
 * Falls back to the letter circle only for an asset with no bundled
 * logo, so an unknown asset never gets a made-up mark.
 */
@Composable
fun AssetLogo(code: String?, sizeDp: Int = 36) {
    val res = assetLogoRes(code)
    if (res != null) {
        Image(
            painter = painterResource(id = res),
            contentDescription = (code?.let { ASSET_LABELS[it] ?: it } ?: "Asset") + " logo",
            modifier = Modifier.size(sizeDp.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        LetterCircle(code, sizeDp)
    }
}

/** Live prices card for the auth screen, exactly as the preview draws it:
 *  a 3-column card, 78dp tall, radius 16, bg #F7F8F2 light / #141414 dark,
 *  1px border, centred cells with 1px dividers between them. BTC, ETH, SOL
 *  from the live ticker. NO "LIVE PRICES" label and no scrolling strip:
 *  v7's scrolling strip was cut off at the right edge on Flint's phone. */
@Composable
fun PricesCard(ticker: List<TickerAsset>) {
    val c = appColors()
    val pick = listOf("BTC", "ETH", "SOL").map { code ->
        ticker.find { it.asset == code }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (c.isDark) Color(0xFF141414) else Color(0xFFF7F8F2)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (c.isDark) Color(0xFF23261C) else Color(0xFFECEEE2)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            pick.forEachIndexed { i, a ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        a?.asset ?: listOf("BTC", "ETH", "SOL")[i],
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PrognosMonoFamily,
                        color = c.grey
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        fmtTickerPrice(a?.price),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.ink
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (a?.chg24 == null) {
                        Text("-", fontSize = 11.sp, fontFamily = PrognosMonoFamily, color = c.grey)
                    } else {
                        val up = (a.chg24 ?: 0.0) >= 0
                        Text(
                            (if (up) "+" else "") + String.format("%.1f", (a.chg24 ?: 0.0) * 100) + "%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = PrognosMonoFamily,
                            color = if (up) {
                                if (c.isDark) Color(0xFF7BC98A) else BidGreen
                            } else {
                                if (c.isDark) Color(0xFFE88A8A) else AskRed
                            }
                        )
                    }
                }
                if (i < pick.size - 1) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(78.dp)
                            .background(if (c.isDark) Color(0xFF262626) else Color(0xFFE7E9DC))
                    )
                }
            }
        }
    }
}

/** Auth field, exactly as the preview draws it: 60dp tall, radius 16,
 *  1.5px border, bg white light / #131313 dark, with a STATIC label
 *  (11.5sp/600 grey) at the top inside the field and the value or
 *  placeholder (15.5sp) below it. An eye icon toggles password visibility.
 *  v7 used a Material floating label that animates and had no eye icon.
 *  v9: the label and the placeholder crowded each other on Flint's phone
 *  (the placeholder sat right up against the label), so the label has an
 *  explicit 14sp line height, a fixed 5dp gap sits between label and
 *  input, and the input and placeholder share a 20sp line height. The
 *  label is static, so its position never moves whether the field is
 *  empty, focused or filled. */
@Composable
fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val c = appColors()
    var showPassword by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(
                if (c.isDark) Color(0xFF131313) else Color.White,
                RoundedCornerShape(16.dp)
            )
            .border(
                1.5.dp,
                if (c.isDark) Color(0xFF2A2A2A) else Color(0xFFE4E4DF),
                RoundedCornerShape(16.dp)
            )
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF9A9A94)
                )
                Spacer(modifier = Modifier.height(5.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = PrognosFontFamily,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = c.ink
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    cursorBrush = SolidColor(c.ink),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(placeholder, fontSize = 15.sp, lineHeight = 20.sp, color = Color(0xFFB9B9B3))
                        }
                        inner()
                    }
                )
            }
            if (isPassword) {
                IconButton(
                    onClick = { showPassword = !showPassword },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = c.grey2,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** Forecast card, exactly as the preview draws it: a pale lime card with a
 *  black "FORECAST" pill (lime text), the verdict in bold (a label for the
 *  market, e.g. "Tense") and the plain-language reason below. The verdict
 *  is never a side: it describes the market, it does not tell the user
 *  which outcome to pick. When the market has no price there is honestly
 *  no forecast. The context carries the live ticker, news, books and
 *  chart the forecast reads; without it the note falls back to what the
 *  market alone can say.
 *
 *  v1.1.8: callers pass minute-floored seconds (nowMinute * 60). The
 *  forecast text only changes once a minute, so the card recomputes
 *  at most once a minute instead of every second. */
@Composable
fun ForecastCard(market: Market, nowSec: Long, ctx: ForecastContext? = null) {
    val c = appColors()
    // v1.1.9: the forecast is remembered on its inputs, so the string
    // build runs once per minute per market instead of on every
    // recomposition of the detail screen (books and chart poll every
    // 5s and used to rebuild it each time).
    val f = remember(market.id, nowSec, ctx) { forecastFor(market, nowSec, ctx) }
    val verdict = f?.verdict
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (c.isDark) Color(0xFF232B12) else Color(0xFFEFF5D8),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF111111), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("FORECAST", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PrognosMonoFamily, color = Lime)
                }
                Spacer(modifier = Modifier.width(10.dp))
                if (verdict != null) {
                    Text(verdict, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                f?.reason ?: "No forecast for this market yet. There is no price to read.",
                fontSize = 14.sp,
                color = c.grey
            )
        }
    }
}

/** Order book as ONE table, exactly as the preview draws it: a bordered
 *  rounded table with a "Price / Size" header, the asks in red on top, a
 *  grey "Spread" divider row, and the bids in green below. v7 split it
 *  into two columns with "Bids" and "Asks" labels and "No bids yet" text.
 *  When there are no quotes at all, the table says so honestly. */
@Composable
fun OrderBookTable(bookUp: BookResponse?, bookDown: BookResponse?) {
    val c = appColors()
    val asks = bookDown?.asks ?: emptyList()
    val bids = bookUp?.bids ?: emptyList()
    val spread = run {
        val bestAsk = asks.firstOrNull()?.price?.toDoubleOrNull()
        val bestBid = bids.firstOrNull()?.price?.toDoubleOrNull()
        if (bestAsk != null && bestBid != null) String.format("%.2f", bestAsk - bestBid) else null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, c.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Price", fontSize = 11.sp, fontFamily = PrognosMonoFamily, color = c.grey)
                Text("Size", fontSize = 11.sp, fontFamily = PrognosMonoFamily, color = c.grey)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (asks.isEmpty() && bids.isEmpty()) {
                Text("No quotes yet", fontSize = 13.sp, color = c.grey)
            } else {
                for (e in asks.take(3)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.price ?: "-", fontFamily = PrognosMonoFamily, fontSize = 14.sp, color = AskRed)
                        Text(e.size ?: "-", fontFamily = PrognosMonoFamily, fontSize = 14.sp, color = c.grey)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        if (spread != null) "Spread $spread" else "Spread -",
                        fontSize = 12.sp, fontFamily = PrognosMonoFamily, color = c.grey
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                for (e in bids.take(3)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.price ?: "-", fontFamily = PrognosMonoFamily, fontSize = 14.sp, color = BidGreen)
                        Text(e.size ?: "-", fontFamily = PrognosMonoFamily, fontSize = 14.sp, color = c.grey)
                    }
                }
            }
        }
    }
}

/** One top-mover card for the Dashboard, exactly as the preview draws it:
 *  a bordered card with a letter circle + code, the price, and a small
 *  change pill. v7 used a wide scrolling card with a dot and a 17sp price. */
@Composable
fun MoverCard(a: TickerAsset, onClick: () -> Unit) {
    val c = appColors()
    Card(
        modifier = Modifier
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = c.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, c.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetLogo(a.asset, sizeDp = 24)
                Spacer(modifier = Modifier.width(6.dp))
                Text(a.asset, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(fmtTickerPrice(a.price), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(modifier = Modifier.height(4.dp))
            ChangePill(a.chg24)
        }
    }
}

/** One watchlist row, exactly as the preview draws it: letter circle +
 *  name and code, price + change pill on the right, star. v7 reused
 *  MarketRow here, which shows the question and the outcome label. */
@Composable
fun WatchlistRow(
    market: Market,
    starred: Boolean,
    onStar: () -> Unit,
    onClick: () -> Unit,
    chg24: Double? = null
) {
    val c = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssetLogo(market.asset, sizeDp = 36)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                market.asset?.let { ASSET_LABELS[it] ?: it } ?: "Market",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = c.ink
            )
            Text(
                market.asset ?: "",
                fontSize = 11.sp,
                color = c.grey
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            MarketPriceLabel(market.p0, extraBold = false)
            Spacer(modifier = Modifier.height(2.dp))
            ChangePill(chg24)
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onStar, modifier = Modifier.size(44.dp)) {
            Icon(
                if (starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (starred) "Remove from watchlist" else "Save to watchlist",
                tint = if (starred) StarGold else c.grey2,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Balance card. One clean dark card in both themes: total balance, a
 * short honest note, and Deposit / Withdraw buttons inside it. The
 * buttons are DISABLED, greyed with lock icons, because deposits and
 * withdrawals are locked until trading opens (see TradingLock). They
 * never navigate anywhere. Balance is always honest.
 *
 * v7: radius 20dp and value 33sp, exactly as the approved preview.
 * A subtle white dot field on the right edge of the dark card, as the
 * preview draws it, so the card reads as designed, not flat.
 *
 * v8: showButtons is false on the Wallet screen, because the preview's
 * Wallet balance card has NO buttons inside it: Deposit and Withdraw
 * are separate grey pills below the card. v7 reused this card on Wallet
 * with the buttons inside, which is not what the preview shows.
 */
@Composable
fun BalanceCard(
    title: String,
    note: String?,
    showButtons: Boolean = true,
    balanceText: String = "$0.00",
    btcText: String? = null
) {
    val c = appColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        shape = RoundedCornerShape(20.dp),
        border = if (c.isDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)) else null
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier.matchParentSize()
            ) {
                val step = 14.dp.toPx()
                val dotColor = Color.White.copy(alpha = 0.13f)
                var y = 12.dp.toPx()
                var row = 0
                while (y < size.height - 8.dp.toPx() && row < 8) {
                    var x = size.width - 8.dp.toPx() - 5 * step + if (row % 2 == 0) 0f else step / 2f
                    var col = 0
                    while (x < size.width - 8.dp.toPx() && col < 5) {
                        drawCircle(dotColor, radius = 1.8f, center = Offset(x, y))
                        x += step
                        col++
                    }
                    y += step
                    row++
                }
            }
            Column(modifier = Modifier.padding(18.dp)) {
                Text(title, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))
                Text(balanceText, fontSize = 33.sp, fontWeight = FontWeight.Bold, color = Color.White)
                // BTC equivalent at the live price from the market
                // ticker. Shown only when the price is known; never a
                // hardcoded price.
                if (btcText != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(btcText, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                }
                if (note != null) {
                    Text(note, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                }
                if (showButtons) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Lime,
                                contentColor = Color(0xFF111111),
                                disabledContainerColor = Color(0xFF2E2E2E),
                                disabledContentColor = Color.White.copy(alpha = 0.45f)
                            )
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Deposit", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.45f)
                            )
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Withdraw")
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Yes price for a market row, or "No price yet" when the
 * market genuinely has no price (a future window with no orders
 * yet, so there is no bid, ask or last trade to read). v1.1.15: a
 * bare dash read as a broken row on the ETH 5-minute markets,
 * which have no price yet. The price is never invented: when
 * there is none, the row says so.
 */
@Composable
fun MarketPriceLabel(p0: Double?, extraBold: Boolean) {
    val c = appColors()
    if (p0 == null) {
        Text("No price yet", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.grey)
    } else {
        Text(
            fmtPct(p0),
            fontSize = 14.sp,
            fontWeight = if (extraBold) FontWeight.ExtraBold else FontWeight.Bold,
            color = c.ink
        )
    }
}

/**
 * Compact market row, exactly as the preview draws it: 64dp tall, a
 * letter circle on the left with the asset code BELOW it (9.5sp/800
 * grey), the question in up to two lines (13.5sp/600) in the middle,
 * and the price (14.5sp/800) with a change pill below it on the right,
 * plus a star. v7 used a 10dp dot with the code inline, a 15sp question,
 * a mono price and the outcome label ("UP") under it.
 */
@Composable
fun MarketRow(
    market: Market,
    starred: Boolean,
    onStar: () -> Unit,
    onClick: () -> Unit,
    chg24: Double? = null
) {
    val c = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AssetLogo(market.asset, sizeDp = 36)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                market.asset ?: "",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.grey,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            market.question,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            MarketPriceLabel(market.p0, extraBold = true)
            Spacer(modifier = Modifier.height(2.dp))
            ChangePill(chg24)
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(
            onClick = onStar,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                if (starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (starred) "Remove from watchlist" else "Save to watchlist",
                tint = if (starred) StarGold else c.grey2,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
