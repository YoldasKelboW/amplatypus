// SPDX-License-Identifier: GPL-3.0-or-later
//
// NEC PC-98 music for the FMP (".opi", ".ovi", ".ozi") and PMD (".m") sound drivers, played by 98fmplayer's
// re-implementations of the drivers on its emulation of the YM2608 sound chip (6 FM voices, 3 SSG square
// voices, ADPCM) and of the PPZ8 PCM driver. Sample banks the song names are read from its folder.
//
// The chip sounds at 55,467 Hz; this resamples to the output rate. The drivers report each track's
// note and volume, which become the channels' levels and sketched waveforms; channels are muted on the
// emulated chip. Songs end after their first loop, or when they fall silent.
#include "engine.h"
#include "voice_view.h"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <cmath>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include "pc98_chip.h"

namespace {

constexpr int CHIP_RATE = PC98_RATE;
// Channels: FM 1-6, SSG 1-3, ADPCM, and the PPZ8's eight PCM voices together.
constexpr int CHANNELS = 11;
constexpr int PPZ = 10;
constexpr double SILENCE_SECONDS = 4.0;
constexpr double NEVER_LOUD_SECONDS = 3.0;
// Songs that do not loop end after this long.
constexpr double MAX_SECONDS = 15 * 60;
constexpr double SEEK_SLICE_SECONDS = 0.02;

std::string extensionOf(const std::string &path) {
    size_t slash = path.find_last_of('/');
    std::string name = slash == std::string::npos ? path : path.substr(slash + 1);
    size_t dot = name.rfind('.');
    if (dot == std::string::npos) return {};
    std::string ext = name.substr(dot + 1);
    std::transform(ext.begin(), ext.end(), ext.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return ext;
}

bool isFmp(const std::string &ext) { return ext == "opi" || ext == "ovi" || ext == "ozi"; }
bool isPmd(const std::string &ext) { return ext == "m" || ext == "m2" || ext == "mz"; }

// Only plain ASCII lines of the song's comments are shown; most are Shift-JIS, which the app cannot
// convert without iconv.
std::string asciiLine(const char *text) {
    if (text == nullptr) return {};
    std::string line;
    for (const char *c = text; *c != '\0'; c++) {
        auto b = static_cast<unsigned char>(*c);
        if (b >= 0x80) return {};
        if (b == 0x1b) return {}; // ANSI colours: not worth taking apart
        if (b >= 0x20) line += static_cast<char>(b);
    }
    while (!line.empty() && line.back() == ' ') line.pop_back();
    return line;
}

class Pc98Player : public Player {
public:
    explicit Pc98Player(pc98_song *song) : song(song), fmp(pc98_is_fmp(song)), voices(CHANNELS) {
        restart();
        ready = true;
    }

    ~Pc98Player() override { pc98_close(song); }

    bool ok() const { return ready; }

    size_t read(int sampleRate, size_t frames, float *out) override {
        if (!ready || sampleRate <= 0) return 0;
        rate = sampleRate;
        if (seeking) {
            std::fill(out, out + frames * 2, 0.0f);
            skipAhead();
            return frames;
        }
        if (ended) return 0;
        double step = static_cast<double>(CHIP_RATE) / rate;
        size_t produced = 0;
        bool loud = false;
        for (; produced < frames; produced++) {
            // Cubic interpolation between the chip's samples around the wanted point.
            while (cursor >= 1.0) {
                if (!nextChipFrame()) break;
                cursor -= 1.0;
            }
            if (cursor >= 1.0) break;
            for (int c = 0; c < 2; c++) {
                float y0 = history[0][c], y1 = history[1][c], y2 = history[2][c], y3 = history[3][c];
                float t = static_cast<float>(cursor);
                float v = y1 + 0.5f * t * (y2 - y0 + t * (2.0f * y0 - 5.0f * y1 + 4.0f * y2 - y3 + t * (3.0f * (y1 - y2) + y3 - y0)));
                out[produced * 2 + c] = std::clamp(v / 32768.0f, -1.0f, 1.0f);
                if (std::abs(v) > 64.0f) loud = true;
            }
            cursor += step;
        }
        double seconds = static_cast<double>(produced) / rate;
        played += seconds;
        followTracks(seconds);
        everLoud = everLoud || loud;
        silentFor = loud ? 0.0 : silentFor + seconds;
        if ((everLoud && silentFor >= SILENCE_SECONDS) || (!everLoud && played >= NEVER_LOUD_SECONDS) ||
            pc98_loops(song) >= 1 || played >= MAX_SECONDS) {
            ended = true;
        }
        return produced;
    }

    // The length is not known before the song has played; the app shows it as unknown.
    double duration() override { return 0.0; }
    double position() override { return played; }

    void seek(double seconds) override {
        if (!ready) return;
        seconds = std::clamp(seconds, 0.0, MAX_SECONDS);
        if (seconds < played) restart();
        target = seconds;
        seeking = target > played;
        ended = false;
    }

    void setStereoSeparation(int) override {}
    int channelCount() override { return ready ? CHANNELS : 0; }

    void setChannelMute(int channel, bool mute) override {
        if (!ready || channel < 0 || channel >= CHANNELS) return;
        muted[static_cast<size_t>(channel)] = mute;
        applyMutes();
    }

    std::string metadata(const std::string &key) override {
        if (!ready) return {};
        if (key == "type_long") return fmp ? "PC-98 (FMP)" : "PC-98 (PMD)";
        if (key == "tracker") return fmp ? "FMP" : "PMD";
        if (key == "title") return comment(0);
        if (key == "artist") return pc98_pmd_comments(song) ? comment(1) : std::string();
        if (key == "message_raw") {
            std::string text;
            for (int line = 0; line < 16; line++) {
                std::string part = comment(line);
                if (part.empty()) continue;
                if (!text.empty()) text += "\n";
                text += part;
            }
            if (pc98_missing_samples(song)) {
                if (!text.empty()) text += "\n";
                text += "Some sample files the song names were not found.";
            }
            return text;
        }
        return {};
    }

    std::string names(int kind) override {
        if (kind != 0) return {};
        return "FM 1\nFM 2\nFM 3\nFM 4\nFM 5\nFM 6\nSSG 1\nSSG 2\nSSG 3\nADPCM\nPPZ8";
    }

    float vu(int channel) override { return voices.vu(channel); }
    void playPosition(int &order, int &pattern, int &row) override { order = pattern = row = 0; }
    std::string patternText(int) override { return {}; }
    bool ownScopes() override { return ready; }
    void scopes(int points, float *out) override { voices.scopes(points, out); }

private:
    void restart() {
        pc98_restart(song);
        applyMutes();
        for (auto &frame : history) frame[0] = frame[1] = 0.0f;
        chipBuffer.clear();
        chipAt = 0;
        cursor = 3.0; // fill the interpolation history first
        played = silentFor = target = 0.0;
        everLoud = ended = seeking = false;
        voices.reset();
    }

    void applyMutes() {
        unsigned mask = 0;
        for (int c = 0; c < CHANNELS; c++) {
            if (muted[static_cast<size_t>(c)]) mask |= 1u << c;
        }
        pc98_set_mute(song, mask);
    }

    // The next of the chip's own samples, into the interpolation history.
    bool nextChipFrame() {
        if (chipAt >= chipBuffer.size()) {
            chipBuffer.assign(1024 * 2, 0);
            pc98_render(song, chipBuffer.data(), 1024);
            chipAt = 0;
        }
        for (int i = 0; i < 3; i++) {
            history[i][0] = history[i + 1][0];
            history[i][1] = history[i + 1][1];
        }
        history[3][0] = chipBuffer[chipAt];
        history[3][1] = chipBuffer[chipAt + 1];
        chipAt += 2;
        return true;
    }

    // Runs the chip on towards the seek target without sound, for a slice of time.
    void skipAhead() {
        auto until = std::chrono::steady_clock::now() + std::chrono::duration<double>(SEEK_SLICE_SECONDS);
        std::vector<int16_t> scratch(1024 * 2);
        while (played < target && std::chrono::steady_clock::now() < until && pc98_loops(song) < 1) {
            std::fill(scratch.begin(), scratch.end(), 0);
            pc98_render(song, scratch.data(), 1024);
            played += 1024.0 / CHIP_RATE;
        }
        if (played >= target || pc98_loops(song) >= 1) {
            seeking = false;
            chipBuffer.clear();
            chipAt = 0;
        }
    }

    // Each channel's loudness and pitch from the driver's tracks: FM 3 has three extra tracks, and the
    // PPZ8 channel stands for its eight voices.
    void followTracks(double seconds) {
        static const int FIRST[CHANNELS] = {0, 1, 2, 6, 7, 8, 9, 10, 11, 12, 13};
        static const int COUNT[CHANNELS] = {1, 1, 4, 1, 1, 1, 1, 1, 1, 1, 8};
        for (int c = 0; c < CHANNELS; c++) {
            double loudness = 0.0, hz = 0.0;
            for (int t = FIRST[c]; t < FIRST[c] + COUNT[c]; t++) {
                pc98_track track = pc98_track_status(song, t);
                if (!track.sounding || track.loudness <= loudness) continue;
                loudness = track.loudness;
                int note = (track.key >> 4) * 12 + (track.key & 0x0f);
                hz = 440.0 * std::pow(2.0, (note - 57) / 12.0);
            }
            VoiceView::Shape shape = c >= 6 && c < 9 ? VoiceView::SQUARE : c >= 9 ? VoiceView::NOISE : VoiceView::SINE;
            voices.set(c, loudness, hz, shape);
        }
        voices.advance(seconds, muted);
    }

    std::string comment(int line) { return asciiLine(pc98_comment(song, line)); }

    pc98_song *song;
    bool fmp;
    std::vector<int16_t> chipBuffer;
    size_t chipAt = 0;
    float history[4][2] = {};
    double cursor = 3.0;
    bool ready = false;
    bool ended = false;
    bool seeking = false;
    bool everLoud = false;
    double played = 0.0, silentFor = 0.0, target = 0.0;
    int rate = 48000;
    std::vector<bool> muted = std::vector<bool>(CHANNELS, false);
    VoiceView voices;
};

} // namespace

bool isPc98Data(const void *, size_t length, const std::string &path) {
    std::string ext = extensionOf(path);
    return length > 0 && (isFmp(ext) || isPmd(ext));
}

Player *createPc98Player(const void *, size_t, const std::string &path) {
    // The file says which driver it is for; the extension is only a guess.
    pc98_song *song = pc98_open(path.c_str());
    if (song == nullptr) return nullptr;
    return new Pc98Player(song);
}
