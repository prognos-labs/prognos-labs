package xyz.fruitslice.prognos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.fruitslice.prognos.data.AuthState
import xyz.fruitslice.prognos.data.VOICE_ENROLL_PHRASE_POOL
import xyz.fruitslice.prognos.data.VoiceOrder
import xyz.fruitslice.prognos.data.VoiceParseResult
import xyz.fruitslice.prognos.data.parseVoiceUtterance
import xyz.fruitslice.prognos.model.Market
import xyz.fruitslice.prognos.model.Outcome
import xyz.fruitslice.prognos.model.TagDto
import xyz.fruitslice.prognos.ui.AppViewModel
import xyz.fruitslice.prognos.ui.HomeState
import xyz.fruitslice.prognos.ui.VoiceEngine
import xyz.fruitslice.prognos.ui.VoiceSheet

/**
 * v1.1.6: the voice screens render and behave, on the JVM under
 * Robolectric, through the real MainActivity. The box has no KVM and
 * no emulator can boot on it (x86_64 needs KVM, which the box CPU
 * does not have; this emulator build refuses ARM64 guests), so this
 * is the end-to-end rendering check: the single mic button in the
 * scaffold, the call-style overlay, the listening sheet, the confirm
 * card, enrollment and the change-password flow, in light and dark.
 *
 * There is no speech recognizer under Robolectric, so tapping the mic
 * shows the unavailable sheet, which is the real fallback path. The
 * listening and confirm sheets are driven to their states through the
 * view model's sheet state, which is exactly what the recognizer
 * path sets in production; the parsed order in the confirm test is a
 * real parse of a real spoken sentence.
 *
 * The rule is createAndroidComposeRule<MainActivity>, not
 * createComposeRule: the latter launches ComponentActivity, which
 * Robolectric cannot resolve because the ui-test-manifest declares
 * it without a MAIN/LAUNCHER filter, and adding that filter would
 * put a second launcher icon in the debug APK. MainActivity is the
 * real launcher activity, so it resolves. Sign-in is driven through
 * the auth store (the test never touches the live server), and the
 * market list is driven through the view model's home state, because
 * there is no live feed under Robolectric. Everything else is real
 * taps through the real screens: the bottom bar, the dashboard
 * avatar into Settings, the dark-mode toggle, the voice buttons.
 *
 * Two test-only gotchas, both found the hard way: the Voice section
 * sits below the fold on the long Settings page, so its buttons need
 * performScrollTo before the tap or the tap silently lands nowhere;
 * and the dark-mode toggle writes to DataStore on an IO thread, so
 * the test waits for the darkMode flow to flip instead of trusting
 * waitForIdle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoiceUiRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun vm(): AppViewModel =
        ViewModelProvider(composeRule.activity)[AppViewModel::class.java]

    @Suppress("UNCHECKED_CAST")
    private fun <T> setPrivateFlow(owner: Any, fieldName: String, value: T) {
        val field = owner.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        val flow = field.get(owner) as MutableStateFlow<T>
        flow.value = value
    }

    /** Signs in without the server. The restore in init has already
     * run by the time the test starts (waitForIdle), so this sticks. */
    private fun signIn() {
        composeRule.waitForIdle()
        val vm = vm()
        val authStoreField = AppViewModel::class.java.getDeclaredField("authStore")
        authStoreField.isAccessible = true
        val authStore = authStoreField.get(vm)
        setPrivateFlow<AuthState>(
            authStore, "_state",
            AuthState(loading = false, loggedIn = true, name = "Test", email = "test@example.com")
        )
        composeRule.waitForIdle()
    }

    /** Drives the sheet host to a state, as the recognizer path does. */
    private fun setSheet(sheet: VoiceSheet?) {
        setPrivateFlow<VoiceSheet?>(vm(), "_voiceSheet", sheet)
        composeRule.waitForIdle()
    }

    private fun setListeningText(text: String) {
        setPrivateFlow(vm(), "_voiceListeningText", text)
    }

    private fun setHomeMarkets(markets: List<Market>) {
        setPrivateFlow(vm(), "_home", HomeState(loading = false, markets = markets))
        composeRule.waitForIdle()
    }

    private fun goToMarkets() {
        signIn()
        composeRule.onNodeWithContentDescription("Markets").performClick()
        composeRule.waitForIdle()
    }

    /** The dashboard avatar (first letter of the name) opens Settings. */
    private fun goToSettings() {
        signIn()
        composeRule.onNodeWithText("T").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    /** The dashboard header toggle, the same one Flint taps. The flip
     * lands through DataStore on an IO thread, so wait for the flow.
     * Dark mode persists in DataStore, so a previous test in this
     * class may already have left it on; then there is nothing to tap. */
    private fun switchToDark() {
        if (composeRule.onAllNodesWithContentDescription("Switch to light mode")
                .fetchSemanticsNodes().isNotEmpty()
        ) return
        composeRule.onNodeWithContentDescription("Switch to dark mode").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) { vm().darkMode.value }
        composeRule.onNodeWithContentDescription("Switch to light mode").assertIsDisplayed()
    }

    private fun testMarket(): Market = Market(
        id = "btc-90k", question = "Will BTC close above \$90k?", status = "open",
        closed = false, start = 0, end = 0, volume = null, vol24 = null,
        last = null, bid = null, ask = null, p0 = 0.5, p1 = 0.5,
        outcomes = listOf(Outcome("Yes", "tok-up"), Outcome("No", "tok-down")),
        tags = listOf(TagDto(slug = "btc")), desc = "", winOutcome = null
    )

    private fun parsedOrder(): VoiceOrder {
        val r = parseVoiceUtterance("buy 50 Yes on BTC above 90k", listOf(testMarket()))
        return (r as VoiceParseResult.Order).order
    }

    @Test
    fun micButtonShowsOnMarkets() {
        goToMarkets()
        composeRule.onNodeWithContentDescription("Voice trading").assertIsDisplayed()
    }

    @Test
    fun micButtonShowsOnMarketsDark() {
        signIn()
        switchToDark()
        composeRule.onNodeWithContentDescription("Markets").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Voice trading").assertIsDisplayed()
    }

    @Test
    fun micButtonShowsOnHome() {
        // v1.1.12: the mic is the single entry point in the scaffold,
        // so it shows on every screen. The dashboard is the default
        // tab after sign-in, so no navigation is needed.
        signIn()
        composeRule.onNodeWithContentDescription("Voice trading").assertIsDisplayed()
    }

    @Test
    fun micButtonShowsOnMarketDetail() {
        goToMarkets()
        setHomeMarkets(listOf(testMarket()))
        composeRule.onNodeWithText("Will BTC close above \$90k?").assertIsDisplayed()
        composeRule.onNodeWithText("Will BTC close above \$90k?").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Voice trading").assertIsDisplayed()
    }

    @Test
    fun tappingMicWithNoEnrollmentShowsEnrollGate() {
        // v1.1.16: the model is bundled, so the mic is available
        // even before the first listen; with no voice set up, the
        // first tap asks to set it up instead of saying the voice
        // is unavailable.
        goToMarkets()
        composeRule.onNodeWithContentDescription("Voice trading").performClick()
        composeRule.onNodeWithText("Set up your voice first").assertIsDisplayed()
    }

    @Test
    fun conversationSheetShowsAnswerAndKeepsListening() {
        // v1.1.9: a conversational answer shows with the recognizer
        // still listening for the follow-up, so the user speaks again
        // without tapping the mic. The partial words show as they
        // come in, same as the listening sheet.
        goToMarkets()
        setListeningText("buy fifty yes")
        setSheet(VoiceSheet.Conversation("I'm fine, thank you. What would you like me to do?"))
        // The call view is scrollable: the bubbles sit below the
        // mic, so scroll them into view before asserting.
        composeRule.onNodeWithText("I'm fine, thank you. What would you like me to do?").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("buy fifty yes").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("End call").assertIsDisplayed()
    }

    /** Drives the recognizer path the way a Final event does, so the
     * answer is the real one the app would give on the phone. */
    private fun processVoiceFinal(text: String) {
        val method = AppViewModel::class.java.getDeclaredMethod(
            "processVoiceFinal", VoiceEngine.Event.Final::class.java
        )
        method.isAccessible = true
        method.invoke(vm(), VoiceEngine.Event.Final(text, listOf(text)))
        composeRule.waitForIdle()
    }

    @Test
    fun mostLiquidAnswerUsesLiveVolume() {
        // v1.1.9: "what was the most liquid market on Prognos Labs?"
        // answers from the live list, naming the highest-volume
        // market and spelling the platform name right.
        signIn()
        val high = testMarket().copy(vol24 = "1500000")
        val low = testMarket().copy(
            id = "btc-80k", question = "Will BTC close above \$80k?", vol24 = "800000"
        )
        setHomeMarkets(listOf(high, low))
        processVoiceFinal("what was the most liquid market on prognos labs")
        // The call view is scrollable: the answer bubble sits
        // below the mic, so scroll it into view before asserting.
        composeRule.onNodeWithText("most liquid market on Prognos Labs", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("BTC close above \$90k", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun listeningSheetShowsLiveWords() {
        goToMarkets()
        setListeningText("buy 50 yes on bitcoin")
        setSheet(VoiceSheet.Listening)
        composeRule.onNodeWithText("buy 50 yes on bitcoin").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("End call").assertIsDisplayed()
    }

    @Test
    fun confirmCardReadsBackTheOrder() {
        // v1.1.16: the read-back is a call-screen exchange, not a
        // card with buttons. The order shows in the app bubble with
        // "Say yes to trade.", and "Say yes or no" underneath.
        // There is no Confirm or Cancel button: you answer out
        // loud, and hanging up cancels.
        goToMarkets()
        setSheet(VoiceSheet.Confirm(parsedOrder(), tradingReady = true))
        composeRule.onNodeWithText("Buy 50 Yes on BTC close above \$90k", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Say yes or no").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("End call").assertIsDisplayed()
    }

    @Test
    fun confirmCardShowsTradingClosed() {
        // v1.1.16: with trading closed, the read-back still shows
        // and says so plainly. There is no Confirm button to
        // disable any more: the call screen has no buttons but the
        // hang-up.
        goToMarkets()
        setSheet(VoiceSheet.Confirm(parsedOrder(), tradingReady = false))
        composeRule.onNodeWithText("Trading is not open yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Say yes or no").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("End call").assertIsDisplayed()
    }

    @Test
    fun enrollScreenShowsThreePhrases() {
        goToSettings()
        composeRule.onNodeWithText("Set up your voice").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Set up your voice").assertIsDisplayed()
        // The phrases are dealt at random from the pool of 24, so
        // the test reads the assignment the screen was given instead
        // of expecting the old fixed three.
        val phrases = vm().enrollState.value.phrases
        assertEquals("Enrollment must deal three phrases", 3, phrases.size)
        assertEquals("Enrollment phrases must be distinct", 3, phrases.distinct().size)
        assertTrue("Enrollment phrases must come from the pool", phrases.all { it in VOICE_ENROLL_PHRASE_POOL })
        for (phrase in phrases) {
            composeRule.onNodeWithText("\"$phrase\"").assertIsDisplayed()
        }
        composeRule.onNodeWithText("0 of 3 done").assertIsDisplayed()
    }

    @Test
    fun settingsHasNoVoiceHistoryAndHasChangePassword() {
        // v1.1.12: the voice history page is gone, and change
        // password is an action that opens its own flow.
        goToSettings()
        composeRule.onAllNodesWithText("Voice history").fetchSemanticsNodes().let { nodes ->
            assertEquals("No voice history text may remain anywhere in settings", 0, nodes.size)
        }
        composeRule.onNodeWithText("Change password").assertIsDisplayed()
        composeRule.onNodeWithText("Help improve voice").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun changePasswordFlowShowsRequestStep() {
        // v1.1.14: change password is a drop-down row in Settings:
        // the first tap expands the row in place, the button inside
        // it opens the request flow.
        goToSettings()
        composeRule.onNodeWithText("Change password").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("A code goes to", substring = true).performScrollTo().assertIsDisplayed()
        // The expanded row holds a second "Change password" node,
        // the button: it opens the flow.
        composeRule.onAllNodesWithText("Change password")[1].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Change password").assertIsDisplayed()
        composeRule.onNodeWithText("Send code").assertIsDisplayed()
    }

    @Test
    fun enrollScreenRendersDark() {
        goToSettings()
        // Back to the dashboard for the dark toggle, then in again.
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        switchToDark()
        composeRule.onNodeWithText("T").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Set up your voice").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Set up your voice").assertIsDisplayed()
        composeRule.onNodeWithText("0 of 3 done").assertIsDisplayed()
    }

    @Test
    fun changePasswordFlowRendersDark() {
        goToSettings()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        switchToDark()
        composeRule.onNodeWithText("T").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Change password").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Change password")[1].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Change password").assertIsDisplayed()
        composeRule.onNodeWithText("Send code").assertIsDisplayed()
    }

    // ---- v1.1.14: video-review fixes ----

    @Test
    fun settingsShowsFourDropDownRows() {
        goToSettings()
        // The Account section sits below the fold on the long
        // settings page: each row is scrolled into view first.
        composeRule.onNodeWithText("Link wallet").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Change password").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Currency").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Default trade size").performScrollTo().assertIsDisplayed()
        // The old large blocks are gone: no wallet address field and
        // no save button until a row is opened.
        composeRule.onAllNodesWithText("Wallet address").fetchSemanticsNodes().let { nodes ->
            assertEquals("Wallet address must stay hidden until its row opens", 0, nodes.size)
        }
    }

    @Test
    fun walletRowExpandsInPlace() {
        goToSettings()
        composeRule.onNodeWithText("Link wallet").performScrollTo().performClick()
        composeRule.waitForIdle()
        // The expanded field opens below the row header, which may
        // sit at the bottom edge after the scroll: bring it up.
        composeRule.onNodeWithText("Wallet address").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Check link").performScrollTo().assertIsDisplayed()
        // The row stays a row: the other settings are still there.
        composeRule.onNodeWithText("Currency").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun enrollScreenShowsNoUnlockedAtZeroOfThree() {
        // v1.1.14: "Voice unlocked" shows only after all three
        // phrases in this session are done, never at zero of three.
        goToSettings()
        composeRule.onNodeWithText("Set up your voice").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("0 of 3 done").assertIsDisplayed()
        composeRule.onAllNodesWithText("Voice unlocked").fetchSemanticsNodes().let { nodes ->
            assertEquals("Voice unlocked must not show at 0 of 3", 0, nodes.size)
        }
    }

    @Test
    fun executingSheetShowsOrderPlacing() {
        goToMarkets()
        setSheet(VoiceSheet.Executing(parsedOrder()))
        composeRule.onNodeWithText("Placing your order").assertIsDisplayed()
        composeRule.onNodeWithText("Buy 50 Yes on BTC close above \$90k").assertIsDisplayed()
    }

    @Test
    fun executedSheetShowsFilledState() {
        goToMarkets()
        setSheet(VoiceSheet.Executed(parsedOrder(), true, "Order placed."))
        composeRule.onNodeWithText("Order filled").assertIsDisplayed()
        composeRule.onNodeWithText("Buy 50 Yes on BTC close above \$90k").assertIsDisplayed()
        composeRule.onNodeWithText("Order placed.").assertIsDisplayed()
        // v1.1.16: the call screen has no Done button. Hanging up
        // is how the call ends.
        composeRule.onNodeWithContentDescription("End call").assertIsDisplayed()
    }

    // ---- v1.1.15: video-review fixes ----

    @Test
    fun micButtonShowsInSettingsHeader() {
        // v1.1.15: the mic is a header button in Settings, so it
        // can no longer cover the "Help improve voice" toggle the
        // floating button sat on in the video.
        goToSettings()
        composeRule.onNodeWithContentDescription("Voice trading").assertIsDisplayed()
        composeRule.onNodeWithText("Help improve voice").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun marketRowShowsNoPriceYetForPricelessMarket() {
        // v1.1.15: a market with no bid, ask or last trade has no
        // price yet. The row says so instead of a bare dash.
        goToMarkets()
        setHomeMarkets(listOf(testMarket().copy(p0 = null, p1 = null)))
        composeRule.onNodeWithText("No price yet").assertIsDisplayed()
    }
}
