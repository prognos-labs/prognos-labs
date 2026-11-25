package xyz.prognoslabs.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.prognoslabs.app.data.VoiceOrder
import xyz.prognoslabs.app.data.fill
import xyz.prognoslabs.app.data.isEnrollComplete
import xyz.prognoslabs.app.data.vocabFor

/**
 * Voice trading screens, built on the approved light and dark
 * designs: ivory, black, lime, the real logo nowhere faked. The mic is
 * tap-to-talk only; the app never listens unless the mic is pressed.
 *
 * v1.1.16: the voice is a full-screen call view, not a bottom sheet.
 * One mic entry point opens a call screen: the real logo at the top,
 * one big lime mic circle with waveform bars and "Listening..."
 * under it, your words as a bubble on the right as you speak, the
 * app's read-back as a bubble on the left, "Say yes or no" by voice,
 * and one red hang-up button at the bottom. No other buttons, no
 * floating button, no example hints, no paragraphs, no loading
 * state: the speech model ships inside the APK, so listening starts
 * at once. An order placed by voice executes visibly inside the
 * call view: the order card, a spinner while it places, then the
 * filled state. The voice history page is gone (Flint, 22 Nov):
 * there is no history screen and no button leading to one. The
 * history itself still lives only on the phone, in the local store.
 */

// ---- Mic button ----

/** The one voice entry point: a small mic icon in the top bar of
 * every screen, the same size and shape as the other header
 * buttons (theme, bell, avatar). v1.1.15: the old 64dp lime
 * floating button sat over every screen and covered content (the
 * voice toggle in Settings, the BTC row percentage in Markets,
 * the Transactions text in Wallet). A header button never
 * overlaps anything: it lives in the header row, in the layout,
 * on every screen. Tapping it opens the same voice sheet. */
@Composable
fun VoiceMicHeaderButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (micScale, micSource) = rememberPressScale()
    Box(
        modifier = modifier
            .size(40.dp)
            .border(1.dp, appColors().border, RoundedCornerShape(20.dp))
            .graphicsLayer(scaleX = micScale, scaleY = micScale)
            .clickable(interactionSource = micSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Voice trading",
            tint = appColors().ink,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ---- Call view (hosted once, above every screen) ----

/**
 * The voice call: a full-screen call view, exactly as the approved
 * mockups. The real logo at the top, one big lime mic circle with
 * waveform bars and "Listening..." under it, your words as a bubble
 * on the right as you speak, the app's read-back as a bubble on the
 * left, "Say yes or no" by voice, and one red hang-up button at
 * the bottom. No other buttons, no floating button, no example
 * hints, no paragraphs, no loading state. The back button hangs up
 * too. An order placed by voice executes inside the call view: the
 * order card, a spinner while it places, then the filled state.
 */
@Composable
fun VoiceSheetHost(
    vm: AppViewModel,
    onOpenVoiceEnroll: () -> Unit
) {
    val sheet by vm.voiceSheet.collectAsState()
    val listeningText by vm.voiceListeningText.collectAsState()
    val confirmListening by vm.confirmListening.collectAsState()
    val current = sheet ?: return
    val c = appColors()
    BackHandler { vm.hangUpVoice() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            LogoImage(48)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    when (current) {
                        is VoiceSheet.Listening -> VoiceListeningContent(
                            text = listeningText
                        )
                        is VoiceSheet.Conversation -> VoiceConversationContent(
                            text = current.text,
                            listeningText = listeningText
                        )
                        is VoiceSheet.VoiceCheck -> VoiceCheckContent(
                            challenge = current.challenge,
                            phase = current.phase,
                            note = current.note,
                            instructionFirst = fill(vocabFor(vm.voiceLanguage.value).challengeInstructionFirst, "phrase" to current.challenge),
                            instructionAgain = fill(vocabFor(vm.voiceLanguage.value).challengeInstructionAgain, "phrase" to current.challenge)
                        )
                        is VoiceSheet.Confirm -> VoiceConfirmContent(
                            order = current.order,
                            tradingReady = current.tradingReady,
                            confirmListening = confirmListening,
                            listeningText = listeningText
                        )
                        is VoiceSheet.Executing -> VoiceExecutingContent(
                            order = current.order
                        )
                        is VoiceSheet.Executed -> VoiceExecutedContent(
                            order = current.order,
                            success = current.success,
                            message = current.message
                        )
                        is VoiceSheet.Message -> VoiceMessageContent(
                            text = current.text,
                            isError = current.isError
                        )
                        is VoiceSheet.EnrollRequired -> VoiceEnrollRequiredContent(
                            onEnroll = {
                                vm.dismissVoiceSheet()
                                onOpenVoiceEnroll()
                            }
                        )
                        is VoiceSheet.Unavailable -> VoiceMessageContent(
                            text = "Voice is not available on this device. The tap buttons still work.",
                            isError = false
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            // Hang up: the call ends, and so does the session.
            // Not while an order is placing: the execution
            // finishes inside the call view first.
            if (current !is VoiceSheet.Executing) {
                VoiceCallHangUp(onHangUp = { vm.hangUpVoice() })
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/** The one button on the call screen, kept as its own piece so
 * the layout above stays readable. */
@Composable
private fun VoiceCallHangUp(onHangUp: () -> Unit) {
    val c = appColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(AskRed, CircleShape)
                .clickable { onHangUp() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CallEnd,
                contentDescription = "End call",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Hang up", fontSize = 14.sp, color = c.grey)
    }
}

/** The big mic: the centre of the call screen. It pulses while
 * the call is live. */
@Composable
private fun VoiceCallMicBlock() {
    val c = appColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val transition = rememberInfiniteTransition(label = "voiceMic")
        val pulse by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "voiceMicPulse"
        )
        Box(
            modifier = Modifier
                .size((120 * pulse).dp)
                .background(Lime, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = null,
                tint = Color(0xFF111111),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        VoiceWaveform()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Listening...", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
    }
}

/** The waveform under the mic: ink and lime bars, as the mockups
 * draw them. */
@Composable
private fun VoiceWaveform() {
    val c = appColors()
    val transition = rememberInfiniteTransition(label = "voiceWave")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 7) {
            val h by transition.animateFloat(
                initialValue = 6f,
                targetValue = 22f,
                animationSpec = infiniteRepeatable(
                    tween(500, delayMillis = i * 90),
                    RepeatMode.Reverse
                ),
                label = "voiceWaveBar$i"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(h.dp)
                    .background(if (i % 2 == 0) c.ink else Lime, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** Your words, as you speak them: a bubble on the right. The last
 * word heard sits on a lime chip, so the bubble grows word by word
 * while you talk. */
@Composable
private fun UserBubble(text: String) {
    val c = appColors()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Text("YOU", fontSize = 12.sp, fontFamily = PrognosMonoFamily, color = c.grey)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .background(c.surface, RoundedCornerShape(16.dp))
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    val words = text.trim().split(Regex("\\s+"))
                    words.forEachIndexed { i, w ->
                        if (i > 0) append(" ")
                        if (i == words.lastIndex) {
                            withStyle(SpanStyle(background = Lime, color = Color(0xFF111111))) {
                                append(w)
                            }
                        } else {
                            append(w)
                        }
                    }
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = c.ink
            )
        }
    }
}

/** The app's words: a bubble on the left. Black with ivory text
 * in light, lime with black text in dark, as the mockups draw it. */
@Composable
private fun AppBubble(text: String) {
    val c = appColors()
    val dark = c.isDark
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text("PROGNOS LABS", fontSize = 12.sp, fontFamily = PrognosMonoFamily, color = c.grey)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (dark) Lime else Color(0xFF111111),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (dark) Color(0xFF111111) else Color(0xFFF5F2EB)
            )
        }
    }
}

/**
 * The voice check pass: the app shows and speaks a fresh challenge
 * phrase, and the user says it back. WORDS is the recognizer pass
 * for the phrase; VOICE is the microphone pass that checks the
 * voice itself on those words.
 */
@Composable
private fun VoiceCheckContent(
    challenge: String,
    phase: VoiceCheckPhase,
    note: String?,
    instructionFirst: String,
    instructionAgain: String
) {
    val c = appColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VoiceCallMicBlock()
        Spacer(modifier = Modifier.height(20.dp))
        AppBubble(if (phase == VoiceCheckPhase.WORDS) instructionFirst else instructionAgain)
        if (note != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(note, fontSize = 14.sp, color = AskRed, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun VoiceListeningContent(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VoiceCallMicBlock()
        if (text.isNotBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            UserBubble(text)
        }
    }
}

/**
 * A conversational answer: the answer shows in the app bubble,
 * and the recognizer is still listening for the follow-up, so the
 * user speaks again without tapping the mic. The partial words
 * show as they come in, same as the listening view.
 */
@Composable
private fun VoiceConversationContent(
    text: String,
    listeningText: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VoiceCallMicBlock()
        if (listeningText.isNotBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            UserBubble(listeningText)
        }
        Spacer(modifier = Modifier.height(12.dp))
        AppBubble(text)
    }
}

/**
 * v1.1.16: the read-back is a call-screen exchange, not a card with
 * buttons. The order you spoke shows in your bubble, the read-back
 * in the app bubble, and "Say yes or no" underneath. There are no
 * Confirm or Cancel buttons: you answer out loud, and the card is
 * already listening. Hang up to cancel. Trading is closed, so a
 * yes says so plainly instead of placing anything.
 */
@Composable
private fun VoiceConfirmContent(
    order: VoiceOrder,
    tradingReady: Boolean,
    confirmListening: Boolean,
    listeningText: String
) {
    val c = appColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (confirmListening) {
            VoiceCallMicBlock()
            Spacer(modifier = Modifier.height(20.dp))
        }
        if (listeningText.isNotBlank()) {
            UserBubble(listeningText)
            Spacer(modifier = Modifier.height(12.dp))
        }
        AppBubble("${order.readback}. Say yes to trade.")
        Spacer(modifier = Modifier.height(12.dp))
        Text("Say yes or no", fontSize = 14.sp, color = c.grey)
        if (!tradingReady) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Please link your wallet to Prognos Labs first.", fontSize = 13.sp, color = c.grey)
        }
    }
}

/**
 * v1.1.14: the order placing, visibly inside the call view. The
 * order card stays on screen with a spinner while the same
 * placeOrder the tap buttons use runs. The call view never closes
 * mid-execution.
 */
@Composable
private fun VoiceExecutingContent(order: VoiceOrder) {
    val c = appColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Placing your order", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
        Spacer(modifier = Modifier.height(8.dp))
        Text(order.readback, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(color = c.ink, modifier = Modifier.size(28.dp))
    }
}

/**
 * v1.1.14: the filled state, still inside the call view. A lime
 * check and the result in plain words. Hanging up closes the call
 * view; there is no Done button on it.
 */
@Composable
private fun VoiceExecutedContent(
    order: VoiceOrder,
    success: Boolean,
    message: String
) {
    val c = appColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(if (success) Lime else c.surface2, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (success) Icons.Filled.Check else Icons.Filled.MicOff,
                contentDescription = null,
                tint = if (success) Color(0xFF111111) else c.grey,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (success) "Order filled" else "Order not placed",
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = c.ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(order.readback, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(message, fontSize = 14.sp, color = c.grey, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VoiceMessageContent(text: String, isError: Boolean) {
    val c = appColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (isError) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = null,
            tint = if (isError) AskRed else Lime,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 16.sp, color = c.ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VoiceEnrollRequiredContent(onEnroll: () -> Unit) {
    val c = appColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Set up your voice first", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Say three short phrases so the app learns your voice.",
            fontSize = 15.sp,
            color = c.ink,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        // The one button on this gate, and the deliberate exception
        // to the call screen's hang-up-only rule: without it, a
        // first tap on the mic with no voice set up would be a dead
        // end. Hanging up is the "not now".
        Button(
            onClick = onEnroll,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color(0xFF111111))
        ) {
            Text("Set up your voice", fontWeight = FontWeight.Bold)
        }
    }
}

// ---- Enrollment screen ----

@Composable
fun VoiceEnrollScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = appColors()
    val state by vm.enrollState.collectAsState()
    val enrolled by vm.voiceEnrolled.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c.surface2)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = c.ink, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Set up your voice", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Say each phrase out loud.",
            fontSize = 15.sp,
            color = c.ink
        )
        // v1.1.14: when a voice is already set up from an earlier
        // session, this screen is a re-do. Say so plainly, and show
        // this session's real progress: unlocked only after all
        // three phrases here are done.
        if (enrolled && !state.complete) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your voice is already set up. Say the phrases again to set it up again.",
                fontSize = 14.sp,
                color = c.grey
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        val doneCount = state.done.count { it }
        Text(
            "$doneCount of ${state.phrases.size} done",
            fontSize = 13.sp,
            fontFamily = PrognosMonoFamily,
            color = c.grey
        )
        Spacer(modifier = Modifier.height(12.dp))
        state.phrases.forEachIndexed { index, phrase ->
            val done = state.done.getOrNull(index) == true
            val active = state.current == index && !state.complete
            val listening = active && state.listening
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = c.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (active) Lime else c.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (done) BidGreen else if (listening) Lime else c.surface2, CircleShape)
                            .clickable(enabled = !done && !state.listening) { vm.enrollListen(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (done) Icons.Filled.Check else Icons.Filled.Mic,
                            contentDescription = if (done) "Done" else "Say this phrase",
                            tint = if (done || listening) Color.White else c.ink,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("\"$phrase\"", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            when {
                                done -> "Done"
                                // v1.1.14: the capture stops by itself
                                // once the phrase is heard and the user
                                // goes quiet. Stop is only the manual
                                // way out.
                                listening -> "Listening... it stops by itself when you finish"
                                else -> "Tap the mic and say it"
                            },
                            fontSize = 13.sp,
                            color = c.grey
                        )
                    }
                    if (listening) {
                        OutlinedButton(onClick = { vm.stopEnrollmentListening() }) {
                            Text("Stop")
                        }
                    }
                }
            }
        }
        val enrollError = state.error
        if (enrollError != null) {
            Text(enrollError, fontSize = 14.sp, color = AskRed)
            Spacer(modifier = Modifier.height(8.dp))
        }
        // v1.1.14: "Voice unlocked" shows only when all three
        // phrases in this session are done. The old code showed it
        // whenever a profile from an earlier session existed, even
        // at zero of three here.
        if (state.complete && isEnrollComplete(state.done)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = c.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Lime),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = BidGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Voice unlocked. Only your voice can trade by voice now.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.ink,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color(0xFF111111))
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}
