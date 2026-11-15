// SPDX-License-Identifier: GPL-3.0-or-later
//
// Thin JNI layer over libopenmpt's C API. The call sequence follows MPTReplayer.m of
// 4champ (Copyright (c) Aleksi Sitomaniemi, used under the MIT license).
#include <jni.h>
#include <libopenmpt/libopenmpt.h>
#include <libopenmpt/libopenmpt_ext.h>
#include <string>
#include <vector>

#define FN(name) Java_io_github_yoldaskelbow_amplatypus_audio_OpenMpt_##name

// A handle is an openmpt_module_ext, which is needed for muting single channels; the plain module
// used by all other calls lives inside it.
static openmpt_module_ext *ext(jlong handle) { return reinterpret_cast<openmpt_module_ext *>(handle); }
static openmpt_module *mod(jlong handle) { return openmpt_module_ext_get_module(ext(handle)); }

extern "C" {

JNIEXPORT jlong JNICALL FN(nativeLoad)(JNIEnv *env, jclass, jbyteArray data) {
    jsize length = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    if (bytes == nullptr) return 0;
    openmpt_module_ext *module = openmpt_module_ext_create_from_memory(
            bytes, static_cast<size_t>(length), nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return reinterpret_cast<jlong>(module);
}

JNIEXPORT void JNICALL FN(nativeClose)(JNIEnv *, jclass, jlong handle) {
    if (handle != 0) openmpt_module_ext_destroy(ext(handle));
}

// Renders up to `frames` interleaved stereo float frames. Returns 0 at the end of the song.
JNIEXPORT jint JNICALL FN(nativeRead)(JNIEnv *env, jclass, jlong handle, jint sampleRate, jfloatArray buffer, jint frames) {
    if (handle == 0) return 0;
    jfloat *out = env->GetFloatArrayElements(buffer, nullptr);
    if (out == nullptr) return 0;
    size_t rendered = openmpt_module_read_interleaved_float_stereo(mod(handle), sampleRate, static_cast<size_t>(frames), out);
    env->ReleaseFloatArrayElements(buffer, out, 0);
    return static_cast<jint>(rendered);
}

JNIEXPORT jdouble JNICALL FN(nativeDuration)(JNIEnv *, jclass, jlong handle) {
    return handle == 0 ? 0.0 : openmpt_module_get_duration_seconds(mod(handle));
}

JNIEXPORT jdouble JNICALL FN(nativePosition)(JNIEnv *, jclass, jlong handle) {
    return handle == 0 ? 0.0 : openmpt_module_get_position_seconds(mod(handle));
}

JNIEXPORT void JNICALL FN(nativeSeek)(JNIEnv *, jclass, jlong handle, jdouble seconds) {
    if (handle == 0) return;
    if (seconds <= 0.0) {
        openmpt_module_set_position_order_row(mod(handle), 0, 0);
    } else {
        openmpt_module_set_position_seconds(mod(handle), seconds);
    }
}

JNIEXPORT void JNICALL FN(nativeSetStereoSeparation)(JNIEnv *, jclass, jlong handle, jint percent) {
    // App range is 0-100, libopenmpt's is 0-200.
    if (handle != 0) openmpt_module_set_render_param(mod(handle), OPENMPT_MODULE_RENDER_STEREOSEPARATION_PERCENT, percent * 2);
}

JNIEXPORT jint JNICALL FN(nativeChannelCount)(JNIEnv *, jclass, jlong handle) {
    return handle == 0 ? 0 : openmpt_module_get_num_channels(mod(handle));
}

// Text comes back as UTF-8 bytes and is decoded in Kotlin; JNI's own string functions expect
// "modified UTF-8" and can choke on what old modules contain.
static jbyteArray toBytes(JNIEnv *env, const std::string &text) {
    jbyteArray result = env->NewByteArray(static_cast<jsize>(text.size()));
    if (result != nullptr && !text.empty()) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    }
    return result;
}

static std::string take(const char *value) {
    if (value == nullptr) return {};
    std::string text(value);
    openmpt_free_string(value);
    return text;
}

JNIEXPORT jbyteArray JNICALL FN(nativeMetadata)(JNIEnv *env, jclass, jlong handle, jstring key) {
    if (handle == 0) return nullptr;
    const char *k = env->GetStringUTFChars(key, nullptr);
    std::string value = take(openmpt_module_get_metadata(mod(handle), k));
    env->ReleaseStringUTFChars(key, k);
    return toBytes(env, value);
}

// Sample names (kind 0) or instrument names (kind 1), one per line.
JNIEXPORT jbyteArray JNICALL FN(nativeNames)(JNIEnv *env, jclass, jlong handle, jint kind) {
    if (handle == 0) return nullptr;
    openmpt_module *m = mod(handle);
    std::string all;
    int32_t count = kind == 0 ? openmpt_module_get_num_samples(m) : openmpt_module_get_num_instruments(m);
    for (int32_t i = 0; i < count; i++) {
        std::string name = take(kind == 0 ? openmpt_module_get_sample_name(m, i) : openmpt_module_get_instrument_name(m, i));
        for (char &c : name) if (c == '\n' || c == '\r') c = ' ';
        all += name;
        all += '\n';
    }
    return toBytes(env, all);
}

// Per-channel VU levels (0..1) of what was rendered last. Returns the number of channels written.
JNIEXPORT jint JNICALL FN(nativeVu)(JNIEnv *env, jclass, jlong handle, jfloatArray out) {
    if (handle == 0) return 0;
    openmpt_module *m = mod(handle);
    jsize capacity = env->GetArrayLength(out);
    int32_t channels = openmpt_module_get_num_channels(m);
    if (channels > capacity) channels = capacity;
    jfloat levels[64];
    if (channels > 64) channels = 64;
    for (int32_t c = 0; c < channels; c++) levels[c] = openmpt_module_get_current_channel_vu_mono(m, c);
    env->SetFloatArrayRegion(out, 0, channels, levels);
    return channels;
}

// Packs order, pattern and row of the play position into one value: order << 40 | pattern << 20 | row.
JNIEXPORT jlong JNICALL FN(nativePlayPosition)(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return 0;
    openmpt_module *m = mod(handle);
    jlong order = openmpt_module_get_current_order(m) & 0xFFFFF;
    jlong pattern = openmpt_module_get_current_pattern(m) & 0xFFFFF;
    jlong row = openmpt_module_get_current_row(m) & 0xFFFFF;
    return (order << 40) | (pattern << 20) | row;
}

// One pattern as text: rows separated by '\n', channels by '|', each cell "C-5 01 A0F"
// (note, instrument, effect and parameter).
JNIEXPORT jbyteArray JNICALL FN(nativePattern)(JNIEnv *env, jclass, jlong handle, jint pattern) {
    if (handle == 0) return nullptr;
    openmpt_module *m = mod(handle);
    int32_t rows = openmpt_module_get_pattern_num_rows(m, pattern);
    int32_t channels = openmpt_module_get_num_channels(m);
    std::string text;
    text.reserve(static_cast<size_t>(rows) * channels * 11);
    for (int32_t r = 0; r < rows; r++) {
        for (int32_t c = 0; c < channels; c++) {
            if (c > 0) text += '|';
            text += take(openmpt_module_format_pattern_row_channel_command(m, pattern, r, c, OPENMPT_MODULE_COMMAND_NOTE));
            text += ' ';
            text += take(openmpt_module_format_pattern_row_channel_command(m, pattern, r, c, OPENMPT_MODULE_COMMAND_INSTRUMENT));
            text += ' ';
            text += take(openmpt_module_format_pattern_row_channel_command(m, pattern, r, c, OPENMPT_MODULE_COMMAND_EFFECT));
            text += take(openmpt_module_format_pattern_row_channel_command(m, pattern, r, c, OPENMPT_MODULE_COMMAND_PARAMETER));
        }
        text += '\n';
    }
    return toBytes(env, text);
}

JNIEXPORT void JNICALL FN(nativeSetChannelMute)(JNIEnv *, jclass, jlong handle, jint channel, jboolean mute) {
    if (handle == 0) return;
    openmpt_module_ext_interface_interactive interactive;
    if (openmpt_module_ext_get_interface(ext(handle), LIBOPENMPT_EXT_C_INTERFACE_INTERACTIVE, &interactive, sizeof(interactive)) != 0) {
        // The channel's volume is set to zero instead of using set_channel_mute_status: a muted channel is
        // not rendered at all, so its note stops moving and resumes out of time when it is switched back
        // on. At zero volume it keeps playing inaudibly and comes back exactly in step.
        interactive.set_channel_volume(ext(handle), channel, mute ? 0.0 : 1.0);
    }
}

// ---- Per-channel oscilloscopes -------------------------------------------------------------
// libopenmpt cannot hand out the audio of a single channel. For the scopes the module is therefore
// loaded once more per channel with every other channel muted, and these copies are rendered in
// step with the audible one.
struct Scopes {
    std::vector<openmpt_module_ext *> modules;
    std::vector<float> scratch;
};

static void destroyScopes(Scopes *scopes) {
    if (scopes == nullptr) return;
    for (openmpt_module_ext *ext : scopes->modules) openmpt_module_ext_destroy(ext);
    delete scopes;
}

JNIEXPORT jlong JNICALL FN(nativeScopesCreate)(JNIEnv *env, jclass, jbyteArray data, jint channels) {
    jsize length = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    if (bytes == nullptr) return 0;
    auto *scopes = new Scopes();
    bool ok = true;
    for (jint c = 0; c < channels && ok; c++) {
        openmpt_module_ext *ext = openmpt_module_ext_create_from_memory(
                bytes, static_cast<size_t>(length), nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr);
        if (ext == nullptr) { ok = false; break; }
        scopes->modules.push_back(ext);
        openmpt_module_ext_interface_interactive interactive;
        if (openmpt_module_ext_get_interface(ext, LIBOPENMPT_EXT_C_INTERFACE_INTERACTIVE, &interactive, sizeof(interactive)) == 0) {
            ok = false;
            break;
        }
        for (jint other = 0; other < channels; other++) interactive.set_channel_mute_status(ext, other, other != c);
        // The cheapest settings are enough for a picture.
        openmpt_module *m = openmpt_module_ext_get_module(ext);
        openmpt_module_set_render_param(m, OPENMPT_MODULE_RENDER_INTERPOLATIONFILTER_LENGTH, 1);
        openmpt_module_set_render_param(m, OPENMPT_MODULE_RENDER_VOLUMERAMPING_STRENGTH, 0);
    }
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    if (!ok) {
        destroyScopes(scopes);
        return 0;
    }
    return reinterpret_cast<jlong>(scopes);
}

JNIEXPORT void JNICALL FN(nativeScopesClose)(JNIEnv *, jclass, jlong handle) {
    destroyScopes(reinterpret_cast<Scopes *>(handle));
}

JNIEXPORT void JNICALL FN(nativeScopesSeek)(JNIEnv *, jclass, jlong handle, jdouble seconds) {
    auto *scopes = reinterpret_cast<Scopes *>(handle);
    if (scopes == nullptr) return;
    for (openmpt_module_ext *ext : scopes->modules) {
        openmpt_module *m = openmpt_module_ext_get_module(ext);
        if (seconds <= 0.0) {
            openmpt_module_set_position_order_row(m, 0, 0);
        } else {
            openmpt_module_set_position_seconds(m, seconds);
        }
    }
}

// Advances every copy by `frames` and writes `points` samples of each channel's waveform to `out`
// (channel after channel).
JNIEXPORT void JNICALL FN(nativeScopesRender)(JNIEnv *env, jclass, jlong handle, jint sampleRate, jint frames, jfloatArray out, jint points) {
    auto *scopes = reinterpret_cast<Scopes *>(handle);
    if (scopes == nullptr || frames <= 0 || points <= 0) return;
    scopes->scratch.resize(static_cast<size_t>(frames));
    std::vector<float> wave(scopes->modules.size() * static_cast<size_t>(points), 0.0f);
    int step = frames / points;
    if (step < 1) step = 1;
    for (size_t c = 0; c < scopes->modules.size(); c++) {
        openmpt_module *m = openmpt_module_ext_get_module(scopes->modules[c]);
        size_t rendered = openmpt_module_read_float_mono(m, sampleRate, static_cast<size_t>(frames), scopes->scratch.data());
        for (int i = 0; i < points; i++) {
            size_t index = static_cast<size_t>(i) * step;
            if (index < rendered) wave[c * points + i] = scopes->scratch[index];
        }
    }
    jsize capacity = env->GetArrayLength(out);
    jsize count = static_cast<jsize>(wave.size());
    env->SetFloatArrayRegion(out, 0, count < capacity ? count : capacity, wave.data());
}

} // extern "C"
