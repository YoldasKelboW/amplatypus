// SPDX-License-Identifier: GPL-3.0-or-later
//
// Exotic Amiga formats, played by UADE (https://zakalwe.fi/uade). UADE emulates an Amiga that runs
// the format's original replay routine. The emulator is a program of its own (uadecore) that
// libuade starts as a child process and talks to over a socket, so a replay routine that crashes
// or hangs cannot take the app down.
#include "engine.h"

#include <algorithm>
#include <array>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <dirent.h>
#include <fcntl.h>
#include <mutex>
#include <strings.h>
#include <sys/stat.h>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

extern "C" {
#include <uade/uade.h>
}

namespace {

const int RATE = 48000;
// Many of these tunes loop for ever; without a length they are stopped here.
const char *const MAX_SECONDS = "600";

// Paula's output per channel is sample (-128..127) times volume (0..64).
const float CHANNEL_FULL_SCALE = 128.0f * 64.0f;
// How far the channel data may run ahead of the audio before the oldest is dropped.
const size_t MAX_CHANNEL_BACKLOG = 4096;

std::mutex setupLock;
std::string baseDir;
std::string corePath;

// What UADE's "write audio" stream says about the four Paula channels. The emulator writes it to a
// pipe: a header, then 12-byte frames. A frame either carries the current output of all four
// channels, or an event; the PET_OUTPUT event for the right channel marks the end of one output
// sample, so counting those keeps the channel data in step with the audio.
class ChannelTap {
public:
    explicit ChannelTap(int fd) : fd(fd), worker([this] { run(); }) {}

    // The emulator must have closed its end of the pipe before this runs, or it waits for ever.
    ~ChannelTap() {
        worker.join();
        close(fd);
    }

    /** Takes up to `count` samples (four channel values each) that belong to the audio just read. */
    void take(size_t count, std::vector<std::array<int16_t, 4>> &out) {
        std::lock_guard<std::mutex> guard(lock);
        if (queue.size() > count + MAX_CHANNEL_BACKLOG) {
            queue.erase(queue.begin(), queue.begin() + static_cast<long>(queue.size() - count - MAX_CHANNEL_BACKLOG));
        }
        size_t n = count < queue.size() ? count : queue.size();
        out.assign(queue.begin(), queue.begin() + static_cast<long>(n));
        queue.erase(queue.begin(), queue.begin() + static_cast<long>(n));
    }

    void clear() {
        std::lock_guard<std::mutex> guard(lock);
        queue.clear();
    }

private:
    void run() {
        static const size_t HEADER = 16;
        static const size_t FRAME = 12;
        std::vector<uint8_t> buffer;
        uint8_t chunk[16 * 1024];
        size_t skip = HEADER;
        std::array<int16_t, 4> current{};
        while (true) {
            ssize_t got = ::read(fd, chunk, sizeof(chunk));
            if (got <= 0) return;
            buffer.insert(buffer.end(), chunk, chunk + got);
            size_t at = 0;
            if (skip > 0) {
                size_t n = skip < buffer.size() ? skip : buffer.size();
                skip -= n;
                at = n;
            }
            std::vector<std::array<int16_t, 4>> samples;
            while (buffer.size() - at >= FRAME) {
                const uint8_t *f = &buffer[at];
                at += FRAME;
                if (f[0] == 0) {
                    for (int c = 0; c < 4; c++) current[c] = static_cast<int16_t>((f[4 + c * 2] << 8) | f[5 + c * 2]);
                } else if (f[5] == 8 /* PET_OUTPUT */ && f[4] == 1 /* right channel */) {
                    samples.push_back(current);
                }
            }
            buffer.erase(buffer.begin(), buffer.begin() + static_cast<long>(at));
            if (!samples.empty()) {
                std::lock_guard<std::mutex> guard(lock);
                queue.insert(queue.end(), samples.begin(), samples.end());
                // Nobody reads while playback is paused; keep the memory bounded.
                if (queue.size() > 4 * RATE) queue.erase(queue.begin(), queue.end() - RATE);
            }
        }
    }

    int fd;
    std::mutex lock;
    std::deque<std::array<int16_t, 4>> queue;
    std::thread worker;
};

class UadePlayer : public Player {
public:
    UadePlayer(uade_state *state, int tapFd) : state(state) {
        if (tapFd >= 0) tap = new ChannelTap(tapFd);
    }
    ~UadePlayer() override {
        // Stops the emulator, which closes the pipe the tap is reading.
        uade_cleanup_state(state);
        delete tap;
    }

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
        if (tap != nullptr) {
            // The emulator's channel data arrives in bursts, so a read can find none ready. Keeping
            // the previous block then stops the meters and scopes from dropping to zero for a frame.
            tap->take(samples / UADE_CHANNELS, takenBlock);
            if (!takenBlock.empty()) lastBlock.swap(takenBlock);
        }
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
        return info == nullptr ? 0.0 : static_cast<double>(info->subsongbytes) / (UADE_BYTES_PER_FRAME * RATE);
    }

    void seek(double seconds) override {
        if (failed) return;
        if (seconds < 0.0) seconds = 0.0;
        const struct uade_song_info *info = uade_get_song_info(state);
        int current = info != nullptr ? info->subsongs.cur : 0;
        uade_seek(UADE_SEEK_SUBSONG_RELATIVE, seconds, current, state);
        if (tap != nullptr) tap->clear();
    }

    int subsongCount() override {
        const struct uade_song_info *info = failed ? nullptr : uade_get_song_info(state);
        return info == nullptr ? 1 : std::max(1, info->subsongs.max - info->subsongs.min + 1);
    }

    int subsong() override {
        const struct uade_song_info *info = failed ? nullptr : uade_get_song_info(state);
        return info == nullptr ? 0 : std::max(0, info->subsongs.cur - info->subsongs.min);
    }

    bool selectSubsong(int index) override {
        const struct uade_song_info *info = failed ? nullptr : uade_get_song_info(state);
        if (info == nullptr || index < 0 || index >= subsongCount()) return false;
        if (uade_seek(UADE_SEEK_SUBSONG_RELATIVE, 0.0, info->subsongs.min + index, state) != 0) return false;
        if (tap != nullptr) tap->clear();
        return true;
    }

    void setStereoSeparation(int) override {}
    // The four Paula channels, when the channel data could be tapped. UADE fixes its mute mask when a
    // song starts, so channels cannot be muted while it plays.
    int channelCount() override { return tap != nullptr ? 4 : 0; }
    void setChannelMute(int, bool) override {}
    bool canMute() override { return false; }

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
    float vu(int channel) override {
        if (channel < 0 || channel >= 4) return 0.0f;
        int peak = 0;
        for (const auto &sample : lastBlock) peak = std::max(peak, std::abs(static_cast<int>(sample[channel])));
        return std::min(1.0f, peak / CHANNEL_FULL_SCALE);
    }

    bool ownScopes() override { return tap != nullptr; }

    void scopes(int points, float *out) override {
        size_t n = lastBlock.size();
        size_t step = n / static_cast<size_t>(points);
        if (step < 1) step = 1;
        for (int c = 0; c < 4; c++) {
            for (int i = 0; i < points; i++) {
                size_t index = static_cast<size_t>(i) * step;
                out[c * points + i] = index < n ? lastBlock[index][c] / CHANNEL_FULL_SCALE : 0.0f;
            }
        }
    }
    void playPosition(int &order, int &pattern, int &row) override { order = pattern = row = 0; }
    std::string patternText(int) override { return {}; }

private:
    uade_state *state;
    bool failed = false;
    std::vector<int16_t> scratch;
    ChannelTap *tap = nullptr;
    // Channel data that belongs to the audio of the last read() that found some.
    std::vector<std::array<int16_t, 4>> lastBlock;
    std::vector<std::array<int16_t, 4>> takenBlock;
};

// A replay routine that loads a second file (TFMX samples, Richard Joseph instruments, Sonix
// instrument folders) asks for it by full path, often with the case changed ("song.INS" for
// "song.ins", "instruments/" for "Instruments/"). UADE's own loader handles that by listing every
// folder from "/" down, and Android does not let apps list /data. A full path is therefore resolved
// here part by part: a part that exists as written is taken as it is, otherwise the folder above it
// (inside the app's own files) is searched ignoring case. Everything else (players, "ENV:" and "S:"
// files) goes to UADE's loader.
static std::string findIgnoringCase(const char *name) {
    std::string resolved;
    const char *part = name;
    while (*part == '/') part++;
    while (*part != 0) {
        const char *end = strchr(part, '/');
        std::string piece = end == nullptr ? std::string(part) : std::string(part, static_cast<size_t>(end - part));
        std::string exact = resolved + "/" + piece;
        struct stat info {};
        if (stat(exact.c_str(), &info) == 0) {
            resolved = exact;
        } else {
            DIR *listing = opendir(resolved.empty() ? "/" : resolved.c_str());
            if (listing == nullptr) return {};
            std::string match;
            while (struct dirent *entry = readdir(listing)) {
                if (strcasecmp(entry->d_name, piece.c_str()) == 0) {
                    match = resolved + "/" + entry->d_name;
                    break;
                }
            }
            closedir(listing);
            if (match.empty()) return {};
            resolved = match;
        }
        if (end == nullptr) break;
        part = end + 1;
        while (*part == '/') part++;
    }
    return resolved;
}

struct uade_file *loadByFullPath(const char *name) {
    struct uade_file *file = uade_file_load(name);
    if (file != nullptr) return file;
    std::string match = findIgnoringCase(name);
    return match.empty() ? nullptr : uade_file_load(match.c_str());
}

// Some replay routines keep file names in small buffers: Sonix (IFF-SMUS) has 128 characters for
// "<song folder>/Instruments/<name>", and the app's folders are longer than that. The song is
// therefore played through a short link next to the app's files ("<files>/m" -> the song's folder),
// which keeps every path the routine builds short. Only one song plays at a time.
std::string shortPathFor(const std::string &path, const std::string &base) {
    size_t slash = path.rfind('/');
    size_t baseSlash = base.rfind('/');
    if (slash == std::string::npos || baseSlash == std::string::npos) return path;
    std::string link = base.substr(0, baseSlash) + "/m";
    unlink(link.c_str());
    if (symlink(path.substr(0, slash).c_str(), link.c_str()) != 0) return path;
    return link + path.substr(slash);
}

struct uade_file *loadAmigaFile(const char *name, const char *playerdir, void *, struct uade_state *state) {
    if (name != nullptr && name[0] == '/') {
        struct uade_file *file = loadByFullPath(name);
        if (file != nullptr) return file;
    } else if (name != nullptr && name[0] != 0) {
        // A relative name ("Instruments/bass.instr") or the "Instruments:" volume means a file next to
        // the module. UADE would look for it relative to the emulator's working folder instead.
        const struct uade_song_info *info = uade_get_song_info(state);
        const char *song = info != nullptr ? info->modulefname : nullptr;
        const char *slash = song != nullptr ? strrchr(song, '/') : nullptr;
        if (slash != nullptr) {
            std::string dir(song, static_cast<size_t>(slash - song));
            std::string relative(name);
            if (strncasecmp(name, "Instruments:", 12) == 0) relative = "Instruments/" + relative.substr(12);
            if (relative.find(':') == std::string::npos) {
                std::string match = findIgnoringCase((dir + "/" + relative).c_str());
                if (!match.empty()) {
                    struct uade_file *file = uade_file_load(match.c_str());
                    if (file != nullptr) return file;
                }
            }
        }
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
    // The emulator writes what every Paula channel plays into this pipe ("write audio"), which
    // gives the meters and oscilloscopes. Only the write end is handed to the emulator process.
    int tapFds[2] = {-1, -1};
    if (pipe(tapFds) == 0) {
        fcntl(tapFds[0], F_SETFD, FD_CLOEXEC);
        uade_config_set_option(config, UC_WRITE_AUDIO_FD, std::to_string(tapFds[1]).c_str());
    }
    // UADE's default filter and panning give the sound of an Amiga 500 on a hi-fi.
    uade_state *state = uade_new_state(config);
    free(config);
    // The emulator has its copy of the write end now (or failed to start); this process needs none.
    if (tapFds[1] >= 0) close(tapFds[1]);
    if (state == nullptr) {
        if (tapFds[0] >= 0) close(tapFds[0]);
        return nullptr;
    }
    uade_set_amiga_loader(loadAmigaFile, nullptr, state);

    // The file is played from its path, not from memory: several replay routines load the module
    // themselves, as a program, through the emulated AmigaDOS.
    // 1 = playing, 0 = not a format UADE knows, negative = error.
    if (uade_play(shortPathFor(path, base).c_str(), -1, state) <= 0) {
        uade_cleanup_state(state);
        if (tapFds[0] >= 0) close(tapFds[0]);
        return nullptr;
    }
    return new UadePlayer(state, tapFds[0]);
}
