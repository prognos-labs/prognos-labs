package xyz.prognoslabs.app.voice

/**
 * The speech gate for one listen: room-noise calibration, speech onset
 * detection, and the silence that ends an utterance.
 *
 * Pure logic, no Android framework, so it is unit-tested with synthetic
 * frames (see VoiceGateTest). The recognizer's runLoop feeds it one frame
 * at a time and writes audio to the segment only once the gate says
 * speech was heard.
 *
 * The rule that matters most: only quiet frames may set the room noise
 * floor. If the user starts speaking the moment the sheet opens, those
 * loud frames are speech, not room noise. Averaging them into the floor
 * would raise the speech threshold above their own voice, so the gate
 * would reject everything they say and the listen would hear nothing.
 * That exact failure happened on v1.1.17, and the test
 * speechFromFirstFrameDetected pins the fix.
 */
class VoiceGate {

    var noiseFloorRms: Double =
        WhisperRecognizer.SILENCE_RMS / WhisperRecognizer.NOISE_FLOOR_MULTIPLIER
        private set

    var speechHeard: Boolean = false
        private set

    private var noiseFloorSum = 0.0
    private var noiseFloorFrames = 0
    private var onsetStreak = 0
    private var lastSpeechMs = 0L

    /**
     * Feed one frame. Returns true when the frame was consumed by
     * calibration and the caller should skip it entirely (no segment
     * write, no finalize check), exactly like the old inline `continue`.
     */
    fun onFrame(rms: Double, nowMs: Long, elapsedMs: Long): Boolean {
        if (elapsedMs < WhisperRecognizer.CALIBRATION_MS && !speechHeard) {
            if (rms < WhisperRecognizer.SILENCE_RMS) {
                noiseFloorSum += rms
                noiseFloorFrames++
                noiseFloorRms =
                    (noiseFloorSum / noiseFloorFrames).coerceIn(150.0, 1200.0)
                return true
            }
        }

        if (!WhisperRecognizer.isSilentAdaptive(rms, noiseFloorRms)) {
            onsetStreak++
            if (onsetStreak >= WhisperRecognizer.ONSET_FRAMES_REQUIRED) {
                speechHeard = true
                lastSpeechMs = nowMs
            }
        } else {
            onsetStreak = 0
        }
        return false
    }

    fun silenceMs(nowMs: Long): Long = if (speechHeard) nowMs - lastSpeechMs else 0L

    fun shouldFinalize(nowMs: Long, elapsedMs: Long): Boolean =
        WhisperRecognizer.shouldFinalize(speechHeard, silenceMs(nowMs), elapsedMs)
}
