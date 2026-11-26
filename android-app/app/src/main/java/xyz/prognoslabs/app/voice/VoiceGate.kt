package xyz.prognoslabs.app.voice

/**
 * The speech gate for one listen: room-noise calibration, speech onset
 * detection, and the silence that ends an utterance.
 *
 * Pure logic, no Android framework, so it is unit-tested with synthetic
 * frames (see VoiceGateTest). The recognizer's runLoop feeds it one frame
 * at a time and writes audio to the segment only once the gate says
 * speech was heard. The cloud capture loop uses the same gate, so the
 * two paths can no longer drift apart the way they did in v1.1.24,
 * when the cloud gate calibrated on loud frames and the on-device gate
 * did not.
 *
 * The rule that matters most: only quiet frames may set the room noise
 * floor. If the user starts speaking the moment the sheet opens, those
 * loud frames are speech, not room noise. Averaging them into the floor
 * would raise the speech threshold above their own voice, so the gate
 * would reject everything they say and the listen would hear nothing.
 * That exact failure happened on v1.1.17, and the test
 * speechFromFirstFrameDetected pins the fix.
 *
 * The second rule: steady room noise must still raise the floor, or
 * the gate hears the room as speech. A short run of frames that are
 * all in the mid band and all close together is steady noise, not a
 * voice: real speech jumps around frame to frame, a fan or an air
 * conditioner does not. When three such frames arrive in a row, their
 * mean becomes the floor and the onset streak resets, so the noise
 * stops counting as speech. A run that breaks early was speech
 * starting, so those frames never raised the floor.
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

    // The current run of steady mid-band frames: frames that may be
    // room noise, waiting to prove they are steady before they are
    // allowed to raise the floor.
    private var stableRunSum = 0.0
    private var stableRunCount = 0

    /**
     * Feed one frame. Returns true when the frame was consumed by
     * calibration and the caller should skip it entirely (no segment
     * write, no finalize check), exactly like the old inline `continue`.
     * The caller keeps the frame in its pre-roll buffer either way, so
     * a word spoken at once is not lost when calibration drops it.
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

        if (!speechHeard) {
            if (isSteadyNoiseFrame(rms)) return false
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

    /**
     * Steady-noise absorption. A frame in the mid band joins the
     * current steady run when it is close to the run mean; a run of
     * STABLE_RUN_FRAMES raises the floor to the run mean and resets
     * the onset streak, so the noise stops counting as speech. A
     * frame that breaks the run, or sits outside the band, ends the
     * run without raising the floor: it may be speech starting.
     * Returns true when the frame was absorbed into a run (or by
     * raising the floor) and must not count toward onset.
     */
    private fun isSteadyNoiseFrame(rms: Double): Boolean {
        if (rms < WhisperRecognizer.SILENCE_RMS ||
            rms > WhisperRecognizer.NOISE_BAND_MAX_RMS
        ) {
            stableRunSum = 0.0
            stableRunCount = 0
            return false
        }
        if (stableRunCount == 0) {
            stableRunSum = rms
            stableRunCount = 1
            return true
        }
        val mean = stableRunSum / stableRunCount
        if (kotlin.math.abs(rms - mean) > mean * WhisperRecognizer.STABLE_RUN_TOLERANCE) {
            stableRunSum = 0.0
            stableRunCount = 0
            return false
        }
        stableRunSum += rms
        stableRunCount++
        if (stableRunCount >= WhisperRecognizer.STABLE_RUN_FRAMES) {
            noiseFloorRms = (stableRunSum / stableRunCount).coerceIn(150.0, 1200.0)
            stableRunSum = 0.0
            stableRunCount = 0
            onsetStreak = 0
        }
        return true
    }

    fun silenceMs(nowMs: Long): Long = if (speechHeard) nowMs - lastSpeechMs else 0L

    fun shouldFinalize(nowMs: Long, elapsedMs: Long): Boolean =
        WhisperRecognizer.shouldFinalize(speechHeard, silenceMs(nowMs), elapsedMs)
}
