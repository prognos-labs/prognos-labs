package xyz.prognoslabs.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Result of the Android system biometric or device credential check.
 * The app never reads, stores, or sends any biometric data.
 */
sealed class BiometricResult {
    object Success : BiometricResult()
    object CancelledOrFailed : BiometricResult()
    object NotSetUp : BiometricResult()
}

/**
 * Single entry point for the trade biometric gate.
 * Uses the Android system BiometricPrompt only, with device passcode,
 * PIN, or pattern allowed as the system fallback.
 */
object BiometricGate {
    const val REFUSAL_FAILED_OR_CANCELLED =
        "I could not confirm it is you, so I have not placed this trade."

    const val REFUSAL_NOT_SET_UP =
        "I could not confirm it is you, so I have not placed this trade. " +
            "Set up a fingerprint, face, or passcode on your phone and try again."

    private const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun authenticate(
        activity: FragmentActivity,
        onResult: (BiometricResult) -> Unit
    ) {
        val manager = BiometricManager.from(activity)
        if (manager.canAuthenticate(ALLOWED_AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            onResult(BiometricResult.NotSetUp)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(BiometricResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Stop at once on cancel or error. Do not retry on our own.
                    onResult(BiometricResult.CancelledOrFailed)
                }

                override fun onAuthenticationFailed() {
                    // Let the system prompt handle retries as normal.
                }
            }
        )

        // Do not set negative button text when DEVICE_CREDENTIAL is enabled;
        // the system handles the cancel and passcode fallback controls.
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm this trade")
            .setSubtitle("Use your fingerprint, face, or passcode to confirm it is you.")
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

        prompt.authenticate(promptInfo)
    }
}

/*
TEST CHECKLIST
Test 1, yes without biometric: user says yes to the read back, then cancels the biometric prompt or fails it.
Expected: nothing executes. The voice says: I could not confirm it is you, so I have not placed this trade.

Test 2, yes with biometric success: user says yes to the read back, then passes fingerprint, face, or passcode.
Expected while trading is open: the order proceeds to execution, and only after the success result.
Expected while trading is closed: this test cannot reach the prompt, because Confirm stays disabled.

Test 3, yes with biometric cancel: user says yes to the read back, then taps cancel on the biometric prompt.
Expected: nothing executes at once, no retry on its own, and the voice says: I could not confirm it is you, so I have not placed this trade.

Test 4, Confirm button path: user taps Confirm instead of using voice.
Expected: the same biometric prompt appears before any execution, with the same success and cancel results as Tests 1 to 3.

Test 5, phone with no biometrics and no passcode: user says yes to the read back.
Expected: the trade is refused, nothing executes, and the voice gives the refusal line about setting up a fingerprint, face, or passcode.

Test 6, user says no to the read back:
Expected: no biometric prompt appears at all, and nothing executes.
*/
