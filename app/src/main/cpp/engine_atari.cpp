// SPDX-License-Identifier: GPL-3.0-or-later
//
// Atari ST music. SNDH (and SC68) files hold the original 68000 replay code; sc68 runs it in an
// emulated Atari ST with its YM-2149 sound chip and MFP timers. YM files are register dumps of the
// YM-2149, played by StSound.
//
// Neither library hands out the three voices on their own, so level and waveform are drawn from what
// the music writes to the chip's registers, as for the C64. Neither can mute a voice: sc68 only can
// with its older YM engine, and the better one (band-limited steps by Antti Lankila) is kept.
// SC68 files with Amiga music (Paula) show no channels.
#include "engine.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include <StSoundLibrary.h>
#include <sc68/sc68.h>

extern "C" {
#include <io68/ym_io.h>
}

namespace {

constexpr int VOICES = 3;
constexpr double ST_CLOCK = 2000000.0;
// SNDH files often say nothing about their length: they end after this long, or earlier once silent.
constexpr int DEFAULT_LENGTH_MS = 180000;
constexpr double SILENCE_SECONDS = 4.0;
constexpr double NEVER_LOUD_SECONDS = 3.0;

std::string lower(std::string text) {
    std::transform(text.begin(), text.end(), text.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return text;
}

std::string extensionOf(const std::string &path) {
    size_t slash = path.find_last_of('/');
    std::string name = slash == std::string::npos ? path : path.substr(slash + 1);
    size_t dot = name.rfind('.');
    return dot == std::string::npos ? std::string() : lower(name.substr(dot + 1));
}

void join(std::string &text, const std::string &line) {
    if (line.empty()) return;
    if (!text.empty()) text += "\n";
    text += line;
}

// What the app shows of the YM-2149's three voices, worked out from its 16 registers.
class ChipView {
public:
    void update(const uint8_t *r, double step, const bool *muted) {
        for (int v = 0; v < VOICES; v++) {
            bool tone = (r[7] & (1 << v)) == 0;
            bool noise = (r[7] & (8 << v)) == 0;
            int volume = r[8 + v] & 0x1f;
            // Bit 4 hands the volume to the envelope generator: show it as fairly loud.
            double target = (tone || noise) ? ((volume & 0x10) ? 0.8 : (volume & 0x0f) / 15.0) : 0.0;
            if (muted != nullptr && muted[v]) target = 0.0;
            level[v] = target >= level[v] ? target : std::max(target, level[v] - step * 3.0);
            int period = r[v * 2] | ((r[v * 2 + 1] & 0x0f) << 8);
            frequency[v] = tone && period > 0 ? ST_CLOCK / (16.0 * period) : 0.0;
            noisy[v] = noise && !tone;
        }
    }

    float vu(int v) const { return v >= 0 && v < VOICES ? static_cast<float>(level[v]) : 0.0f; }

    // A square wave at the voice's pitch (or noise) as tall as its volume, about 20 ms of it.
    void scopes(int points, float *out) {
        for (int v = 0; v < VOICES; v++) {
            double cycles = std::min(frequency[v] * 0.02, 24.0);
            for (int i = 0; i < points; i++) {
                double sample;
                if (noisy[v]) {
                    seed = seed * 1103515245u + 12345u;
                    sample = static_cast<double>((seed >> 16) & 0x7fff) / 16384.0 - 1.0;
                } else if (cycles > 0.0) {
                    double t = phase[v] + cycles * i / points;
                    sample = t - std::floor(t) < 0.5 ? 1.0 : -1.0;
                } else {
                    sample = 0.0;
                }
                out[v * points + i] = static_cast<float>(sample * level[v] * 0.3);
            }
            phase[v] += cycles * 0.37;
            phase[v] -= std::floor(phase[v]);
        }
    }

    void reset() { *this = ChipView(); }

private:
    double level[VOICES] = {};
    double frequency[VOICES] = {};
    double phase[VOICES] = {};
    bool noisy[VOICES] = {};
    uint32_t seed = 12345u;
};

// Ends a track that has fallen silent, or that never makes a sound.
struct SilenceWatch {
    double played = 0.0, silentFor = 0.0;
    bool everLoud = false;

    bool over(const short *samples, size_t count, double step) {
        bool loud = std::any_of(samples, samples + count, [](short s) { return std::abs(s) > 64; });
        played += step;
        everLoud = everLoud || loud;
        silentFor = loud ? 0.0 : silentFor + step;
        return (everLoud && silentFor >= SILENCE_SECONDS && played > 3.0) || (!everLoud && played >= NEVER_LOUD_SECONDS);
    }

    void reset() { *this = SilenceWatch(); }
};

// ------------------------------------------------------------------ YM files (StSound)

class YmPlayer : public Player {
public:
    YmPlayer(const void *data, size_t length)
        : file(static_cast<const char *>(data), static_cast<const char *>(data) + length) {
        ready = open(48000);
    }

    ~YmPlayer() override {
        if (music != nullptr) ymMusicDestroy(music);
    }

    bool ok() const { return ready; }

    size_t read(int sampleRate, size_t frames, float *out) override {
        if (!ready) return 0;
        if (sampleRate != rate) {
            ymu32 at = ymMusicGetPos(music);
            if (!open(sampleRate)) return 0;
            if (ymMusicIsSeekable(music)) ymMusicSeek(music, at);
        }
        if (ended || ymMusicIsOver(music)) return 0;
        buffer.resize(frames);
        if (!ymMusicCompute(music, buffer.data(), static_cast<ymint>(frames))) return 0;
        for (size_t i = 0; i < frames; i++) out[i * 2] = out[i * 2 + 1] = buffer[i] / 32768.0f;
        double step = static_cast<double>(frames) / rate;
        uint8_t regs[16];
        for (int r = 0; r < 16; r++) regs[r] = static_cast<uint8_t>(ymMusicGetRegister(music, r));
        chip.update(regs, step, nullptr);
        if (silence.over(buffer.data(), frames, step)) ended = true;
        return frames;
    }

    double duration() override { return lengthMs / 1000.0; }
    double position() override { return ready ? ymMusicGetPos(music) / 1000.0 : 0.0; }

    void seek(double seconds) override {
        if (!ready || !ymMusicIsSeekable(music)) return;
        ymMusicSeek(music, static_cast<ymu32>(std::max(0.0, seconds) * 1000.0));
        silence.reset();
        ended = false;
    }

    void setStereoSeparation(int) override {}
    int channelCount() override { return ready ? VOICES : 0; }
    void setChannelMute(int, bool) override {}
    bool canMute() override { return false; }

    std::string metadata(const std::string &key) override {
        if (!ready) return {};
        ymMusicInfo_t info;
        ymMusicGetInfo(music, &info);
        auto text = [](const ymchar *s) { return s != nullptr ? std::string(s) : std::string(); };
        if (key == "title") return text(info.pSongName);
        if (key == "artist") return text(info.pSongAuthor);
        if (key == "type_long") return "Atari ST (YM)";
        if (key == "tracker") return text(info.pSongPlayer);
        if (key == "message_raw") {
            std::string message;
            join(message, text(info.pSongAuthor).empty() ? "" : "Author: " + text(info.pSongAuthor));
            join(message, text(info.pSongType).empty() ? "" : "Format: " + text(info.pSongType));
            join(message, text(info.pSongComment));
            return message;
        }
        return {};
    }

    std::string names(int) override { return {}; }
    float vu(int channel) override { return chip.vu(channel); }
    void playPosition(int &order, int &pattern, int &row) override { order = pattern = row = 0; }
    std::string patternText(int) override { return {}; }
    bool ownScopes() override { return ready; }
    void scopes(int points, float *out) override { chip.scopes(points, out); }

private:
    bool open(int sampleRate) {
        if (music != nullptr) ymMusicDestroy(music);
        music = ymMusicCreateWithRate(sampleRate);
        if (music == nullptr) return false;
        if (!ymMusicLoadMemory(music, file.data(), static_cast<ymu32>(file.size()))) {
            ymMusicDestroy(music);
            music = nullptr;
            return false;
        }
        ymMusicSetLoopMode(music, YMFALSE);
        ymMusicPlay(music);
        ymMusicInfo_t info;
        ymMusicGetInfo(music, &info);
        lengthMs = info.musicTimeInMs > 0 ? info.musicTimeInMs : DEFAULT_LENGTH_MS;
        rate = sampleRate;
        ended = false;
        silence.reset();
        chip.reset();
        return true;
    }

    std::vector<char> file;
    YMMUSIC *music = nullptr;
    bool ready = false;
    bool ended = false;
    int rate = 0;
    int lengthMs = DEFAULT_LENGTH_MS;
    std::vector<ymsample> buffer;
    ChipView chip;
    SilenceWatch silence;
};

// ------------------------------------------------------------------ SNDH and SC68 (sc68)

// sc68 keeps global state: it is set up once, and only one file plays at a time.
std::mutex sc68Lock;
bool sc68Ready = false;

bool initSc68() {
    if (sc68Ready) return true;
    sc68_init_t init;
    std::memset(&init, 0, sizeof(init));
    init.flags.no_load_config = 1;
    init.flags.no_save_config = 1;
    sc68Ready = sc68_init(&init) == 0;
    return sc68Ready;
}

class SndhPlayer : public Player {
public:
    SndhPlayer(const void *data, size_t length)
        : file(static_cast<const char *>(data), static_cast<const char *>(data) + length) {
        std::lock_guard<std::mutex> guard(sc68Lock);
        ready = initSc68() && open(48000) && start(1);
    }

    ~SndhPlayer() override {
        std::lock_guard<std::mutex> guard(sc68Lock);
        if (sc68 != nullptr) {
            sc68_close(sc68);
            sc68_destroy(sc68);
        }
    }

    bool ok() const { return ready; }

    size_t read(int sampleRate, size_t frames, float *out) override {
        std::lock_guard<std::mutex> guard(sc68Lock);
        if (!ready || ended) return 0;
        if (sampleRate != rate) {
            if (!open(sampleRate) || !start(track)) return 0;
        }
        return render(frames, out);
    }

    double duration() override { return lengthMs / 1000.0; }
    double position() override { return seconds; }

    void seek(double target) override {
        std::lock_guard<std::mutex> guard(sc68Lock);
        if (!ready) return;
        // The emulation cannot jump: it starts the track again and runs it silently up to the target.
        if (!start(track)) return;
        std::vector<float> scratch(4096 * 2);
        target = std::min(std::max(target, 0.0), lengthMs / 1000.0);
        while (seconds + 0.01 < target && !ended) {
            size_t frames = std::min<size_t>(4096, static_cast<size_t>((target - seconds) * rate) + 1);
            if (render(frames, scratch.data()) == 0) break;
        }
    }

    void setStereoSeparation(int) override {}
    int channelCount() override { return ready && !amiga ? VOICES : 0; }

    void setChannelMute(int, bool) override {}
    bool canMute() override { return false; }

    std::string metadata(const std::string &key) override {
        std::lock_guard<std::mutex> guard(sc68Lock);
        if (!ready) return {};
        sc68_music_info_t info;
        if (sc68_music_info(sc68, &info, track, nullptr) != 0) return {};
        auto text = [](const char *s) { return s != nullptr ? std::string(s) : std::string(); };
        std::string title = text(info.title), album = text(info.album);
        if (key == "title") return !title.empty() ? title : album;
        if (key == "artist") return text(info.artist);
        if (key == "type_long") return amiga ? "Amiga (SC68)" : sndh ? "Atari ST (SNDH)" : "Atari ST (SC68)";
        if (key == "tracker") return text(info.year);
        if (key == "message_raw") {
            std::string message;
            join(message, album.empty() || album == title ? "" : "Album: " + album);
            join(message, text(info.artist).empty() ? "" : "Author: " + text(info.artist));
            join(message, text(info.year).empty() ? "" : "Year: " + text(info.year));
            if (subsongCount() > 1) join(message, "Song " + std::to_string(track) + " of " + std::to_string(subsongCount()));
            return message;
        }
        return {};
    }

    std::string names(int) override { return {}; }
    float vu(int channel) override { return chip.vu(channel); }
    void playPosition(int &order, int &pattern, int &row) override { order = pattern = row = 0; }
    std::string patternText(int) override { return {}; }
    bool ownScopes() override { return ready; }
    void scopes(int points, float *out) override { chip.scopes(points, out); }

    int subsongCount() override { return tracks; }
    int subsong() override { return track - 1; }

    bool selectSubsong(int index) override {
        std::lock_guard<std::mutex> guard(sc68Lock);
        if (!ready || index < 0 || index >= tracks) return false;
        return start(index + 1);
    }

private:
    bool open(int sampleRate) {
        if (sc68 != nullptr) {
            sc68_close(sc68);
            sc68_destroy(sc68);
        }
        sc68_create_t create;
        std::memset(&create, 0, sizeof(create));
        create.sampling_rate = static_cast<unsigned>(sampleRate);
        create.name = "amplatypus";
        // 4 MB, as much as an STE or Falcon could have: big SNDH files need more than the default 1 MB.
        create.log2mem = 22;
        sc68 = sc68_create(&create);
        if (sc68 == nullptr) return false;
        if (sc68_load_mem(sc68, file.data(), static_cast<int>(file.size())) != 0) return false;
        tracks = std::max(1, sc68_cntl(sc68, SC68_GET_TRACKS));
        rate = sampleRate;
        // The YM emulator, for its registers.
        void **emulators = nullptr;
        ym = nullptr;
        if (sc68_cntl(sc68, SC68_EMULATORS, &emulators) == 0 && emulators != nullptr && emulators[1] != nullptr) {
            ym = ymio_emulator(static_cast<io68_t *>(emulators[1]));
        }
        return true;
    }

    // Tracks are counted from 1, as sc68 does.
    bool start(int number) {
        if (sc68_play(sc68, number, SC68_DEF_LOOP) != 0) return false;
        track = number;
        // Amiga music in an SC68 file plays on the emulated Paula chip, not the YM.
        sc68_music_info_t info;
        amiga = sc68_music_info(sc68, &info, number, nullptr) == 0 && info.trk.amiga && !info.trk.ym;
        seconds = 0.0;
        ended = false;
        started = false;
        silence.reset();
        chip.reset();
        lengthMs = DEFAULT_LENGTH_MS;
        return true;
    }

    size_t render(size_t frames, float *out) {
        buffer.resize(frames * 2);
        int n = 0, code = 0;
        // The first call only sets the track up and delivers nothing.
        for (int tries = 0; tries < 3 && n == 0; tries++) {
            n = static_cast<int>(frames);
            code = sc68_process(sc68, buffer.data(), &n);
            if (code == SC68_ERROR || (code & SC68_END)) break;
        }
        if (code == SC68_ERROR) {
            ended = true;
            return 0;
        }
        if (!started) {
            // The track's length and its YM chip are known once it runs.
            started = true;
            int length = sc68_cntl(sc68, SC68_GET_LEN);
            if (length > 0) lengthMs = length;

        }
        if (code & SC68_END) ended = true;
        if (n <= 0) return 0;
        size_t produced = static_cast<size_t>(n);
        for (size_t i = 0; i < produced; i++) {
            out[i * 2] = buffer[i * 2] / 32768.0f;
            out[i * 2 + 1] = buffer[i * 2 + 1] / 32768.0f;
        }
        double step = static_cast<double>(produced) / rate;
        seconds += step;
        if (ym != nullptr && !amiga) chip.update(ym->shadow.index, step, nullptr);
        if (silence.over(buffer.data(), produced * 2, step) || seconds * 1000.0 >= lengthMs) ended = true;
        return produced;
    }

    std::vector<char> file;
    bool sndh = true;
    sc68_t *sc68 = nullptr;
    ym_t *ym = nullptr;
    bool ready = false;
    bool ended = false;
    bool started = false;
    int rate = 0;
    int tracks = 1;
    int track = 1;
    int lengthMs = DEFAULT_LENGTH_MS;
    double seconds = 0.0;
    bool amiga = false;
    std::vector<short> buffer;
    ChipView chip;
    SilenceWatch silence;

public:
    void setSc68File(bool sc68File) { sndh = !sc68File; }
};

bool isSndh(const char *bytes, size_t length) {
    // Plain SNDH files carry "SNDH" near the start; most are ICE-packed ("ICE!").
    if (length >= 4 && (std::memcmp(bytes, "ICE!", 4) == 0 || std::memcmp(bytes, "Ice!", 4) == 0)) return true;
    for (size_t i = 0; i + 4 <= std::min<size_t>(length, 64); i++) {
        if (std::memcmp(bytes + i, "SNDH", 4) == 0) return true;
    }
    return false;
}

bool isSc68(const char *bytes, size_t length) {
    static const char MAGIC[] = "SC68 Music-file / (c) (BeN)jamin Gerard / SasHipA-Dev  ";
    return length >= sizeof(MAGIC) - 1 && std::memcmp(bytes, MAGIC, sizeof(MAGIC) - 1) == 0;
}

bool isYm(const char *bytes, size_t length, const std::string &path) {
    if (length >= 4 && std::memcmp(bytes, "YM", 2) == 0 && bytes[3] == '!') return true;
    // Most YM files are LHA archives ("-lh5-" at byte 2) with the dump inside.
    return extensionOf(path) == "ym" && length >= 7 && std::memcmp(bytes + 2, "-lh", 3) == 0;
}

} // namespace

bool isAtariData(const void *data, size_t length, const std::string &path) {
    const auto *bytes = static_cast<const char *>(data);
    std::string ext = extensionOf(path);
    return isYm(bytes, length, path) || isSc68(bytes, length) || ((ext == "sndh" || ext == "snd") && isSndh(bytes, length));
}

Player *createAtariPlayer(const void *data, size_t length, const std::string &path) {
    const auto *bytes = static_cast<const char *>(data);
    if (isYm(bytes, length, path)) {
        auto player = std::make_unique<YmPlayer>(data, length);
        return player->ok() ? player.release() : nullptr;
    }
    auto player = std::make_unique<SndhPlayer>(data, length);
    player->setSc68File(isSc68(bytes, length));
    return player->ok() ? player.release() : nullptr;
}
