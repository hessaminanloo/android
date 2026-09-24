#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <string>
#include <unistd.h>
#include <vector>

#ifdef MEETMATE_HAS_WHISPER
#include "whisper.h"
#endif

#define TAG "MeetMateWhisper"

static void throwState(JNIEnv* env, const char* message) {
    jclass error = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(error, message);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_hess_meetmate_domain_WhisperNativeBindings_init(JNIEnv* env, jclass, jstring modelPath) {
#ifndef MEETMATE_HAS_WHISPER
    throwState(env, "whisper.cpp sources are missing; clone them into third_party/whisper.cpp");
    return 0;
#else
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params params = whisper_context_default_params();
    whisper_context* context = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!context) { throwState(env, "Whisper model could not be loaded"); return 0; }
    __android_log_print(ANDROID_LOG_INFO, TAG, "Whisper model loaded");
    return reinterpret_cast<jlong>(context);
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_hess_meetmate_domain_WhisperNativeBindings_transcribe(JNIEnv* env, jclass, jlong handle, jshortArray input, jint count) {
#ifndef MEETMATE_HAS_WHISPER
    throwState(env, "whisper.cpp is not linked");
    return nullptr;
#else
    auto* context = reinterpret_cast<whisper_context*>(handle);
    if (!context || count <= 0) return nullptr;
    const jsize length = env->GetArrayLength(input);
    const jsize used = std::min<jsize>(count, length);
    jshort* raw = env->GetShortArrayElements(input, nullptr);
    std::vector<float> samples(static_cast<size_t>(used));
    for (jsize i = 0; i < used; ++i) samples[static_cast<size_t>(i)] = static_cast<float>(raw[i]) / 32768.0f;
    env->ReleaseShortArrayElements(input, raw, JNI_ABORT);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = std::max(1, std::min(8, static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN))));
    params.print_progress = false; params.print_realtime = false; params.print_timestamps = false;
    params.single_segment = true; params.no_timestamps = true; params.no_context = true; params.language = "fa"; params.translate = false;
    if (whisper_full(context, params, samples.data(), static_cast<int>(samples.size())) != 0) return nullptr;
    std::string text;
    const int segments = whisper_full_n_segments(context);
    for (int i = 0; i < segments; ++i) text += whisper_full_get_segment_text(context, i);
    return text.empty() ? nullptr : env->NewStringUTF(text.c_str());
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_hess_meetmate_domain_WhisperNativeBindings_release(JNIEnv*, jclass, jlong handle) {
#ifdef MEETMATE_HAS_WHISPER
    if (handle) whisper_free(reinterpret_cast<whisper_context*>(handle));
#else
    (void) handle;
#endif
}
