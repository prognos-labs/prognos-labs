<?php
/*
 * Server-side proxy to the public market data API. The browser
 * cannot fetch it directly (no CORS origin header on the replies),
 * so this file fetches it server-side and hands JSON to the
 * frontend on the same domain.
 *
 * No key or secret is used or stored here; the public endpoints
 * need none. Responses are cached on disk for 30 seconds, news
 * for 5 minutes. Learning lives in cache/learning-state.json,
 * cache/learning-samples.json and cache/learning.jsonl, all
 * denied to the web by cache/.htaccess.
 *
 * TODO: the .htaccess deny rule has only been checked by
 * inspection, not under a real Apache. confirm learning.jsonl
 * returns 403 once this is live.
 */

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$BASE = 'https://api.dexbuilder.com';
$CACHE_DIR = __DIR__ . '/cache';
$CACHE_TTL = 30; // seconds
$NEWS_TTL = 300; // seconds
$HISTORY_FILE = __DIR__ . '/cache/history.json';
$HISTORY_MAX_AGE = 1800; // seconds kept (30 minutes)
$HISTORY_MAX_SNAPS = 40;
$HISTORY_MIN_GAP = 60; // seconds between snapshots
$UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36';

function fail($msg, $code = 502) {
    http_response_code($code);
    echo json_encode(array('error' => $msg));
    exit;
}

function cache_path($url) {
    global $CACHE_DIR;
    return $CACHE_DIR . '/' . md5($url) . '.json';
}

function cache_get_ttl($url, $ttl) {
    $p = cache_path($url);
    if (!is_file($p)) return null;
    if (time() - filemtime($p) > $ttl) return null;
    $body = file_get_contents($p);
    if ($body === false || $body === '') return null;
    return $body;
}

function cache_get($url) {
    global $CACHE_TTL;
    return cache_get_ttl($url, $CACHE_TTL);
}

function cache_get_stale($url) {
    $p = cache_path($url);
    if (!is_file($p)) return null;
    $body = file_get_contents($p);
    if ($body === false || $body === '') return null;
    return $body;
}

function cache_put($url, $body) {
    global $CACHE_DIR;
    if (!is_dir($CACHE_DIR)) {
        @mkdir($CACHE_DIR, 0755, true);
    }
    if (is_dir($CACHE_DIR) && is_writable($CACHE_DIR)) {
        @file_put_contents(cache_path($url), $body, LOCK_EX);
    }
    prune_cache();
}

/* prune price and response caches older than a day, temp files older than an hour. at most once an hour (marker file). learning, history and the placeholder page are never touched. */
function prune_cache() {
    global $CACHE_DIR;
    if (!is_dir($CACHE_DIR)) return;
    $marker = $CACHE_DIR . '/.prune-last';
    $now = time();
    $last = is_file($marker) ? (int) @file_get_contents($marker) : 0;
    if ($now - $last < 3600) return;
    @file_put_contents($marker, (string) $now, LOCK_EX);
    $cut = $now - 86400;
    $cutTmp = $now - 3600;
    $files = @scandir($CACHE_DIR);
    if (!is_array($files)) return;
    foreach ($files as $f) {
        if ($f === '.' || $f === '..') continue;
        $p = $CACHE_DIR . '/' . $f;
        if (!is_file($p)) continue;
        if (preg_match('/^price-[0-9a-f]{32}\.json$/', $f) || preg_match('/^[0-9a-f]{32}\.json$/', $f)) {
            if (@filemtime($p) < $cut) @unlink($p);
        } elseif (strpos($f, '.tmp.') !== false) {
            if (@filemtime($p) < $cutTmp) @unlink($p);
        }
    }
}

/* fetch several urls at once; bodies come back in the same order, null where one failed. the ticker used to take seconds on a cold cache with eight sequential calls. */
function upstream_get_multi($urls, $accept = 'application/json') {
    global $UA;
    $mh = curl_multi_init();
    $chs = array();
    foreach ($urls as $i => $url) {
        $ch = curl_init($url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);
        curl_setopt($ch, CURLOPT_MAXREDIRS, 3);
        curl_setopt($ch, CURLOPT_TIMEOUT, 15);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 8);
        curl_setopt($ch, CURLOPT_USERAGENT, $UA);
        curl_setopt($ch, CURLOPT_HTTPHEADER, array('Accept: ' . $accept));
        curl_multi_add_handle($mh, $ch);
        $chs[$i] = $ch;
    }
    $running = null;
    do {
        $mrc = curl_multi_exec($mh, $running);
        if ($running) curl_multi_select($mh, 1);
    } while ($running && $mrc === CURLM_OK);
    $out = array();
    foreach ($chs as $i => $ch) {
        $body = curl_multi_getcontent($ch);
        $code = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $out[$i] = ($body !== false && $body !== '' && $code >= 200 && $code < 300) ? $body : null;
        curl_multi_remove_handle($mh, $ch);
        curl_close($ch);
    }
    curl_multi_close($mh);
    return $out;
}

function upstream_get($url, $accept = 'application/json') {
    global $UA;
    $ch = curl_init($url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);
    curl_setopt($ch, CURLOPT_MAXREDIRS, 3);
    curl_setopt($ch, CURLOPT_TIMEOUT, 15);
    curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 8);
    curl_setopt($ch, CURLOPT_USERAGENT, $UA);
    curl_setopt($ch, CURLOPT_HTTPHEADER, array('Accept: ' . $accept));
    $body = curl_exec($ch);
    $err = curl_error($ch);
    $code = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    if ($body === false || $body === '') {
        return array(null, 'Upstream request failed' . ($err ? ': ' . $err : ''));
    }
    if ($code < 200 || $code >= 300) {
        return array(null, 'Upstream returned HTTP ' . $code);
    }
    return array($body, null);
}

/* through the 30-second cache. null when there is no fresh fetch and no stale copy. */
function fetch_body($url) {
    $hit = cache_get($url);
    if ($hit !== null) return $hit;
    list($body, $err) = upstream_get($url);
    if ($body !== null) {
        cache_put($url, $body);
        return $body;
    }
    return cache_get_stale($url);
}

function serve($url) {
    $body = fetch_body($url);
    if ($body !== null) {
        echo $body;
        return;
    }
    fail('Could not reach the market data service.');
}

function clean_id($v, $max = 120, $extra = '') {
    if (!is_string($v) || $v === '') return null;
    if (strlen($v) > $max) return null;
    /* $extra adds safe characters for values that need them: the
       pagination cursor is a JWT-style token and may carry = + and . */
    if (!preg_match('/^[A-Za-z0-9_\-' . preg_quote($extra, '/') . ']+$/', $v)) return null;
    return $v;
}

function clean_int($v, $def, $min, $max) {
    if ($v === null || $v === '') return $def;
    if (!preg_match('/^\d+$/', (string) $v)) return $def;
    $n = (int) $v;
    if ($n < $min) $n = $min;
    if ($n > $max) $n = $max;
    return $n;
}

function num_or_null($v) {
    if ($v === null || $v === '' || !is_numeric($v)) return null;
    $n = (float) $v;
    return is_finite($n) ? $n : null;
}

function utf8_truncate($s, $max) {
    if (strlen($s) <= $max) return $s;
    $t = substr($s, 0, $max);
    while ($t !== '' && (ord(substr($t, -1)) & 0xC0) === 0x80) {
        $t = substr($t, 0, -1);
    }
    if ($t !== '') {
        $last = ord(substr($t, -1));
        if ($last >= 0xC0 && $last < 0xF8) $t = substr($t, 0, -1);
    }
    return $t;
}

/* price history */

function load_history() {
    global $HISTORY_FILE;
    if (!is_file($HISTORY_FILE)) return array('snapshots' => array());
    $raw = file_get_contents($HISTORY_FILE);
    if ($raw === false || $raw === '') return array('snapshots' => array());
    $d = json_decode($raw, true);
    if (!is_array($d) || !isset($d['snapshots']) || !is_array($d['snapshots'])) {
        return array('snapshots' => array());
    }
    return $d;
}

/* one snapshot a minute at most. {t, m: {id: [price, spread]}}, prices to 3 places. no quote, left out. */
function record_snapshot($body) {
    global $HISTORY_FILE, $HISTORY_MAX_AGE, $HISTORY_MAX_SNAPS, $HISTORY_MIN_GAP, $CACHE_DIR;
    $data = json_decode($body, true);
    if (!is_array($data) || empty($data['items']) || !is_array($data['items'])) return;
    $now = time();
    $hist = load_history();
    $snaps = $hist['snapshots'];
    if (!empty($snaps)) {
        $last = end($snaps);
        if (isset($last['t']) && ($now - (int) $last['t']) < $HISTORY_MIN_GAP) return;
    }
    $m = array();
    foreach ($data['items'] as $raw) {
        if (!isset($raw['market_id']) || $raw['market_id'] === '') continue;
        $bid = num_or_null(isset($raw['best_bid']) ? $raw['best_bid'] : null);
        $ask = num_or_null(isset($raw['best_ask']) ? $raw['best_ask'] : null);
        $last = num_or_null(isset($raw['last_trade_price']) ? $raw['last_trade_price'] : null);
        $p = null;
        if ($bid !== null && $ask !== null) $p = ($bid + $ask) / 2;
        elseif ($bid !== null) $p = $bid;
        elseif ($ask !== null) $p = $ask;
        elseif ($last !== null && $last > 0) $p = $last;
        if ($p === null) continue;
        $spread = ($bid !== null && $ask !== null) ? $ask - $bid : null;
        $m[(string) $raw['market_id']] = array(round($p, 3), $spread === null ? null : round($spread, 3));
    }
    if (empty($m)) return;
    $snaps[] = array('t' => $now, 'm' => $m);
    $cut = $now - $HISTORY_MAX_AGE;
    $snaps = array_values(array_filter($snaps, function ($s) use ($cut) {
        return isset($s['t']) && (int) $s['t'] >= $cut;
    }));
    if (count($snaps) > $HISTORY_MAX_SNAPS) {
        $snaps = array_slice($snaps, -$HISTORY_MAX_SNAPS);
    }
    if (!is_dir($CACHE_DIR)) {
        @mkdir($CACHE_DIR, 0755, true);
    }
    /* temp file + rename, so a reader never sees half a file. */
    $tmp = $HISTORY_FILE . '.tmp.' . getmypid();
    if (@file_put_contents($tmp, json_encode(array('snapshots' => $snaps)), LOCK_EX) !== false) {
        @rename($tmp, $HISTORY_FILE);
    }
}

function action_watchdog() {
    global $BASE;
    $hist = load_history();
    $snaps = $hist['snapshots'];
    $now = time();
    /* baseline is the snapshot nearest 15 minutes ago, at least 10 minutes old. no baseline, nothing to compare. */
    $target = $now - 900;
    $base = null;
    foreach ($snaps as $s) {
        if (!isset($s['t']) || (int) $s['t'] > $now - 600) continue;
        if ($base === null || abs((int) $s['t'] - $target) < abs((int) $base['t'] - $target)) {
            $base = $s;
        }
    }
    $body = fetch_body($BASE . '/api/v4/prediction/markets?limit=100');
    if ($body === null) fail('Could not reach the market data service.');
    $data = json_decode($body, true);
    $items = (is_array($data) && isset($data['items']) && is_array($data['items'])) ? $data['items'] : array();
    $flags = array();
    $baselineAgeMin = null;
    if ($base !== null && isset($base['m']) && is_array($base['m'])) {
        $baselineAgeMin = (int) round(($now - (int) $base['t']) / 60);
        foreach ($items as $raw) {
            $id = isset($raw['market_id']) ? (string) $raw['market_id'] : '';
            if ($id === '' || !isset($base['m'][$id])) continue;
            $q = '';
            if (isset($raw['question'])) {
                if (is_array($raw['question']) && isset($raw['question']['en'])) $q = (string) $raw['question']['en'];
                elseif (is_string($raw['question'])) $q = $raw['question'];
            }
            $q = utf8_truncate($q, 300);
            $bid = num_or_null(isset($raw['best_bid']) ? $raw['best_bid'] : null);
            $ask = num_or_null(isset($raw['best_ask']) ? $raw['best_ask'] : null);
            $last = num_or_null(isset($raw['last_trade_price']) ? $raw['last_trade_price'] : null);
            $p = null;
            if ($bid !== null && $ask !== null) $p = ($bid + $ask) / 2;
            elseif ($bid !== null) $p = $bid;
            elseif ($ask !== null) $p = $ask;
            elseif ($last !== null && $last > 0) $p = $last;
            $spread = ($bid !== null && $ask !== null) ? $ask - $bid : null;
            $old = $base['m'][$id];
            $oldP = isset($old[0]) ? (float) $old[0] : null;
            $oldSpread = isset($old[1]) ? $old[1] : null;
            $oldSpread = $oldSpread === null ? null : (float) $oldSpread;
            $label0 = 'Up';
            if (isset($raw['outcomes'][0]['outcome'])) {
                $o0 = $raw['outcomes'][0]['outcome'];
                if (is_array($o0) && isset($o0['en'])) $label0 = (string) $o0['en'];
                elseif (is_string($o0)) $label0 = $o0;
            }
            $label1 = 'Down';
            if (isset($raw['outcomes'][1]['outcome'])) {
                $o1 = $raw['outcomes'][1]['outcome'];
                if (is_array($o1) && isset($o1['en'])) $label1 = (string) $o1['en'];
                elseif (is_string($o1)) $label1 = $o1;
            }
            /* A quote that went stale. */
            if ($p === null && $oldP !== null) {
                $flags[] = array(
                    'market_id' => $id, 'question' => $q, 'kind' => 'stale',
                    'text' => 'No live quote right now. About ' . $baselineAgeMin . ' minutes ago this market was priced at ' . number_format($oldP * 100, 1) . '% for ' . $label0 . ', and now the book is empty.',
                    'change' => null,
                );
                continue;
            }
            if ($p === null || $oldP === null) continue;
            /* A sharp price move: 5 points or more. */
            $dp = $p - $oldP;
            if (abs($dp) >= 0.05) {
                $dir = $dp > 0 ? $label0 : $label1;
                $pts = number_format(abs($dp) * 100, 1);
                $flags[] = array(
                    'market_id' => $id, 'question' => $q, 'kind' => 'move',
                    'text' => 'Moved ' . $pts . ' points towards ' . $dir . ' in about ' . $baselineAgeMin . ' minutes, from ' . number_format($oldP * 100, 1) . '% to ' . number_format($p * 100, 1) . '% for ' . $label0 . '.',
                    'change' => round($dp, 3),
                );
                continue;
            }
            /* A spread that widened sharply. */
            if ($spread !== null && $oldSpread !== null && $spread >= 0.10 && $oldSpread <= 0.05) {
                $flags[] = array(
                    'market_id' => $id, 'question' => $q, 'kind' => 'spread',
                    'text' => 'The gap between the best bid and ask widened from ' . number_format($oldSpread, 2) . ' to ' . number_format($spread, 2) . ' in about ' . $baselineAgeMin . ' minutes, so the price can move further on a single trade.',
                    'change' => round($spread - $oldSpread, 3),
                );
            }
        }
    }
    echo json_encode(array(
        'flags' => $flags,
        'baseline_age_min' => $baselineAgeMin,
        'history_snapshots' => count($snaps),
        'checked' => count($items),
        'fetched_at' => gmdate('c', $now),
    ));
}

function action_history() {
    $hist = load_history();
    $snaps = $hist['snapshots'];
    $oldest = null;
    $newest = null;
    $tracked = 0;
    foreach ($snaps as $s) {
        if (!isset($s['t'])) continue;
        $t = (int) $s['t'];
        if ($oldest === null || $t < $oldest) $oldest = $t;
        if ($newest === null || $t > $newest) $newest = $t;
    }
    if (!empty($snaps)) {
        $last = end($snaps);
        if (isset($last['m']) && is_array($last['m'])) $tracked = count($last['m']);
    }
    echo json_encode(array(
        'snapshots' => count($snaps),
        'oldest' => $oldest ? gmdate('c', $oldest) : null,
        'newest' => $newest ? gmdate('c', $newest) : null,
        'markets_tracked' => $tracked,
    ));
}

/* news */

function rss_field($block, $tag) {
    if (!preg_match('/<' . $tag . '\b[^>]*>(.*?)<\/' . $tag . '>/is', $block, $m)) return '';
    $v = trim($m[1]);
    if (preg_match('/^<!\[CDATA\[(.*)\]\]>$/s', $v, $c)) $v = $c[1];
    $v = strip_tags($v);
    $v = html_entity_decode($v, ENT_QUOTES, 'UTF-8');
    return trim($v);
}

/* parse RSS 2.0 without an xml extension: plain text match on <item> blocks. newest first. */
function parse_rss_items($xml) {
    $out = array();
    if (!preg_match_all('/<item\b[^>]*>(.*?)<\/item>/is', $xml, $m)) return $out;
    foreach ($m[1] as $block) {
        $title = rss_field($block, 'title');
        $link = clean_news_link(rss_field($block, 'link'));
        if ($title === '' || $link === '') continue;
        $pub = rss_field($block, 'pubDate');
        $ts = ($pub !== '') ? strtotime($pub) : 0;
        if ($ts === false || $ts <= 0) $ts = 0;
        $out[] = array(
            'title' => $title,
            'link' => clean_news_link($link),
            'time' => $ts ? gmdate('c', $ts) : null,
            'ts' => $ts,
        );
    }
    return $out;
}

/* only http/https links ever reach an anchor. a feed item with a javascript: or data: link, or no usable link, is dropped, so a poisoned feed cannot plant a live script link on the page. */
function clean_news_link($link) {
    $p = parse_url($link);
    if ($p === false || !isset($p['scheme']) || !isset($p['host'])) {
        return '';
    }
    $scheme = strtolower($p['scheme']);
    if ($scheme !== 'http' && $scheme !== 'https') {
        return '';
    }
    $out = $scheme . '://' . $p['host'];
    if (isset($p['port'])) $out .= ':' . (int) $p['port'];
    if (isset($p['path'])) $out .= $p['path'];
    if (isset($p['query'])) $out .= '?' . $p['query'];
    return $out;
}

function tag_assets($title) {
    $words = array(
        'BTC' => array('bitcoin', 'btc'),
        'ETH' => array('ethereum', 'ether', 'eth'),
        'XRP' => array('xrp', 'ripple'),
        'SOL' => array('solana', 'sol'),
        'DOGE' => array('dogecoin', 'doge'),
        'BNB' => array('bnb', 'binance'),
        'HYPE' => array('hyperliquid', 'hype'),
        'ZEC' => array('zcash', 'zec'),
    );
    $tags = array();
    foreach ($words as $asset => $list) {
        foreach ($list as $w) {
            if (preg_match('/\b' . preg_quote($w, '/') . '\b/i', $title)) {
                $tags[] = $asset;
                break;
            }
        }
    }
    return $tags;
}

function action_news() {
    global $NEWS_TTL;
    $cacheKey = 'news-digest-v1';
    $hit = cache_get_ttl($cacheKey, $NEWS_TTL);
    if ($hit !== null) {
        echo $hit;
        return;
    }
    $items = array();
    $source = null;
    $feeds = array(
        array('url' => 'https://cointelegraph.com/rss', 'name' => 'Cointelegraph'),
        array('url' => 'https://www.coindesk.com/arc/outboundfeeds/rss/', 'name' => 'CoinDesk'),
    );
    foreach ($feeds as $f) {
        list($body, $err) = upstream_get($f['url'], 'application/rss+xml, application/xml, text/xml');
        if ($body === null) continue;
        $parsed = parse_rss_items($body);
        if (empty($parsed)) continue;
        $items = $parsed;
        $source = $f['name'];
        break;
    }
    if (empty($items)) {
        fail('Market news is unavailable right now. Both news feeds could not be reached.');
    }
    /* mood from the live signals feed, if it answers. context only; the digest works without it. */
    $mood = null;
    list($wbody, $werr) = upstream_get('https://sabio-signals.flint00hart.workers.dev/');
    if ($wbody !== null) {
        $wd = json_decode($wbody, true);
        if (is_array($wd) && isset($wd['news']) && is_array($wd['news'])) {
            $n = $wd['news'];
            if (isset($n['scanned'])) {
                $mood = array(
                    'scanned' => (int) $n['scanned'],
                    'positive' => isset($n['positive1h']) ? (int) $n['positive1h'] : null,
                    'negative' => isset($n['negative1h']) ? (int) $n['negative1h'] : null,
                );
            }
        }
    }
    $items = array_slice($items, 0, 12);
    $out = array();
    foreach ($items as $it) {
        $out[] = array(
            'title' => $it['title'],
            'link' => $it['link'],
            'time' => $it['time'],
            'source' => $source,
            'tags' => tag_assets($it['title']),
        );
    }
    $payload = json_encode(array(
        'items' => $out,
        'source' => $source,
        'mood' => $mood,
        'fetched_at' => gmdate('c', time()),
    ));
    cache_put($cacheKey, $payload);
    echo $payload;
}

/* learning */

/* forecasts are recorded as shown; when a market ends, its outcome is worked out, first from the market itself, then from the asset price at window end against window start. a challenger is fitted from the scored forecasts and only replaces the champion when it beats it on newer markets by a clear margin. if the outcome cannot be established, the market stays unscored. */

$LEARN_LOG = __DIR__ . '/cache/learning.jsonl';
$LEARN_STATE = __DIR__ . '/cache/learning-state.json';
$LEARN_SAMPLES = __DIR__ . '/cache/learning-samples.json';
$LEARN_RECORD_GAP = 900;   /* seconds: one record per market per 15 min */
$LEARN_LOG_MAX = 4000;     /* lines kept in the raw record */
$LEARN_SAMPLES_MAX = 2000; /* scored forecasts kept */
$LEARN_MIN_SAMPLES = 30;   /* scored markets needed before training */
$LEARN_PASS_GAP = 600;     /* seconds between learn passes */
$LEARN_PROMOTE_MARGIN = 0.01; /* Brier margin the challenger must win by */

$COINBASE_MAP = array(
    'BTC' => 'BTC-USD', 'ETH' => 'ETH-USD', 'XRP' => 'XRP-USD',
    'SOL' => 'SOL-USD', 'DOGE' => 'DOGE-USD', 'BNB' => 'BNB-USD',
    'HYPE' => 'HYPE-USD', 'ZEC' => 'ZEC-USD',
);

function learn_load_state() {
    global $LEARN_STATE;
    $def = array(
        'champion' => 'formula', 'weights' => null,
        'scored' => 0, 'correct' => 0,
        'brier_champion' => null, 'brier_challenger' => null,
        'last_trained' => null, 'last_pass' => 0,
    );
    if (!is_file($LEARN_STATE)) return $def;
    $raw = file_get_contents($LEARN_STATE);
    if ($raw === false || $raw === '') return $def;
    $d = json_decode($raw, true);
    if (!is_array($d)) return $def;
    return array_merge($def, $d);
}

function learn_save_state($s) {
    global $LEARN_STATE, $CACHE_DIR;
    if (!is_dir($CACHE_DIR)) @mkdir($CACHE_DIR, 0755, true);
    $tmp = $LEARN_STATE . '.tmp.' . getmypid();
    if (@file_put_contents($tmp, json_encode($s), LOCK_EX) !== false) {
        @rename($tmp, $LEARN_STATE);
    }
}

function learn_load_samples() {
    global $LEARN_SAMPLES;
    if (!is_file($LEARN_SAMPLES)) return array();
    $raw = file_get_contents($LEARN_SAMPLES);
    if ($raw === false || $raw === '') return array();
    $d = json_decode($raw, true);
    return is_array($d) ? $d : array();
}

function learn_save_samples($arr) {
    global $LEARN_SAMPLES, $LEARN_SAMPLES_MAX, $CACHE_DIR;
    if (count($arr) > $LEARN_SAMPLES_MAX) {
        $arr = array_slice($arr, -$LEARN_SAMPLES_MAX);
    }
    if (!is_dir($CACHE_DIR)) @mkdir($CACHE_DIR, 0755, true);
    $tmp = $LEARN_SAMPLES . '.tmp.' . getmypid();
    if (@file_put_contents($tmp, json_encode(array_values($arr)), LOCK_EX) !== false) {
        @rename($tmp, $LEARN_SAMPLES);
    }
}

/* at most one record per market per 15 minutes, so the log stays small. */
function learn_append_records($recs) {
    global $LEARN_LOG, $LEARN_RECORD_GAP, $LEARN_LOG_MAX, $CACHE_DIR;
    if (empty($recs)) return 0;
    $last = array();
    if (is_file($LEARN_LOG)) {
        $lines = @file($LEARN_LOG, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        if ($lines !== false) {
            foreach (array_slice($lines, -600) as $ln) {
                $d = json_decode($ln, true);
                if (is_array($d) && isset($d['id'], $d['t'])) $last[$d['id']] = (int) $d['t'];
            }
        }
    }
    $now = time();
    $out = array();
    foreach ($recs as $r) {
        if (isset($last[$r['id']]) && ($now - $last[$r['id']]) < $LEARN_RECORD_GAP) continue;
        $out[] = json_encode($r);
        $last[$r['id']] = $now;
    }
    if (empty($out)) return 0;
    if (!is_dir($CACHE_DIR)) @mkdir($CACHE_DIR, 0755, true);
    @file_put_contents($LEARN_LOG, implode("\n", $out) . "\n", FILE_APPEND | LOCK_EX);
    $lines = @file($LEARN_LOG, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
    if ($lines !== false && count($lines) > $LEARN_LOG_MAX) {
        $keep = array_slice($lines, -$LEARN_LOG_MAX);
        $tmp = $LEARN_LOG . '.tmp.' . getmypid();
        if (@file_put_contents($tmp, implode("\n", $keep) . "\n", LOCK_EX) !== false) {
            @rename($tmp, $LEARN_LOG);
        }
    }
    return count($out);
}

function learn_logit($p) {
    $p = max(0.01, min(0.99, (float) $p));
    return log($p / (1 - $p));
}

function learn_sigmoid($x) {
    if ($x > 30) return 1.0;
    if ($x < -30) return 0.0;
    return 1 / (1 + exp(-$x));
}

/* features: the forecast as log-odds, plus time left, volume and spread, each standardised. the frontend uses the same ones. */
function learn_feat($p, $mins, $vol, $spread, $mu, $sd) {
    $z = function ($v, $m, $s) { return $s > 0 ? ($v - $m) / $s : 0.0; };
    return array(
        1.0,
        learn_logit($p),
        $z(log($mins + 1), $mu['logmins'], $sd['logmins']),
        $z(log($vol + 1), $mu['logvol'], $sd['logvol']),
        $z($spread, $mu['spread'], $sd['spread']),
    );
}

/* small logistic model, plain gradient descent. no libraries, a few hundred samples at most. */
function learn_fit($train) {
    $lm = array(); $lv = array(); $ls = array();
    foreach ($train as $s) {
        $lm[] = log((int) $s['mins'] + 1);
        $lv[] = log((float) $s['vol'] + 1);
        $ls[] = (float) $s['spread'];
    }
    $n = count($train);
    $mu = array(
        'logmins' => array_sum($lm) / $n,
        'logvol' => array_sum($lv) / $n,
        'spread' => array_sum($ls) / $n,
    );
    $sd = array('logmins' => 0.0, 'logvol' => 0.0, 'spread' => 0.0);
    foreach ($train as $s) {
        $sd['logmins'] += pow(log((int) $s['mins'] + 1) - $mu['logmins'], 2);
        $sd['logvol'] += pow(log((float) $s['vol'] + 1) - $mu['logvol'], 2);
        $sd['spread'] += pow((float) $s['spread'] - $mu['spread'], 2);
    }
    foreach (array('logmins', 'logvol', 'spread') as $k) {
        $sd[$k] = sqrt($sd[$k] / max(1, $n));
        if ($sd[$k] <= 0) $sd[$k] = 1.0;
    }
    $b = array(0.0, 0.0, 0.0, 0.0, 0.0);
    $lr = 0.3; $l2 = 0.01; $iters = 300;
    for ($it = 0; $it < $iters; $it++) {
        $g = array(0.0, 0.0, 0.0, 0.0, 0.0);
        foreach ($train as $s) {
            $x = learn_feat((float) $s['p'], (int) $s['mins'], (float) $s['vol'], (float) $s['spread'], $mu, $sd);
            $dot = 0.0;
            for ($j = 0; $j < 5; $j++) $dot += $b[$j] * $x[$j];
            $err = learn_sigmoid($dot) - (float) $s['outcome'];
            for ($j = 0; $j < 5; $j++) $g[$j] += $err * $x[$j];
        }
        for ($j = 0; $j < 5; $j++) {
            $g[$j] = $g[$j] / $n + $l2 * $b[$j];
            $b[$j] -= $lr * $g[$j];
        }
    }
    return array('b' => $b, 'mu' => $mu, 'sd' => $sd);
}

function learn_predict($w, $p, $mins, $vol, $spread) {
    $x = learn_feat($p, $mins, $vol, $spread, $w['mu'], $w['sd']);
    $dot = 0.0;
    for ($j = 0; $j < 5; $j++) $dot += $w['b'][$j] * $x[$j];
    return learn_sigmoid($dot);
}

function learn_brier($preds, $outs) {
    $n = count($preds);
    if ($n === 0) return null;
    $s = 0.0;
    for ($i = 0; $i < $n; $i++) $s += pow($preds[$i] - $outs[$i], 2);
    return $s / $n;
}

/* one-minute candles. $ttl is how long a fetched range is cached: a day for settled history, a minute for a live chart. other public feeds were checked and are blocked from this server. */
function price_cache_path($key) {
    global $CACHE_DIR;
    return $CACHE_DIR . '/price-' . md5($key) . '.json';
}

/* cached ranges are read from disk first; only the missing ones go out, in parallel. all candles in time order, or null. */
function coinbase_candles_multi($product, $ranges, $ttl = 86400) {
    $out = array();
    $todo = array();
    foreach ($ranges as $idx => $r) {
        $start = gmdate('Y-m-d\TH:i:s\Z', $r[0]);
        $end = gmdate('Y-m-d\TH:i:s\Z', $r[1]);
        $key = $product . '|' . $start . '|' . $end;
        $p = price_cache_path($key);
        if (is_file($p) && (time() - filemtime($p) <= $ttl)) {
            $hit = file_get_contents($p);
            if ($hit !== false && $hit !== '') {
                $d = json_decode($hit, true);
                if (is_array($d)) { $out[$idx] = $d; continue; }
            }
        }
        $todo[$idx] = array(
            'url' => 'https://api.exchange.coinbase.com/products/' . rawurlencode($product) .
                '/candles?start=' . rawurlencode($start) . '&end=' . rawurlencode($end) . '&granularity=60',
            'path' => $p,
        );
    }
    if (!empty($todo)) {
        $bodies = upstream_get_multi(array_values(array_map(function ($t) { return $t['url']; }, $todo)));
        $i = 0;
        foreach ($todo as $idx => $t) {
            $body = $bodies[$i++];
            if ($body === null) return null;
            $d = json_decode($body, true);
            if (!is_array($d) || empty($d)) return null;
            global $CACHE_DIR;
            if (!is_dir($CACHE_DIR)) @mkdir($CACHE_DIR, 0755, true);
            @file_put_contents($t['path'], $body, LOCK_EX);
            $out[$idx] = $d;
        }
    }
    ksort($out);
    $all = array();
    foreach ($out as $chunk) {
        foreach ($chunk as $row) $all[] = $row;
    }
    return $all;
}

function coinbase_candles($product, $startTs, $endTs, $ttl = 86400) {
    $r = coinbase_candles_multi($product, array(array($startTs, $endTs)), $ttl);
    return $r;
}

/* window-start close against the close of the last full minute of the window. up wins on a tie, same as the market's own rule. null when a price is missing. */
function price_outcome($asset, $winStart, $winEnd) {
    global $COINBASE_MAP;
    if (!isset($COINBASE_MAP[$asset])) return null;
    $candles = coinbase_candles($COINBASE_MAP[$asset], $winStart - 60, $winEnd);
    if ($candles === null) return null;
    $startKey = (int) floor($winStart / 60) * 60;
    $endKey = (int) floor($winEnd / 60) * 60 - 60;
    $startClose = null; $endClose = null;
    foreach ($candles as $c) {
        if (!is_array($c) || count($c) < 5) continue;
        $t = (int) $c[0];
        $close = num_or_null($c[4]);
        if ($close === null) continue;
        if ($t === $startKey) $startClose = $close;
        if ($t === $endKey) $endClose = $close;
    }
    if ($startClose === null || $endClose === null) return null;
    return ($endClose >= $startClose) ? 1 : 0;
}

/* 1 when the first outcome won, 0 when the second won, null when it is not reported. */
function reported_outcome($marketId) {
    global $BASE;
    $body = fetch_body($BASE . '/api/v4/prediction/markets/' . rawurlencode($marketId));
    if ($body === null) return null;
    $d = json_decode($body, true);
    if (!is_array($d)) return null;
    $win = isset($d['winning_outcome']) ? (string) $d['winning_outcome'] : '';
    if ($win !== '') {
        $outs = isset($d['outcomes']) && is_array($d['outcomes']) ? $d['outcomes'] : array();
        $labels = array('', '');
        for ($i = 0; $i < 2; $i++) {
            if (isset($outs[$i]['outcome'])) {
                $o = $outs[$i]['outcome'];
                $labels[$i] = is_array($o) && isset($o['en']) ? (string) $o['en'] : (string) $o;
            }
        }
        if ($labels[0] !== '' && strcasecmp($win, $labels[0]) === 0) return 1;
        if ($labels[1] !== '' && strcasecmp($win, $labels[1]) === 0) return 0;
    }
    $wt = isset($d['winning_token_id']) ? (string) $d['winning_token_id'] : '';
    if ($wt !== '') {
        $outs = isset($d['outcomes']) && is_array($d['outcomes']) ? $d['outcomes'] : array();
        for ($i = 0; $i < 2; $i++) {
            if (isset($outs[$i]['token_id']) && $wt === (string) $outs[$i]['token_id']) {
                return $i === 0 ? 1 : 0;
            }
        }
    }
    return null;
}

/* chart */

/* from its tags first, then its question. same mapping as the frontend. */
function chart_asset($d) {
    $map = array(
        'bitcoin' => 'BTC', 'btc' => 'BTC',
        'ethereum' => 'ETH', 'eth' => 'ETH', 'ether' => 'ETH',
        'xrp' => 'XRP', 'ripple' => 'XRP',
        'solana' => 'SOL', 'sol' => 'SOL',
        'dogecoin' => 'DOGE', 'doge' => 'DOGE',
        'bnb' => 'BNB', 'binance' => 'BNB',
        'hyperliquid' => 'HYPE', 'hype' => 'HYPE',
        'zcash' => 'ZEC', 'zec' => 'ZEC',
    );
    $tags = isset($d['tags']) && is_array($d['tags']) ? $d['tags'] : array();
    foreach ($tags as $t) {
        $s = isset($t['slug']) ? strtolower((string) $t['slug']) : '';
        if ($s !== '' && isset($map[$s])) return $map[$s];
    }
    $q = '';
    if (isset($d['question'])) {
        $q = is_array($d['question']) && isset($d['question']['en'])
            ? (string) $d['question']['en'] : (string) $d['question'];
    }
    $ql = strtolower($q);
    $names = array(
        array('bitcoin', 'BTC'), array('ethereum', 'ETH'), array('ether', 'ETH'),
        array('xrp', 'XRP'), array('ripple', 'XRP'), array('solana', 'SOL'),
        array('dogecoin', 'DOGE'), array('doge', 'DOGE'), array('bnb', 'BNB'),
        array('hyperliquid', 'HYPE'), array('zcash', 'ZEC'),
    );
    foreach ($names as $pair) {
        if (strpos($ql, $pair[0]) !== false) return $pair[1];
    }
    return null;
}

function chart_window_secs($d) {
    $tags = isset($d['tags']) && is_array($d['tags']) ? $d['tags'] : array();
    foreach ($tags as $t) {
        $s = isset($t['slug']) ? strtoupper((string) $t['slug']) : '';
        if ($s === '5M') return 300;
        if ($s === '15M') return 900;
        if ($s === '1H') return 3600;
        if ($s === '4H') return 14400;
        if ($s === '1D') return 86400;
    }
    return 300;
}

/* asset price over the window as one-minute candles, plus the window-start price: the line the average has to end above for the first outcome to win. window not started yet: last hour, line not set. the feed answers at most 300 candles per call, so long windows are fetched in chunks. nothing invented: no feed, no chart. */
function action_chart() {
    global $BASE, $COINBASE_MAP;
    $id = clean_id(isset($_GET['market_id']) ? $_GET['market_id'] : null, 120);
    if ($id === null) fail('A valid market_id is required.', 400);
    $body = fetch_body($BASE . '/api/v4/prediction/markets/' . rawurlencode($id));
    if ($body === null) fail('Could not reach the market data service.');
    $d = json_decode($body, true);
    if (!is_array($d)) fail('Could not read the market.', 502);
    $end = isset($d['end_date']) ? (int) $d['end_date'] : 0;
    if ($end <= 0) fail('This market has no end time.', 502);
    $asset = chart_asset($d);
    if ($asset === null || !isset($COINBASE_MAP[$asset])) {
        echo json_encode(array(
            'available' => false, 'reason' => 'no_price_feed',
            'market_id' => $id, 'asset' => $asset,
        ));
        return;
    }
    $win = chart_window_secs($d);
    $winStart = $end - $win;
    $now = time();
    $started = $now >= $winStart;
    $ended = $now >= $end;
    if ($started) {
        $from = $winStart;
        $to = min($end, $now);
    } else {
        $from = $now - 3600;
        $to = $now;
    }
    $fromMin = (int) floor($from / 60) * 60;
    $toMin = (int) floor($to / 60) * 60 - 60;
    if ($toMin < $fromMin) {
        echo json_encode(array(
            'available' => false, 'reason' => 'no_prices',
            'market_id' => $id, 'asset' => $asset,
            'window_secs' => $win, 'win_start' => $winStart, 'win_end' => $end,
            'started' => $started, 'ended' => $ended,
        ));
        return;
    }
    /* 300-minute chunks, the most the feed answers. the chunks go out at once, in parallel. */
    $ranges = array();
    $chunkStart = $fromMin - 60;
    while ($chunkStart <= $toMin) {
        $chunkEnd = min($chunkStart + 300 * 60, $toMin + 60);
        $ranges[] = array($chunkStart, $chunkEnd);
        $chunkStart = $chunkEnd;
    }
    $all = coinbase_candles_multi($COINBASE_MAP[$asset], $ranges, 60);
    if ($all === null) {
        echo json_encode(array(
            'available' => false, 'reason' => 'price_feed_unavailable',
            'market_id' => $id, 'asset' => $asset,
            'window_secs' => $win, 'win_start' => $winStart, 'win_end' => $end,
            'started' => $started, 'ended' => $ended,
        ));
        return;
    }
    $pts = array();
    $seen = array();
    foreach ($all as $row) {
        if (!is_array($row) || count($row) < 5) continue;
        $t = (int) $row[0];
        $close = num_or_null($row[4]);
        if ($close === null) continue;
        if ($t < $fromMin || $t > $toMin) continue;
        $pts[$t] = $close;
    }
    ksort($pts);
    $list = array();
    foreach ($pts as $t => $close) $list[] = array($t, $close);
    if (empty($list)) {
        echo json_encode(array(
            'available' => false, 'reason' => 'no_prices',
            'market_id' => $id, 'asset' => $asset,
            'window_secs' => $win, 'win_start' => $winStart, 'win_end' => $end,
            'started' => $started, 'ended' => $ended,
        ));
        return;
    }
    /* at most 120 points, keeping the first and the last, so the chart stays light. */
    if (count($list) > 120) {
        $n = count($list);
        $step = (int) ceil($n / 120);
        $keep = array();
        for ($i = 0; $i < $n; $i += $step) $keep[] = $list[$i];
        if (end($keep) !== end($list)) $keep[] = end($list);
        $list = $keep;
    }
    /* window-start minute close. if that minute has no candle, the earliest price we have, and the reply says so. */
    $line = null;
    $lineSource = null;
    if ($started) {
        if (isset($pts[$winStart])) {
            $line = $pts[$winStart];
            $lineSource = 'window_start';
        } else {
            $line = $list[0][1];
            $lineSource = 'earliest';
        }
    }
    $closes = array_column($list, 1);
    $current = end($closes);
    $average = array_sum($closes) / count($closes);
    echo json_encode(array(
        'available' => true, 'market_id' => $id, 'asset' => $asset,
        'window_secs' => $win, 'win_start' => $winStart, 'win_end' => $end,
        'started' => $started, 'ended' => $ended,
        'line' => $line, 'line_source' => $lineSource,
        'current' => $current, 'average' => round($average, 6),
        'points' => $list,
        'fetched_at' => gmdate('c', $now),
    ));
}

/* ticker */

/* price and 24h move for the eight assets. the stats reply carries the price 24 hours ago (open) and the last price, so the move is (last - open) / open. one small request per asset, cached 30 seconds. an asset whose price cannot be read is left out; when none can be read, the reply says so. */
function coingecko_ticker($assets) {
    /* fast path: one CoinGecko call for all eight, well under a second against about 2.3s for eight parallel Coinbase calls. CoinGecko's 24h change is a percent, so it is divided by 100 to match the fraction the frontend expects. null when anything is missing, so the caller falls back to Coinbase. */
    /* TODO: CoinGecko's 24h window differs from Coinbase's by up to ~0.8pp on volatile days. live with it for now, or take the change from Coinbase and lose the speed. */
    global $UA;
    $ids = array(
        'BTC' => 'bitcoin', 'ETH' => 'ethereum', 'XRP' => 'ripple',
        'SOL' => 'solana', 'DOGE' => 'dogecoin', 'BNB' => 'binancecoin',
        'HYPE' => 'hyperliquid', 'ZEC' => 'zcash',
    );
    $url = 'https://api.coingecko.com/api/v3/simple/price?ids='
        . implode(',', array_values($ids))
        . '&vs_currencies=usd&include_24hr_change=true';
    $ch = curl_init($url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);
    curl_setopt($ch, CURLOPT_MAXREDIRS, 3);
    curl_setopt($ch, CURLOPT_TIMEOUT, 6);
    curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 3);
    curl_setopt($ch, CURLOPT_USERAGENT, $UA);
    curl_setopt($ch, CURLOPT_HTTPHEADER, array('Accept: application/json'));
    $body = curl_exec($ch);
    $code = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    if ($body === false || $body === '' || $code < 200 || $code >= 300) return null;
    $d = json_decode($body, true);
    if (!is_array($d)) return null;
    $out = array();
    foreach ($assets as $code2 => $label) {
        if (!isset($ids[$code2]) || !isset($d[$ids[$code2]])) return null;
        $row = $d[$ids[$code2]];
        $last = num_or_null(isset($row['usd']) ? $row['usd'] : null);
        if ($last === null || $last <= 0) return null;
        $chg = num_or_null(isset($row['usd_24h_change']) ? $row['usd_24h_change'] : null);
        $out[] = array(
            'asset' => $code2, 'label' => $label,
            'price' => $last, 'chg24' => ($chg === null ? null : $chg / 100),
        );
    }
    return $out;
}

function action_ticker() {
    global $COINBASE_MAP;
    $assets = array(
        'BTC' => 'Bitcoin', 'ETH' => 'Ethereum', 'XRP' => 'XRP',
        'SOL' => 'Solana', 'DOGE' => 'Dogecoin', 'BNB' => 'BNB',
        'HYPE' => 'Hyperliquid', 'ZEC' => 'ZCash',
    );
    $hit = cache_get_ttl('ticker-v1', 30);
    if ($hit !== null) {
        echo $hit;
        return;
    }
    /* CoinGecko first; if it fails, times out, or comes back short of any asset, fall back to the parallel Coinbase fetch below. */
    $out = coingecko_ticker($assets);
    if ($out === null) {
        $out = array();
        /* the eight stats requests go out at once, in parallel: one after another, the first paint took seconds on a cold cache. */
        $urls = array();
        foreach ($assets as $code => $label) {
            $urls[$code] = 'https://api.exchange.coinbase.com/products/' . $COINBASE_MAP[$code] . '/stats';
        }
        $bodies = upstream_get_multi($urls);
        foreach ($assets as $code => $label) {
            $body = isset($bodies[$code]) ? $bodies[$code] : null;
            if ($body === null) continue;
            $d = json_decode($body, true);
            if (!is_array($d)) continue;
            $last = num_or_null(isset($d['last']) ? $d['last'] : null);
            $open = num_or_null(isset($d['open']) ? $d['open'] : null);
            if ($last === null || $last <= 0) continue;
            $chg = ($open !== null && $open > 0) ? ($last - $open) / $open : null;
            $out[] = array(
                'asset' => $code, 'label' => $label,
                'price' => $last, 'chg24' => $chg,
            );
        }
    }
    if (empty($out)) fail('The price feed could not be reached.');
    $payload = json_encode(array(
        'assets' => $out,
        'fetched_at' => gmdate('c', time()),
    ));
    cache_put('ticker-v1', $payload);
    echo $payload;
}

/* score ended markets that have not been scored yet, then train and check the challenger. bounded, so a pass never holds a request for long. at most one pass every 10 minutes. */
function learn_pass($maxScore = 4) {
    global $LEARN_LOG, $LEARN_PASS_GAP, $LEARN_MIN_SAMPLES, $LEARN_PROMOTE_MARGIN;
    $state = learn_load_state();
    $now = time();
    if ($now - (int) $state['last_pass'] < $LEARN_PASS_GAP) return $state;
    $state['last_pass'] = $now;
    $samples = learn_load_samples();
    $scored = array();
    foreach ($samples as $s) $scored[$s['id']] = true;
    $byMarket = array();
    if (is_file($LEARN_LOG)) {
        $lines = @file($LEARN_LOG, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        if ($lines !== false) {
            foreach ($lines as $ln) {
                $d = json_decode($ln, true);
                if (!is_array($d) || !isset($d['id'], $d['t'], $d['end'])) continue;
                if ((int) $d['end'] > $now - 60) continue;
                if (isset($scored[$d['id']])) continue;
                $id = $d['id'];
                if (!isset($byMarket[$id]) || (int) $d['t'] > (int) $byMarket[$id]['t']) {
                    $byMarket[$id] = $d;
                }
            }
        }
    }
    uasort($byMarket, function ($a, $b) { return (int) $a['end'] - (int) $b['end']; });
    $n = 0;
    foreach ($byMarket as $id => $rec) {
        if ($n >= $maxScore) break;
        $out = reported_outcome($id);
        $src = 'reported';
        if ($out === null) {
            $winStart = (int) $rec['end'] - (int) $rec['win'];
            $out = price_outcome(isset($rec['asset']) ? $rec['asset'] : '', $winStart, (int) $rec['end']);
            $src = 'price';
        }
        if ($out === null) continue;
        $samples[] = array(
            'id' => $id, 'asset' => isset($rec['asset']) ? $rec['asset'] : '',
            'p' => (float) $rec['p'], 'price' => (float) $rec['price'],
            'spread' => (float) $rec['spread'], 'mins' => (int) $rec['mins'],
            'vol' => (float) $rec['vol'], 'end' => (int) $rec['end'], 'win' => (int) $rec['win'],
            'outcome' => (int) $out, 'source' => $src, 't' => $now,
        );
        $n++;
    }
    $state['scored'] = count($samples);
    $correct = 0;
    foreach ($samples as $s) {
        $call = ($s['p'] >= 0.5) ? 1 : 0;
        if ($call === (int) $s['outcome']) $correct++;
    }
    $state['correct'] = $correct;
    if (count($samples) >= $LEARN_MIN_SAMPLES) {
        $sorted = $samples;
        usort($sorted, function ($a, $b) { return (int) $a['end'] - (int) $b['end']; });
        $total = count($sorted);
        $nTest = max(10, (int) floor($total * 0.3));
        $nTrain = $total - $nTest;
        if ($nTrain >= 10 && $nTest >= 10) {
            $train = array_slice($sorted, 0, $nTrain);
            $test = array_slice($sorted, $nTrain);
            $w = learn_fit($train);
            $outs = array(); $champPreds = array(); $challPreds = array();
            foreach ($test as $s) {
                $outs[] = (int) $s['outcome'];
                if ($state['champion'] === 'learned' && is_array($state['weights'])) {
                    $champPreds[] = learn_predict($state['weights'], (float) $s['p'], (int) $s['mins'], (float) $s['vol'], (float) $s['spread']);
                } else {
                    $champPreds[] = (float) $s['p'];
                }
                $challPreds[] = learn_predict($w, (float) $s['p'], (int) $s['mins'], (float) $s['vol'], (float) $s['spread']);
            }
            $bc = learn_brier($champPreds, $outs);
            $bl = learn_brier($challPreds, $outs);
            $state['brier_champion'] = $bc;
            $state['brier_challenger'] = $bl;
            if ($bl !== null && $bc !== null) {
                if ($bl <= $bc - $LEARN_PROMOTE_MARGIN) {
                    $state['champion'] = 'learned';
                    $state['weights'] = $w;
                } elseif ($bc <= $bl - $LEARN_PROMOTE_MARGIN) {
                    $state['champion'] = 'formula';
                    $state['weights'] = null;
                }
                /* Within the margin: keep the current champion. */
            }
            $state['last_trained'] = gmdate('c', $now);
        }
    }
    learn_save_samples($samples);
    learn_save_state($state);
    return $state;
}

function action_record_forecasts() {
    $raw = file_get_contents('php://input');
    $data = json_decode($raw, true);
    if (!is_array($data) || !isset($data['items']) || !is_array($data['items'])) {
        fail('A JSON body with an items list is required.', 400);
    }
    $items = array_slice($data['items'], 0, 200);
    $recs = array();
    $now = time();
    foreach ($items as $it) {
        if (!is_array($it)) continue;
        $id = clean_id(isset($it['id']) ? $it['id'] : null, 120);
        if ($id === null) continue;
        $asset = isset($it['asset']) ? strtoupper(trim((string) $it['asset'])) : '';
        if ($asset !== '' && !preg_match('/^[A-Z0-9]{1,10}$/', $asset)) continue;
        $p = num_or_null(isset($it['p']) ? $it['p'] : null);
        $price = num_or_null(isset($it['price']) ? $it['price'] : null);
        if ($p === null || $p < 0 || $p > 1 || $price === null || $price < 0 || $price > 1) continue;
        $spread = num_or_null(isset($it['spread']) ? $it['spread'] : null);
        $mins = isset($it['mins']) ? (int) $it['mins'] : 0;
        if ($mins < 0) $mins = 0;
        if ($mins > 100000) $mins = 100000;
        $vol = num_or_null(isset($it['vol']) ? $it['vol'] : null);
        if ($vol === null || $vol < 0) $vol = 0;
        $end = isset($it['end']) ? (int) $it['end'] : 0;
        if ($end <= 0) continue;
        $win = isset($it['win']) ? (int) $it['win'] : 300;
        if (!in_array($win, array(300, 900, 3600, 14400, 86400), true)) $win = 300;
        $label0 = isset($it['label0']) ? substr((string) $it['label0'], 0, 20) : 'Up';
        $recs[] = array(
            't' => $now, 'id' => $id, 'asset' => $asset, 'label0' => $label0,
            'p' => round($p, 3), 'price' => round($price, 3),
            'spread' => $spread === null ? null : round($spread, 3),
            'mins' => $mins, 'vol' => round($vol, 2), 'end' => $end, 'win' => $win,
        );
    }
    $added = learn_append_records($recs);
    learn_pass(1);
    echo json_encode(array('recorded' => $added, 'received' => count($recs)));
}

function action_learning() {
    global $LEARN_MIN_SAMPLES;
    $state = learn_pass(4);
    $samples = learn_load_samples();
    $n = count($samples);
    $correct = 0;
    foreach ($samples as $s) {
        $call = ($s['p'] >= 0.5) ? 1 : 0;
        if ($call === (int) $s['outcome']) $correct++;
    }
    echo json_encode(array(
        'champion' => $state['champion'],
        'weights' => $state['weights'],
        'scored' => $n,
        'correct' => $correct,
        'brier_champion' => isset($state['brier_champion']) ? $state['brier_champion'] : null,
        'brier_challenger' => isset($state['brier_challenger']) ? $state['brier_challenger'] : null,
        'last_trained' => isset($state['last_trained']) ? $state['last_trained'] : null,
        'samples_needed' => $LEARN_MIN_SAMPLES,
    ));
}

/* routing */

$action = isset($_GET['action']) ? $_GET['action'] : 'markets';

if ($action === 'markets') {
    $limit = clean_int(isset($_GET['limit']) ? $_GET['limit'] : null, 100, 1, 100);
    $url = $BASE . '/api/v4/prediction/markets?limit=' . $limit;
    if (isset($_GET['cursor']) && $_GET['cursor'] !== '') {
        /* The cursor is a JWT-style token (base64url segments
           joined by dots, 266 characters on the live listing)
           and may also carry base64 padding = or +. It is
           URL-encoded into the upstream query, so none of those
           characters can break out of the parameter. */
        $cursor = clean_id($_GET['cursor'], 500, '=+.');
        if ($cursor !== null) $url .= '&cursor=' . rawurlencode($cursor);
    }
    if (isset($_GET['settlement_status']) && $_GET['settlement_status'] !== '') {
        $ss = clean_id($_GET['settlement_status'], 40);
        if ($ss !== null) $url .= '&settlement_status=' . rawurlencode($ss);
    }
    $body = fetch_body($url);
    if ($body === null) fail('Could not reach the market data service.');
    record_snapshot($body);
    echo $body;
} elseif ($action === 'market') {
    $id = clean_id(isset($_GET['id']) ? $_GET['id'] : null, 120);
    if ($id === null) fail('A valid market id is required.', 400);
    serve($BASE . '/api/v4/prediction/markets/' . rawurlencode($id));
} elseif ($action === 'prices') {
    $raw = isset($_GET['token_ids']) ? $_GET['token_ids'] : '';
    if (!is_string($raw) || $raw === '') fail('token_ids is required.', 400);
    $ids = array();
    foreach (explode(',', $raw) as $t) {
        $t = trim($t);
        $c = clean_id($t, 200);
        if ($c !== null) $ids[] = $c;
    }
    if (empty($ids)) fail('No valid token ids were given.', 400);
    $ids = array_slice(array_unique($ids), 0, 50);
    serve($BASE . '/api/v4/prediction/tokens/prices?token_ids=' . rawurlencode(implode(',', $ids)));
} elseif ($action === 'book') {
    $tid = clean_id(isset($_GET['token_id']) ? $_GET['token_id'] : null, 200);
    if ($tid === null) fail('A valid token_id is required.', 400);
    $depth = clean_int(isset($_GET['depth']) ? $_GET['depth'] : null, 20, 1, 100);
    serve($BASE . '/api/v4/prediction/tokens/' . rawurlencode($tid) . '/order_book?depth=' . $depth);
} elseif ($action === 'watchdog') {
    action_watchdog();
} elseif ($action === 'history') {
    action_history();
} elseif ($action === 'news') {
    action_news();
} elseif ($action === 'record_forecasts') {
    action_record_forecasts();
} elseif ($action === 'learning') {
    action_learning();
} elseif ($action === 'chart') {
    action_chart();
} elseif ($action === 'ticker') {
    action_ticker();
} else {
    fail('Unknown action. Use markets, market, prices, book, watchdog, history, news, record_forecasts, learning, chart or ticker.', 400);
}
