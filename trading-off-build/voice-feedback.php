<?php
// Prognos Labs voice feedback endpoint.
// Standalone: this file is separate from the market site api.php and does not
// touch it. It only records anonymous failed phrases from the app into
// cache/voice-feedback.jsonl, which the existing cache/.htaccess denies
// from the web. POST only. It never returns the contents of the file.
//
// What is stored per report: the phrase text, the language code, the app
// version, and the time. Nothing else. No audio, no name, no email, no
// wallet address, no device identifier, no IP address. The app only sends
// a report when the user opted in with the voice feedback toggle, which
// is off by default.
header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

define('VOICE_FEEDBACK_FILE', __DIR__ . '/cache/voice-feedback.jsonl');
define('VOICE_FEEDBACK_MAX_PHRASE', 200);
define('VOICE_FEEDBACK_MAX_BODY', 2048);

/* Make sure cache/ can never be read over the web, even if the
 * .htaccess was not uploaded with the site. Creates it on first use. */
function feedback_ensure_cache_protected() {
    $dir = dirname(VOICE_FEEDBACK_FILE);
    if (!is_dir($dir)) {
        mkdir($dir, 0755, true);
    }
    $ht = $dir . '/.htaccess';
    if (!is_file($ht)) {
        $content = "# Deny web access to everything cached here. Voice feedback\n"
            . "# (voice-feedback.jsonl) and every other file in this\n"
            . "# directory must never be readable over the web.\n"
            . "# Only the placeholder page stays public.\n"
            . "Require all denied\n"
            . "<Files \"index.html\">\n"
            . "    Require all granted\n"
            . "</Files>\n"
            . "Options -Indexes\n";
        @file_put_contents($ht, $content, LOCK_EX);
    }
    $idx = $dir . '/index.html';
    if (!is_file($idx)) {
        @file_put_contents($idx, "<!doctype html><title>Prognos Labs</title><p>Nothing here.</p>\n", LOCK_EX);
    }
}

function feedback_fail($code, $error, $field = null) {
    http_response_code($code);
    $out = array('ok' => false, 'error' => $error);
    if ($field !== null) {
        $out['field'] = $field;
    }
    echo json_encode($out);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    feedback_fail(405, 'method');
}

feedback_ensure_cache_protected();

$raw = file_get_contents('php://input');
if ($raw === false || strlen($raw) > VOICE_FEEDBACK_MAX_BODY) {
    feedback_fail(400, 'bad_request');
}
$data = json_decode($raw, true);
if (!is_array($data)) {
    feedback_fail(400, 'bad_request');
}

// Only these three fields are accepted. Anything else in the body is
// ignored, never stored.
$phrase = isset($data['phrase']) ? trim((string) $data['phrase']) : '';
// 1 to 200 UTF-8 characters, no control characters. The dot with the u flag
// counts characters, not bytes, so this works with or without mbstring.
if (preg_match('/^.{1,' . VOICE_FEEDBACK_MAX_PHRASE . '}$/us', $phrase) !== 1 ||
    preg_match('/[\x00-\x1F\x7F]/', $phrase) === 1) {
    feedback_fail(400, 'bad_phrase', 'phrase');
}

$language = isset($data['language']) ? (string) $data['language'] : '';
if (preg_match('/^[a-z]{2}(-[A-Z]{2})?$/', $language) !== 1) {
    $language = 'en';
}

$version = isset($data['version']) ? (string) $data['version'] : '';
if (preg_match('/^[0-9]+\.[0-9]+\.[0-9]+$/', $version) !== 1) {
    $version = '';
}

$dir = dirname(VOICE_FEEDBACK_FILE);
if (!is_dir($dir)) {
    mkdir($dir, 0755, true);
}

$fp = fopen(VOICE_FEEDBACK_FILE, 'c+');
if ($fp === false) {
    feedback_fail(500, 'store');
}
flock($fp, LOCK_EX);
$record = array(
    'ts' => gmdate('c'),
    'phrase' => $phrase,
    'language' => $language,
    'version' => $version,
);
fseek($fp, 0, SEEK_END);
fwrite($fp, json_encode($record, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE) . "\n");
flock($fp, LOCK_UN);
fclose($fp);

echo json_encode(array('ok' => true));
