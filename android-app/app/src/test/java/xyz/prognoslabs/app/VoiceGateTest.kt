package xyz.prognoslabs.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.prognoslabs.app.voice.VoiceGate

/**
 * v1.1.18: the speech gate is tested with synthetic frames before any
 * build goes to a phone. v1.1.17 failed exactly here: the user speaks
 * the moment the sheet opens, the first loud frames were averaged into
 * the room noise floor, the speech threshold rose above his own voice,
 * and the listen heard nothing while the bars still moved.
 */
class VoiceGateTest {

    // One frame every 100 ms, like the recognizer reading its buffers.
    private fun feed(gate: VoiceGate, frames: List<Double>): VoiceGate {
        var now = 0L
        for (rms in frames) {
            gate.onFrame(rms, now, now)
            now += 100L
        }
        return gate
    }

    @Test
    fun speechAfterSilenceDetected() {
        val gate = feed(VoiceGate(), listOf(80.0, 90.0, 100.0, 1500.0, 1600.0, 1400.0))
        assertTrue(gate.speechHeard)
    }

    @Test
    fun speechFromFirstFrameDetected() {
        // The v1.1.17 failure: speech from the very first frame must
        // still be heard, and the noise floor must stay low instead of
        // being poisoned by the user's own voice.
        val gate = feed(VoiceGate(), listOf(1500.0, 1600.0, 1400.0, 1500.0))
        assertTrue(gate.speechHeard)
        assertTrue(gate.noiseFloorRms < 400.0)
    }

    @Test
    fun pureSilenceNeverDetects() {
        val gate = feed(VoiceGate(), List(30) { 80.0 })
        assertFalse(gate.speechHeard)
    }

    @Test
    fun noisyRoomStillDetects() {
        // Room noise near the silence line raises the floor, but loud
        // speech must still cross it.
        val gate = feed(VoiceGate(), listOf(350.0, 350.0, 350.0, 1500.0, 1600.0))
        assertTrue(gate.speechHeard)
    }

    @Test
    fun finalizeAfterSilence() {
        val gate = VoiceGate()
        var now = 0L
        for (rms in listOf(80.0, 90.0, 1500.0, 1600.0)) {
            gate.onFrame(rms, now, now)
            now += 100L
        }
        assertTrue(gate.speechHeard)
        assertFalse(gate.shouldFinalize(now, now))
        now += 800L
        gate.onFrame(80.0, now, now)
        assertTrue(gate.shouldFinalize(now, now))
    }
}
