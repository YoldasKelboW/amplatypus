// SPDX-License-Identifier: GPL-3.0-or-later
//
// Exotic Amiga formats, played by UADE (https://zakalwe.fi/uade). UADE emulates an Amiga that runs
// the format's original replay routine. The emulator is a program of its own (uadecore) that
// libuade starts as a child process and talks to over a socket, so a replay routine that crashes
// or hangs cannot take the app down.
#include "engine.h"

#include <cstdlib>
#include <mutex>
#include <vector>

extern "C" {
#include <uade/uade.h>
}

namespace {

const int RATE = 48000;
// Many of these tunes loop for ever; without a length they are stopped here.
const char *const MAX_SECONDS = "600";

std::mutex setupLock;
std::string baseDir;
std::string corePath;

class UadePlayer : public Player {
public:
    explicit UadePlayer(uade_state *state) : state(state) {}
    ~UadePlayer() override { uade_cleanup_state(state); }

    size_t read(int, size_t frames, float *out) override {
        if (failed) return 0;
        scratch.resize(frames * UADE_CHANNELS);
        ssize_t bytes = uade_read(scratch.data(), frames * UADE_BYTES_PER_FRAME, state);
        if (bytes <= 0) {
            // After an error the state may not be used again, only freed.
            if (bytes < 0) failed = true;
            return 0;
        }
        size_t samples = static_cast<size_t>(bytes) / UADE_BYTES_PER_SAMPLE;
        for (size_t i = 0; i < samples; i++) out[i] = scratch[i] / 32768.0f;
        return samples / UADE_CHANNELS;
    }

    // Zero when UADE does not know: these formats have no length field, and the replay routine
    // only says when it is done.
    double duration() override {
        if (failed) return 0.0;
        const struct uade_song_info *info = uade_get_song_info(state);
        return info != nullptr && info->duration > 0.0 ? info->duration : 0.0;
    }

    double position() override {
        if (failed) return 0.0;
        // uade_get_time_position() only answers for RMC containers, so the position is taken
        // from the number of bytes synthesised so far.
        const struct uade_song_info *info = uade_get_song_info(state);
        return info == nullptr ? 0.0 : static_cast<double>(info->songbytes) / (UADE_BYTES_PER_FRAME * RATE);
    }

    void seek(double seconds) override {
        if (failed) return;
        if (seconds < 0.0) seconds = 0.0;
        uade_seek(UADE_SEEK_SONG_RELATIVE, seconds, 0, state);
    }

    void setStereoSeparation(int) override {}
    // Per-channel data is not wired up yet, so no meters, scopes or pattern for these formats.
    int channelCount() override { return 0; }
    void setChannelMute(int, bool) override {}

    std::string metadata(const std::string &key) override {
        if (failed) return {};
        const struct uade_song_info *info = uade_get_song_info(state);
        if (info == nullptr) return {};
        if (key == "title") return info->modulename;
        if (key == "type_long") return info->formatname[0] != 0 ? info->formatname : info->playername;
        if (key == "tracker") return info->playername;
        return {};
    }

    std::string names(int) override { return {}; }
    float vu(int) override { return 0.0f; }
    void playPosition(int &order, int &pattern, int &row) override { order = pattern = row = 0; }
    std::string patternText(int) override { return {}; }

private:
    uade_state *state;
    bool failed = false;
    std::vector<int16_t> scratch;
};

} // namespace

void setupUade(const std::string &base, const std::string &core) {
    std::lock_guard<std::mutex> guard(setupLock);
    baseDir = base;
    corePath = core;
}

Player *createUadePlayer(const std::string &path) {
    std::string base, core;
    {
        std::lock_guard<std::mutex> guard(setupLock);
        base = baseDir;
        core = corePath;
    }
    if (base.empty() || core.empty() || path.empty()) return nullptr;

    uade_config *config = uade_new_config();
    if (config == nullptr) return nullptr;
    uade_config_set_option(config, UC_BASE_DIR, base.c_str());
    uade_config_set_option(config, UC_UADECORE_FILE, core.c_str());
    uade_config_set_option(config, UC_FREQUENCY, "48000");
    uade_config_set_option(config, UC_TIMEOUT_VALUE, MAX_SECONDS);
    // UADE's default filter and panning give the sound of an Amiga 500 on a hi-fi.
    uade_state *state = uade_new_state(config);
    free(config);
    if (state == nullptr) return nullptr;

    // The file is played from its path, not from memory: several replay routines load the module
    // themselves, as a program, through the emulated AmigaDOS.
    // 1 = playing, 0 = not a format UADE knows, negative = error.
    if (uade_play(path.c_str(), -1, state) <= 0) {
        uade_cleanup_state(state);
        return nullptr;
    }
    return new UadePlayer(state);
}
