package xyz.prognoslabs.app.data

/**
 * Amount input for the market-detail trade panel. Pure, so unit tests
 * can run it on the JVM. Keeps digits and decimal points only, exactly
 * the filter the trade panel used before v9; the value stays a plain
 * string and is parsed with toDoubleOrNull() where it is used.
 */
fun sanitizeAmountInput(raw: String): String =
    raw.filter { ch -> ch.isDigit() || ch == '.' }
