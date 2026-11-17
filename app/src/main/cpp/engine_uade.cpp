// SPDX-License-Identifier: GPL-3.0-or-later
//
// Exotic Amiga formats, played by UADE (https://zakalwe.fi/uade). UADE emulates an Amiga that runs
// the format's original replay routine. The emulator is a program of its own (uadecore) that
// libuade starts as a child process and talks to over a socket, so a replay routine that crashes
// or hangs cannot take the app down.
#include "engine.h"

#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <strings.h>
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

// A replay routine that loads a second file (TFMX samples, Richard Joseph instruments) asks for it by
// full path, often with the case changed ("song.INS" for "song.ins"). UADE's own loader handles that
// by listing every folder from "/" down, and Android does not let apps list /data. A full path is
// therefore looked up here, ignoring case only in the last part, inside the folder the app owns.
// Everything else (players, "ENV:" and "S:" files) goes to UADE's loader.
struct uade_file *loadByFullPath(const char *name) {
    struct uade_file *file = uade_file_load(name);
    if (file != nullptr) return file;
    const char *slash = strrchr(name, '/');
    if (slash == nullptr || slash[1] == 0) return nullptr;
    std::string dir(name, static_cast<size_t>(slash - name));
    DIR *listing = opendir(dir.empty() ? "/" : dir.c_str());
    if (listing == nullptr) return nullptr;
    std::string match;
    while (struct dirent *entry = readdir(listing)) {
        if (strcasecmp(entry->d_name, slash + 1) == 0) {
            match = dir + "/" + entry->d_name;
            break;
        }
    }
    closedir(listing);
    return match.empty() ? nullptr : uade_file_load(match.c_str());
}

struct uade_file *loadAmigaFile(const char *name, const char *playerdir, void *, struct uade_state *state) {
    if (name != nullptr && name[0] == '/') {
        struct uade_file *file = loadByFullPath(name);
        if (file != nullptr) return file;
    }
    return uade_load_amiga_file(name, playerdir, state);
}

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
    uade_set_amiga_loader(loadAmigaFile, nullptr, state);

    // The file is played from its path, not from memory: several replay routines load the module
    // themselves, as a program, through the emulated AmigaDOS.
    // 1 = playing, 0 = not a format UADE knows, negative = error.
    if (uade_play(path.c_str(), -1, state) <= 0) {
        uade_cleanup_state(state);
        return nullptr;
    }
    return new UadePlayer(state);
}
