package xyz.fruitslice.prognos.data

/**
 * Pure auth validation. No Android classes here, so unit tests can run on the JVM.
 * Server auth lives in AuthApi.kt / Repository.kt; these helpers only check
 * shapes (email, password length, OTP digits) before anything is sent.
 */
fun isValidEmail(email: String): Boolean {
    val e = email.trim()
    if (e.isEmpty() || e.contains(' ')) return false
    val at = e.indexOf('@')
    if (at < 1) return false
    val dot = e.indexOf('.', at)
    return dot > at + 1 && dot < e.length - 1
}

/**
 * Matches the server rule: at least 8 characters, with a letter and a
 * number. The client used to accept 6 characters, which the server
 * then rejected; the two now agree so the app never tells a user a
 * password is fine and then fails it.
 */
fun isValidPassword(password: String): Boolean {
    if (password.length < 8) return false
    return password.any { it.isLetter() } && password.any { it.isDigit() }
}

fun isValidName(name: String): Boolean = name.trim().length >= 2

/** Returns a plain error message, or null when the inputs are fine. */
fun authError(name: String, email: String, password: String, isSignup: Boolean): String? {
    if (isSignup && !isValidName(name)) return "Enter your name."
    if (!isValidEmail(email)) return "Enter a valid email address."
    if (!isValidPassword(password)) return "Password needs at least 8 characters, with a letter and a number."
    return null
}

/** A new password needs at least 8 characters. Sign-up now uses the same rule. */
fun isValidNewPassword(password: String): Boolean = isValidPassword(password)

/**
 * Change-password validation, pure so unit tests can run it on the JVM.
 * currentOk comes from checking the stored hash; the caller verifies it.
 * Returns a plain error message, or null when the change is fine.
 */
fun changePasswordError(currentOk: Boolean, newPassword: String, confirm: String): String? {
    if (!currentOk) return "Your current password is wrong."
    if (!isValidNewPassword(newPassword)) return "New password needs at least 8 characters."
    if (newPassword != confirm) return "New passwords do not match."
    return null
}
