// SPDX-License-Identifier: GPL-3.0-or-later
#include "core_api.h"
#include "engine.h"

#include <algorithm>
#include <cstring>
#include <string>

#include <libopenmpt/libopenmpt.h>

namespace {
Player *player(void *handle) { return static_cast<Player *>(handle); }
} // namespace

AMP_API void *amp_load(const void *data, int length, const char *name) {
    if (data == nullptr || length <= 0) return nullptr;
    std::string path = name != nullptr ? name : "";
    auto size = static_cast<size_t>(length);
    Player *created = nullptr;
    // The same order as the Android app: console music by header or extension, then HivelyTracker.
    if (isGmeData(data, size, path)) created = createGmePlayer(data, size, path);
    if (created == nullptr && isHivelyData(data, size)) created = createHivelyPlayer(data, size);
    // libopenmpt last: it takes many formats, some of which share extensions with the others.
    if (created == nullptr) created = createOpenMptPlayer(data, size);
    return created;
}

AMP_API void amp_free(void *handle) { delete player(handle); }

AMP_API int amp_read(void *handle, int sample_rate, int frames, float *out) {
    if (handle == nullptr || frames <= 0) return 0;
    return static_cast<int>(player(handle)->read(sample_rate, static_cast<size_t>(frames), out));
}

AMP_API double amp_duration(void *handle) { return handle ? player(handle)->duration() : 0.0; }
AMP_API double amp_position(void *handle) { return handle ? player(handle)->position() : 0.0; }
AMP_API void amp_seek(void *handle, double seconds) { if (handle) player(handle)->seek(seconds); }
AMP_API int amp_channels(void *handle) { return handle ? player(handle)->channelCount() : 0; }
AMP_API void amp_mute(void *handle, int channel, int mute) { if (handle) player(handle)->setChannelMute(channel, mute != 0); }
AMP_API float amp_level(void *handle, int channel) { return handle ? player(handle)->vu(channel) : 0.0f; }
AMP_API int amp_subsongs(void *handle) { return handle ? player(handle)->subsongCount() : 0; }
AMP_API int amp_select_subsong(void *handle, int index) { return handle && player(handle)->selectSubsong(index) ? 1 : 0; }

AMP_API int amp_metadata(void *handle, const char *key, char *buffer, int size) {
    if (handle == nullptr || key == nullptr) return 0;
    std::string value = player(handle)->metadata(key);
    if (buffer != nullptr && size > 0) {
        size_t copied = std::min(value.size(), static_cast<size_t>(size - 1));
        std::memcpy(buffer, value.data(), copied);
        buffer[copied] = '\0';
    }
    return static_cast<int>(value.size());
}

AMP_API void amp_play_position(void *handle, int *order, int *pattern, int *row) {
    int o = 0, p = 0, r = 0;
    if (handle) player(handle)->playPosition(o, p, r);
    if (order) *order = o;
    if (pattern) *pattern = p;
    if (row) *row = r;
}

AMP_API int amp_order_count(void *handle) { return handle ? player(handle)->orderCount() : 0; }

AMP_API void amp_tempo(void *handle, int *speed, int *bpm) {
    int s = 0, b = 0;
    if (handle) player(handle)->tempo(s, b);
    if (speed) *speed = s;
    if (bpm) *bpm = b;
}

AMP_API int amp_pattern(void *handle, int pattern, char *buffer, int size) {
    if (handle == nullptr) return 0;
    std::string text = player(handle)->patternText(pattern);
    if (buffer != nullptr && size > 0) {
        size_t copied = std::min(text.size(), static_cast<size_t>(size - 1));
        std::memcpy(buffer, text.data(), copied);
        buffer[copied] = '\0';
    }
    return static_cast<int>(text.size());
}

AMP_API int amp_own_scopes(void *handle) { return handle && player(handle)->ownScopes() ? 1 : 0; }

AMP_API void amp_player_scopes(void *handle, int points, float *out) {
    if (handle && out && points > 0) player(handle)->scopes(points, out);
}

AMP_API void *amp_scopes_create(const void *data, int length, int channels) {
    if (data == nullptr || length <= 0) return nullptr;
    auto size = static_cast<size_t>(length);
    if (isHivelyData(data, size)) return createHivelyScopes(data, size);
    return createOpenMptScopes(data, size, channels);
}

AMP_API void amp_scopes_free(void *scopes) { delete static_cast<ScopeSet *>(scopes); }
AMP_API int amp_scopes_channels(void *scopes) { return scopes ? static_cast<int>(static_cast<ScopeSet *>(scopes)->channels()) : 0; }
AMP_API void amp_scopes_seek(void *scopes, double seconds) { if (scopes) static_cast<ScopeSet *>(scopes)->seek(seconds); }

AMP_API void amp_scopes_render(void *scopes, int sample_rate, int frames, float *out, int points) {
    if (scopes && out && frames > 0 && points > 0) static_cast<ScopeSet *>(scopes)->render(sample_rate, frames, out, points);
}

AMP_API int amp_can_mute(void *handle) { return handle && player(handle)->canMute() ? 1 : 0; }

AMP_API const char *amp_extensions(void) {
    return "nsf;nsfe;spc;gbs;vgm;vgz;gym;hes;kss;sap;ay;emul;ahx;hvl";
}

AMP_API const char *amp_tracker_extensions(void) {
    static const std::string list = [] {
        std::string text;
        const char *openmpt = openmpt_get_supported_extensions();
        if (openmpt != nullptr) {
            text = openmpt;
            openmpt_free_string(openmpt);
        }
        return text;
    }();
    return list.c_str();
}
