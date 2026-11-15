// SPDX-License-Identifier: GPL-3.0-or-later
//
// JNI layer. A handle is a Player (or ScopeSet) pointer; which replay library is behind it is
// decided when the module is loaded.
#include <jni.h>
#include <string>
#include <vector>

#include "engine.h"

#define FN(name) Java_io_github_yoldaskelbow_amplatypus_audio_OpenMpt_##name

static Player *player(jlong handle) { return reinterpret_cast<Player *>(handle); }
static ScopeSet *scopeSet(jlong handle) { return reinterpret_cast<ScopeSet *>(handle); }

// Text comes back as UTF-8 bytes and is decoded in Kotlin; JNI's own string functions expect
// "modified UTF-8" and can choke on what old modules contain.
static jbyteArray toBytes(JNIEnv *env, const std::string &text) {
    jbyteArray result = env->NewByteArray(static_cast<jsize>(text.size()));
    if (result != nullptr && !text.empty()) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    }
    return result;
}

extern "C" {

JNIEXPORT jlong JNICALL FN(nativeLoad)(JNIEnv *env, jclass, jbyteArray data) {
    jsize length = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    if (bytes == nullptr) return 0;
    size_t size = static_cast<size_t>(length);
    Player *created = isHivelyData(bytes, size) ? createHivelyPlayer(bytes, size) : createOpenMptPlayer(bytes, size);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return reinterpret_cast<jlong>(created);
}

JNIEXPORT void JNICALL FN(nativeClose)(JNIEnv *, jclass, jlong handle) {
    delete player(handle);
}

// Renders up to `frames` interleaved stereo float frames. Returns 0 at the end of the song.
JNIEXPORT jint JNICALL FN(nativeRead)(JNIEnv *env, jclass, jlong handle, jint sampleRate, jfloatArray buffer, jint frames) {
    if (handle == 0) return 0;
    jfloat *out = env->GetFloatArrayElements(buffer, nullptr);
    if (out == nullptr) return 0;
    size_t rendered = player(handle)->read(sampleRate, static_cast<size_t>(frames), out);
    env->ReleaseFloatArrayElements(buffer, out, 0);
    return static_cast<jint>(rendered);
}

JNIEXPORT jdouble JNICALL FN(nativeDuration)(JNIEnv *, jclass, jlong handle) {
    return handle == 0 ? 0.0 : player(handle)->duration();
}

JNIEXPORT jdouble JNICALL FN(nativePosition)(JNIEnv *, jclass, jlong handle) {
    return handle == 0 ? 0.0 : player(handle)->position();
}

JNIEXPORT void JNICALL FN(nativeSeek)(JNIEnv *, jclass, jlong handle, jdouble seconds) {
    if (handle != 0) player(handle)->seek(seconds);
}

JNIEXPORT void JNICALL FN(nativeSetStereoSeparation)(JNIEnv *, jclass, jlong handle, jint percent) {
    if (handle != 0) player(handle)->setStereoSeparation(percent);
}

JNIEXPORT jint JNICALL FN(nativeChannelCount)(JNIEnv *, jclass, jlong handle) {
    return handle == 0 ? 0 : player(handle)->channelCount();
}

JNIEXPORT jbyteArray JNICALL FN(nativeMetadata)(JNIEnv *env, jclass, jlong handle, jstring key) {
    if (handle == 0) return nullptr;
    const char *k = env->GetStringUTFChars(key, nullptr);
    std::string value = player(handle)->metadata(k);
    env->ReleaseStringUTFChars(key, k);
    return toBytes(env, value);
}

// Sample names (kind 0) or instrument names (kind 1), one per line.
JNIEXPORT jbyteArray JNICALL FN(nativeNames)(JNIEnv *env, jclass, jlong handle, jint kind) {
    if (handle == 0) return nullptr;
    return toBytes(env, player(handle)->names(kind));
}

// Per-channel VU levels (0..1) of what was rendered last. Returns the number of channels written.
JNIEXPORT jint JNICALL FN(nativeVu)(JNIEnv *env, jclass, jlong handle, jfloatArray out) {
    if (handle == 0) return 0;
    Player *p = player(handle);
    jsize capacity = env->GetArrayLength(out);
    int channels = p->channelCount();
    if (channels > capacity) channels = capacity;
    jfloat levels[64];
    if (channels > 64) channels = 64;
    for (int c = 0; c < channels; c++) levels[c] = p->vu(c);
    env->SetFloatArrayRegion(out, 0, channels, levels);
    return channels;
}

// Packs order, pattern and row of the play position into one value: order << 40 | pattern << 20 | row.
JNIEXPORT jlong JNICALL FN(nativePlayPosition)(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return 0;
    int order = 0, pattern = 0, row = 0;
    player(handle)->playPosition(order, pattern, row);
    return (static_cast<jlong>(order & 0xFFFFF) << 40) | (static_cast<jlong>(pattern & 0xFFFFF) << 20) | (row & 0xFFFFF);
}

// One pattern as text: rows separated by '\n', channels by '|', each cell "C-5 01 A0F"
// (note, instrument, effect and parameter).
JNIEXPORT jbyteArray JNICALL FN(nativePattern)(JNIEnv *env, jclass, jlong handle, jint pattern) {
    if (handle == 0) return nullptr;
    return toBytes(env, player(handle)->patternText(pattern));
}

JNIEXPORT void JNICALL FN(nativeSetChannelMute)(JNIEnv *, jclass, jlong handle, jint channel, jboolean mute) {
    if (handle != 0) player(handle)->setChannelMute(channel, mute);
}

JNIEXPORT jlong JNICALL FN(nativeScopesCreate)(JNIEnv *env, jclass, jbyteArray data, jint channels) {
    jsize length = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    if (bytes == nullptr) return 0;
    size_t size = static_cast<size_t>(length);
    ScopeSet *created = isHivelyData(bytes, size) ? createHivelyScopes(bytes, size) : createOpenMptScopes(bytes, size, channels);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return reinterpret_cast<jlong>(created);
}

JNIEXPORT void JNICALL FN(nativeScopesClose)(JNIEnv *, jclass, jlong handle) {
    delete scopeSet(handle);
}

JNIEXPORT void JNICALL FN(nativeScopesSeek)(JNIEnv *, jclass, jlong handle, jdouble seconds) {
    if (handle != 0) scopeSet(handle)->seek(seconds);
}

// Advances every channel by `frames` and writes `points` samples of each channel's waveform to
// `out` (channel after channel).
JNIEXPORT void JNICALL FN(nativeScopesRender)(JNIEnv *env, jclass, jlong handle, jint sampleRate, jint frames, jfloatArray out, jint points) {
    if (handle == 0 || frames <= 0 || points <= 0) return;
    ScopeSet *scopes = scopeSet(handle);
    std::vector<float> wave(scopes->channels() * static_cast<size_t>(points), 0.0f);
    scopes->render(sampleRate, frames, wave.data(), points);
    jsize capacity = env->GetArrayLength(out);
    jsize count = static_cast<jsize>(wave.size());
    env->SetFloatArrayRegion(out, 0, count < capacity ? count : capacity, wave.data());
}

} // extern "C"
