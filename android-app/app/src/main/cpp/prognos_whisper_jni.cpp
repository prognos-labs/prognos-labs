// JNI bridge between the app and whisper.cpp v1.7.6.
// Three calls only: init (load the model file), transcribe (audio in,
// text out), free. The model file is bundled in the APK assets, copied
// into app-private storage on first use, and verified by size and SHA-256
// before it is loaded. Every call guards its inputs, so a bad handle or
// empty audio returns null instead of crashing.
#include <jni.h>

#include <string>
#include <vector>

#include <android/log.h>

#include "whisper.h"

#define LOG_TAG "PrognosWhisper"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

constexpr const char *DEFAULT_DOMAIN_PROMPT =
    "Prognos Labs, Bitcoin, BTC, Ethereum, ETH, Solana, SOL, XRP, "
    "Dogecoin, BNB, Zcash, HYPE, Buy, Sell, Yes, No, take profit, stop loss.";

std::string trim(const std::string &text) {
    const size_t first = text.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return std::string();
    const size_t last = text.find_last_not_of(" \t\r\n");
    return text.substr(first, last - first + 1);
}

std::string transcribeWithContext(
    whisper_context *ctx,
    const float *samples,
    int nSamples,
    const char *language,
    int nThreads,
    const char *initialPrompt
) {
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = language;
    params.n_threads = nThreads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    if (initialPrompt != nullptr && initialPrompt[0] != '\0') {
        params.initial_prompt = initialPrompt;
    }

    if (whisper_full(ctx, params, samples, nSamples) != 0) {
        LOGW("transcribe failed");
        return std::string();
    }

    const int nSegments = whisper_full_n_segments(ctx);
    std::string out;
    for (int i = 0; i < nSegments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text == nullptr) continue;
        const std::string segment = trim(text);
        if (segment.empty()) continue;
        if (!out.empty()) out += " ";
        out += segment;
    }
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_xyz_prognoslabs_app_voice_WhisperNative_nativeInit(
    JNIEnv *env, jobject /* thiz */, jstring modelPath
) {
    if (modelPath == nullptr) return 0;
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) return 0;
    whisper_context *ctx =
        whisper_init_from_file_with_params(path, whisper_context_default_params());
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) LOGW("model load failed");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_xyz_prognoslabs_app_voice_WhisperNative_nativeTranscribe(
    JNIEnv *env,
    jobject /* thiz */,
    jlong contextPtr,
    jshortArray pcm,
    jstring language,
    jint threads,
    jstring initialPrompt
) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(contextPtr);
    if (ctx == nullptr || pcm == nullptr) return nullptr;

    const jsize len = env->GetArrayLength(pcm);
    if (len <= 0) return nullptr;

    jshort *elems = env->GetShortArrayElements(pcm, nullptr);
    if (elems == nullptr) return nullptr;

    std::vector<float> samples(static_cast<size_t>(len));
    for (jsize i = 0; i < len; ++i) {
        samples[static_cast<size_t>(i)] = static_cast<float>(elems[i]) / 32768.0f;
    }
    env->ReleaseShortArrayElements(pcm, elems, JNI_ABORT);

    std::string langOwned;
    const char *lang = "en";
    if (language != nullptr) {
        const char *chars = env->GetStringUTFChars(language, nullptr);
        if (chars != nullptr) {
            langOwned = chars;
            env->ReleaseStringUTFChars(language, chars);
        }
    }
    if (!langOwned.empty()) lang = langOwned.c_str();

    std::string promptOwned;
    const char *prompt = DEFAULT_DOMAIN_PROMPT;
    if (initialPrompt != nullptr) {
        const char *pChars = env->GetStringUTFChars(initialPrompt, nullptr);
        if (pChars != nullptr) {
            promptOwned = pChars;
            env->ReleaseStringUTFChars(initialPrompt, pChars);
        }
    }
    if (!promptOwned.empty()) prompt = promptOwned.c_str();

    const int nThreads = threads < 1 ? 1 : (threads > 4 ? 4 : threads);
    const std::string text = transcribeWithContext(
        ctx, samples.data(), len, lang, nThreads, prompt
    );
    if (text.empty()) return nullptr;
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_xyz_prognoslabs_app_voice_WhisperNative_nativeFree(
    JNIEnv * /* env */, jobject /* thiz */, jlong contextPtr
) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(contextPtr);
    if (ctx != nullptr) whisper_free(ctx);
}

} // extern "C"

