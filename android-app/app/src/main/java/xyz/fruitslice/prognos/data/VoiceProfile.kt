package xyz.fruitslice.prognos.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import java.util.Locale

/**
 * The voice lock, pure. No Android classes here, so unit tests can run
 * on the JVM.
 *
 * How it works, plainly: when the user sets up voice trading they say
 * three short phrases. From the sound of each phrase the app measures
 * a few things that differ from person to person — the pitch of the
 * voice, how much the pitch moves, the brightness of the sound, how
 * fast the person talks. Those measurements stay on the phone, in the
 * same encrypted store as the login token. Later, when a voice order
 * is spoken, the app measures the same things again and checks they
 * are close to the enrolled ones.
 *
 * Honest limit, stated in the app and here: this is a small
 * on-device check, not a biometric guarantee. A recording of the
 * owner could still fool it. That is why the login and the final
 * confirmation stay in place: they are the real protection, and the
 * voice check is one more layer on top.
 */

/**
 * Enrollment phrases: every user reads three phrases picked at
 * random from this pool of 24, and the assignment is stored with
 * the voice profile, so two users almost never enroll on the same
 * words. The pool stays trading-flavoured (orders, prices, the
 * platform), so enrollment still sounds like the app.
 */
val VOICE_ENROLL_PHRASE_POOL = listOf(
    "Buy fifty Yes on Bitcoin above ninety thousand",
    "Sell twenty No on Ethereum below three thousand",
    "What is my balance",
    "Buy ten Yes on Solana above two hundred",
    "Sell five No on Dogecoin below fifty cents",
    "Show me the most liquid market",
    "What is the latest news about Bitcoin",
    "Buy one hundred Yes on XRP above three dollars",
    "Sell thirty No on BNB below six hundred",
    "How much is Ethereum right now",
    "Open the Bitcoin market for me",
    "Buy twenty five Yes on Hype above forty",
    "What is the price of Zcash today",
    "Sell fifteen No on Solana below one eighty",
    "Show me my open positions",
    "Buy seventy five Yes on Bitcoin above one hundred thousand",
    "When does trading open on Prognos",
    "Sell forty No on XRP below two dollars",
    "What is the forecast for Ethereum",
    "Buy thirty Yes on Dogecoin above sixty cents",
    "Show me the news about Solana",
    "Sell ten No on Hype below thirty five",
    "How do I buy on Prognos Labs",
    "Buy sixty Yes on Zcash above four hundred"
)

/** Picks [count] distinct phrases from the pool, at random. Pure,
 * so tests can prove different seeds deal different hands and the
 * same seed deals the same hand. */
fun pickEnrollPhrases(pool: List<String>, count: Int, random: Random): List<String> =
    pool.shuffled(random).distinct().take(count)

/**
 * Challenge phrases for the trade-time check: short, plain
 * two-to-three-word phrases, a fresh one every time. The app shows
 * and speaks one before an order is confirmed, and the user must
 * say it; the voice check runs on that challenge utterance, so a
 * recording of the enrollment phrases cannot pass.
 *
 * Honest limit: this is challenge-response anti-replay, not true
 * liveness detection. It proves the speaker can say a fresh phrase
 * right now; it does not prove the speaker is live in the room.
 * The login and the read-back confirmation stay the real
 * protection, and no code anywhere claims otherwise.
 */
val VOICE_CHALLENGE_PHRASES = listOf(
    "blue river", "green apple", "morning light", "silver moon",
    "golden bridge", "swift falcon", "calm water", "bright star",
    "red bicycle", "warm summer", "deep ocean", "soft rain",
    "wild horses", "crisp autumn", "gentle breeze", "dark forest",
    "happy trader", "quiet harbour", "rapid current", "bold venture"
)

/** Picks one challenge phrase at random. Pure. */
fun pickChallengePhrase(pool: List<String>, random: Random): String =
    pool[random.nextInt(pool.size)]

/** True when the challenge phrase was said, in order, somewhere in
 * what was heard: extra words around it are fine, missing or
 * wrong words are not. Pure. */
fun challengeMatches(heard: String, challenge: String, language: VoiceLanguage = VoiceLanguage.ENGLISH): Boolean {
    val heardWords = normalizeHeard(heard, language).lowercase(Locale.US)
        .split(Regex("\\s+")).filter { it.isNotBlank() }
    val challengeWords = challenge.lowercase(Locale.US)
        .split(Regex("\\s+")).filter { it.isNotBlank() }
    if (challengeWords.isEmpty()) return false
    if (heardWords.size < challengeWords.size) return false
    return heardWords.windowed(challengeWords.size).any { it == challengeWords }
}

data class VoiceFeatures(
    /** Average pitch in Hz. */
    val meanF0Hz: Double,
    /** How much the pitch moves in Hz. */
    val f0StdHz: Double,
    /** Brightness of the sound in Hz. */
    val spectralCentroidHz: Double,
    /** Zero-crossing rate, 0..1. */
    val zeroCrossingRate: Double,
    /** Loudness in dB, relative to full scale. */
    val rmsDb: Double,
    /** Words per second, from the transcript and the clip length. */
    val wordsPerSecond: Double
)

data class VoiceProfile(
    val means: VoiceFeatures,
    val stds: VoiceFeatures,
    val sampleCount: Int
)

/** Serialised form, kept in the encrypted voice store. */
data class StoredVoiceProfile(
    val meanF0Hz: Double,
    val f0StdHz: Double,
    val spectralCentroidHz: Double,
    val zeroCrossingRate: Double,
    val rmsDb: Double,
    val wordsPerSecond: Double,
    val stdF0Hz: Double,
    val stdF0StdHz: Double,
    val stdSpectralCentroidHz: Double,
    val stdZeroCrossingRate: Double,
    val stdRmsDb: Double,
    val stdWordsPerSecond: Double,
    val sampleCount: Int
)

fun VoiceProfile.toStored(): StoredVoiceProfile = StoredVoiceProfile(
    meanF0Hz = means.meanF0Hz,
    f0StdHz = means.f0StdHz,
    spectralCentroidHz = means.spectralCentroidHz,
    zeroCrossingRate = means.zeroCrossingRate,
    rmsDb = means.rmsDb,
    wordsPerSecond = means.wordsPerSecond,
    stdF0Hz = stds.meanF0Hz,
    stdF0StdHz = stds.f0StdHz,
    stdSpectralCentroidHz = stds.spectralCentroidHz,
    stdZeroCrossingRate = stds.zeroCrossingRate,
    stdRmsDb = stds.rmsDb,
    stdWordsPerSecond = stds.wordsPerSecond,
    sampleCount = sampleCount
)

fun StoredVoiceProfile.toProfile(): VoiceProfile = VoiceProfile(
    means = VoiceFeatures(meanF0Hz, f0StdHz, spectralCentroidHz, zeroCrossingRate, rmsDb, wordsPerSecond),
    stds = VoiceFeatures(stdF0Hz, stdF0StdHz, stdSpectralCentroidHz, stdZeroCrossingRate, stdRmsDb, stdWordsPerSecond),
    sampleCount = sampleCount
)

private const val MIN_VOICED_RMS = 0.005

/**
 * Measures the voice features of a mono 16-bit clip. Returns null
 * when the clip is too quiet or has no voiced speech: the app must
 * ask the user to say the phrase again, not build a profile from
 * silence.
 */
fun extractVoiceFeatures(
    pcm: ShortArray,
    sampleRate: Int,
    wordsSpoken: Int = 0
): VoiceFeatures? {
    if (pcm.size < sampleRate / 2 || sampleRate <= 0) return null
    val x = DoubleArray(pcm.size) { pcm[it] / 32768.0 }
    var sumSq = 0.0
    for (v in x) sumSq += v * v
    val rms = sqrt(sumSq / x.size)
    if (rms < MIN_VOICED_RMS) return null
    val rmsDb = 20.0 * kotlin.math.log10(rms)

    var crossings = 0
    for (i in 1 until x.size) {
        if ((x[i - 1] < 0) != (x[i] < 0)) crossings++
    }
    val zcr = crossings.toDouble() / x.size

    val f0s = estimateF0PerFrame(x, sampleRate)
    if (f0s.isEmpty()) return null
    val meanF0 = f0s.average()
    val f0Std = sqrt(f0s.sumOf { (it - meanF0) * (it - meanF0) } / f0s.size)

    val centroid = spectralCentroid(x, sampleRate)
    val durationSec = pcm.size.toDouble() / sampleRate
    val wps = if (wordsSpoken > 0 && durationSec > 0) wordsSpoken / durationSec else 0.0
    return VoiceFeatures(meanF0, f0Std, centroid, zcr, rmsDb, wps)
}

/**
 * Pitch per 25 ms frame by autocorrelation, 50..400 Hz. A frame
 * counts only when it is loud enough and the autocorrelation peak is
 * clear, so silence and noise do not set the pitch.
 */
private fun estimateF0PerFrame(x: DoubleArray, sampleRate: Int): List<Double> {
    val frame = sampleRate / 40 // 25 ms
    val hop = sampleRate / 100 // 10 ms
    val minLag = sampleRate / 400
    val maxLag = sampleRate / 50
    val out = mutableListOf<Double>()
    var start = 0
    while (start + frame <= x.size) {
        var energy = 0.0
        for (i in start until start + frame) energy += x[i] * x[i]
        val frameRms = sqrt(energy / frame)
        if (frameRms >= MIN_VOICED_RMS) {
            var bestLag = -1
            var bestCorr = 0.0
            var corr0 = 0.0
            for (lag in 0..maxLag) {
                var c = 0.0
                for (i in 0 until frame - lag) c += x[start + i] * x[start + i + lag]
                if (lag == 0) corr0 = c
                if (lag in minLag..maxLag && corr0 > 0) {
                    val norm = c / corr0
                    if (norm > bestCorr) {
                        bestCorr = norm
                        bestLag = lag
                    }
                }
            }
            if (bestLag > 0 && bestCorr > 0.3) out.add(sampleRate.toDouble() / bestLag)
        }
        start += hop
    }
    return out
}

/** Rough brightness: energy-weighted mean frequency from a 256-point FFT. */
private fun spectralCentroid(x: DoubleArray, sampleRate: Int): Double {
    val n = 256
    val frames = listOf(x.size / 4, x.size / 2)
    var weightedSum = 0.0
    var total = 0.0
    for (centre in frames) {
        val start = (centre - n / 2).coerceIn(0, x.size - n)
        if (start < 0) continue
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in 0 until n) {
            val w = 0.5 * (1 - cos(2 * PI * i / (n - 1)))
            re[i] = x[start + i] * w
        }
        fft(re, im)
        for (k in 1 until n / 2) {
            val mag = sqrt(re[k] * re[k] + im[k] * im[k])
            val freq = k.toDouble() * sampleRate / n
            weightedSum += freq * mag
            total += mag
        }
    }
    return if (total > 0) weightedSum / total else 0.0
}

/** Iterative radix-2 FFT, in place. */
private fun fft(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j xor bit
        if (i < j) {
            val tr = re[i]; re[i] = re[j]; re[j] = tr
            val ti = im[i]; im[i] = im[j]; im[j] = ti
        }
    }
    var len = 2
    while (len <= n) {
        val ang = -2 * PI / len
        val wr = cos(ang)
        val wi = kotlin.math.sin(ang)
        var i = 0
        while (i < n) {
            var curWr = 1.0
            var curWi = 0.0
            for (k in 0 until len / 2) {
                val ur = re[i + k]
                val ui = im[i + k]
                val vr = re[i + k + len / 2] * curWr - im[i + k + len / 2] * curWi
                val vi = re[i + k + len / 2] * curWi + im[i + k + len / 2] * curWr
                re[i + k] = ur + vr
                im[i + k] = ui + vi
                re[i + k + len / 2] = ur - vr
                im[i + k + len / 2] = ui - vi
                val nextWr = curWr * wr - curWi * wi
                curWi = curWr * wi + curWi * wr
                curWr = nextWr
            }
            i += len
        }
        len *= 2
    }
}

/** Builds a profile from the enrollment samples (at least 3). */
fun buildVoiceProfile(samples: List<VoiceFeatures>): VoiceProfile? {
    if (samples.size < 3) return null
    fun mean(f: (VoiceFeatures) -> Double) = samples.map(f).average()
    fun std(f: (VoiceFeatures) -> Double): Double {
        val m = mean(f)
        return sqrt(samples.sumOf { (f(it) - m) * (f(it) - m) } / samples.size)
    }
    return VoiceProfile(
        means = VoiceFeatures(
            mean { it.meanF0Hz }, mean { it.f0StdHz }, mean { it.spectralCentroidHz },
            mean { it.zeroCrossingRate }, mean { it.rmsDb }, mean { it.wordsPerSecond }
        ),
        stds = VoiceFeatures(
            std { it.meanF0Hz }, std { it.f0StdHz }, std { it.spectralCentroidHz },
            std { it.zeroCrossingRate }, std { it.rmsDb }, std { it.wordsPerSecond }
        ),
        sampleCount = samples.size
    )
}

// Floors keep a tight enrollment from rejecting the same voice the
// next day: a real voice moves a little between readings.
private fun z(value: Double, mean: Double, std: Double, floor: Double): Double {
    val s = maxOf(std, floor)
    return abs(value - mean) / s
}

/**
 * How far a fresh reading sits from the enrolled profile, as a mean
 * absolute z-score across the features. 0 means identical; 1 means
 * about one standard deviation away on average.
 */
fun voiceMatchDistance(profile: VoiceProfile, features: VoiceFeatures): Double {
    val m = profile.means
    val s = profile.stds
    val zs = listOf(
        z(features.meanF0Hz, m.meanF0Hz, s.meanF0Hz, 12.0),
        z(features.f0StdHz, m.f0StdHz, s.f0StdHz, 8.0),
        z(features.spectralCentroidHz, m.spectralCentroidHz, s.spectralCentroidHz, 120.0),
        z(features.zeroCrossingRate, m.zeroCrossingRate, s.zeroCrossingRate, 0.02),
        z(features.rmsDb, m.rmsDb, s.rmsDb, 3.0),
        z(features.wordsPerSecond, m.wordsPerSecond, s.wordsPerSecond, 0.5)
    )
    return zs.average()
}

/**
 * Whether a fresh reading belongs to the enrolled voice. Pitch is
 * the most speaker-specific feature, so it gets its own stricter
 * check on top of the overall distance.
 */
fun voiceMatches(profile: VoiceProfile, features: VoiceFeatures): Boolean {
    val pitchZ = z(features.meanF0Hz, profile.means.meanF0Hz, profile.stds.meanF0Hz, 12.0)
    return pitchZ < 2.5 && voiceMatchDistance(profile, features) < 2.0
}

/** The voice-lock decision for one spoken order. */
enum class VoiceCheck {
    /** The reading matches the enrolled voice. */
    MATCHED,
    /** The reading does not match: do not trade by voice. */
    NOT_MATCHED,
    /** No usable audio was captured: do not trade by voice. */
    UNAVAILABLE
}

/**
 * v1.1.14: enrollment is complete only when all three phrases are
 * done. The enrollment screen used to show "Voice unlocked" whenever
 * a profile from an earlier session existed, even at zero of three
 * phrases in this session. The unlocked card is gated on this, never
 * on a stored profile alone.
 */
fun isEnrollComplete(done: List<Boolean>): Boolean =
    done.size == 3 && done.all { it }

fun voiceCheckDecision(profile: VoiceProfile?, features: VoiceFeatures?): VoiceCheck {
    if (profile == null || features == null) return VoiceCheck.UNAVAILABLE
    return if (voiceMatches(profile, features)) VoiceCheck.MATCHED else VoiceCheck.NOT_MATCHED
}
