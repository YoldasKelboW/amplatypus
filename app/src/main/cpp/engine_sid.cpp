// SPDX-License-Identifier: GPL-3.0-or-later
//
// Commodore 64 music: SID files (PSID) and Compute's Sidplayer songs (MUS, with STR for the second
// SID), played by libsidplayfp with the reSIDfp emulation of the SID chip.
#include "engine.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include <builders/residfp-builder/residfp.h>
#include <sidplayfp/SidConfig.h>
#include <sidplayfp/SidInfo.h>
#include <sidplayfp/SidTune.h>
#include <sidplayfp/SidTuneInfo.h>
#include <sidplayfp/sidplayfp.h>

namespace {

constexpr int VOICES = 3;
// SID tunes loop for ever and say nothing about their length: they end after this long, or earlier
// once they have been silent for a while.
constexpr double MAX_SECONDS = 180.0;
constexpr double SILENCE_SECONDS = 4.0;
constexpr double PAL_CLOCK = 985248.0;

// Attack times of the SID envelope in seconds; decay and release take three times as long.
constexpr double ATTACK[16] = {0.002, 0.008, 0.016, 0.024, 0.038, 0.056, 0.068, 0.080,
                               0.100, 0.250, 0.500, 0.800, 1.0, 3.0, 5.0, 8.0};

// What the app shows of one voice. libsidplayfp only gives the mixed sound, so level and waveform
// are worked out from what the tune writes to the SID's registers.
struct Voice {
    double level = 0.0;
    bool gate = false;
    bool attacking = false;
    double phase = 0.0;
};

std::string join(const std::string &a, const std::string &b) {
    if (a.empty()) return b;
    if (b.empty()) return a;
    return a + "\n" + b;
}

class SidPlayer : public Player {
public:
    explicit SidPlayer(const std::string &path) : builder("AMPlatypus"), tune(path.c_str()) {
        if (!tune.getStatus()) return;
        tune.selectSong(0);
        const SidTuneInfo *info = tune.getInfo();
        chips = std::max(1, info->sidChips());
        builder.create(engine.info().maxsids());
        if (!builder.getStatus()) return;
        builder.filter(true);
        voices.assign(static_cast<size_t>(chips * VOICES), Voice());
        muted.assign(voices.size(), false);
        ready = configure(48000) && engine.load(&tune);
    }

    bool ok() const { return ready; }

    size_t read(int sampleRate, size_t frames, float *out) override {
        if (!ready || ended) return 0;
        if (sampleRate != rate && !configure(sampleRate)) return 0;
        if (seconds >= MAX_SECONDS) return 0;
        size_t channels = stereo ? 2 : 1;
        buffer.resize(frames * channels);
        uint_least32_t got = engine.play(buffer.data(), static_cast<uint_least32_t>(buffer.size()));
        size_t produced = got / channels;
        if (produced == 0) return 0;
        bool loud = false;
        for (size_t i = 0; i < produced; i++) {
            short left = buffer[i * channels];
            short right = stereo ? buffer[i * channels + 1] : left;
            out[i * 2] = left / 32768.0f;
            out[i * 2 + 1] = right / 32768.0f;
            if (std::abs(left) > 64 || std::abs(right) > 64) loud = true;
        }
        double step = static_cast<double>(produced) / rate;
        seconds += step;
        silentFor = loud ? 0.0 : silentFor + step;
        // A jingle that has finished stays silent; skip the rest instead of playing silence.
        if (seconds > 2.0 && silentFor >= SILENCE_SECONDS) ended = true;
        updateVoices(step);
        return produced;
    }

    double duration() override { return 0.0; }
    double position() override { return seconds; }

    void seek(double target) override {
        if (!ready) return;
        // The emulation cannot jump: it starts the song again and runs it silently up to the target.
        engine.load(&tune);
        seconds = 0.0;
        silentFor = 0.0;
        ended = false;
        std::vector<float> scratch(4096 * 2);
        target = std::min(std::max(target, 0.0), MAX_SECONDS);
        while (seconds + 0.01 < target) {
            size_t frames = std::min<size_t>(4096, static_cast<size_t>((target - seconds) * rate) + 1);
            if (read(rate, frames, scratch.data()) == 0) break;
        }
    }

    int subsongCount() override { return ready ? static_cast<int>(std::max(1u, tune.getInfo()->songs())) : 1; }

    int subsong() override {
        return ready ? static_cast<int>(std::max(1u, tune.getInfo()->currentSong())) - 1 : 0;
    }

    bool selectSubsong(int index) override {
        if (!ready || index < 0 || index >= subsongCount()) return false;
        tune.selectSong(static_cast<unsigned int>(index + 1));
        if (!engine.load(&tune)) return false;
        seconds = 0.0;
        silentFor = 0.0;
        ended = false;
        for (Voice &voice : voices) voice = Voice();
        // Muting is set on the emulation, which a new load resets.
        for (size_t c = 0; c < muted.size(); c++) {
            if (muted[c]) engine.mute(static_cast<unsigned int>(c / VOICES), static_cast<unsigned int>(c % VOICES), true);
        }
        return true;
    }

    void setStereoSeparation(int) override {}
    int channelCount() override { return ready ? chips * VOICES : 0; }

    void setChannelMute(int channel, bool mute) override {
        if (!ready || channel < 0 || channel >= static_cast<int>(muted.size())) return;
        muted[static_cast<size_t>(channel)] = mute;
        engine.mute(static_cast<unsigned int>(channel / VOICES), static_cast<unsigned int>(channel % VOICES), mute);
    }

    std::string metadata(const std::string &key) override {
        if (!ready) return {};
        const SidTuneInfo *info = tune.getInfo();
        auto line = [info](unsigned int i) -> std::string {
            return i < info->numberOfInfoStrings() && info->infoString(i) != nullptr ? info->infoString(i) : "";
        };
        if (key == "title") return line(0);
        if (key == "artist") return line(1);
        if (key == "type_long") {
            // libsidplayfp's names are long ("PlaySID one-file format (PSID)"); the app shows short ones.
            std::string format = info->formatString() != nullptr ? info->formatString() : "";
            std::string name = format.find("RSID") != std::string::npos ? "C64 SID (RSID)"
                : format.find("PSID") != std::string::npos ? "C64 SID (PSID)"
                : "C64 Sidplayer";
            return chips > 1 ? name + ", " + std::to_string(chips) + " SIDs" : name;
        }
        // Shown where other formats name their replay routine: the year and publisher.
        if (key == "tracker") return line(2);
        if (key == "message_raw") {
            std::string text;
            if (!line(1).empty()) text = join(text, "Author: " + line(1));
            if (!line(2).empty()) text = join(text, "Released: " + line(2));
            if (info->songs() > 1) {
                text = join(text, "Song " + std::to_string(info->currentSong()) + " of " + std::to_string(info->songs()));
            }
            for (unsigned int i = 0; i < info->numberOfCommentStrings(); i++) {
                if (info->commentString(i) != nullptr) text = join(text, info->commentString(i));
            }
            return text;
        }
        return {};
    }

    std::string names(int) override { return {}; }

    float vu(int channel) override {
        if (channel < 0 || channel >= static_cast<int>(voices.size())) return 0.0f;
        // The envelope model reaches full level easily; real voices rarely sound that loud.
        return static_cast<float>(voices[static_cast<size_t>(channel)].level * 0.8);
    }

    void playPosition(int &order, int &pattern, int &row) override { order = pattern = row = 0; }
    std::string patternText(int) override { return {}; }
    bool ownScopes() override { return ready; }

    // A picture of each voice drawn from its registers: the selected waveform at the voice's pitch
    // and pulse width, as tall as its envelope.
    void scopes(int points, float *out) override {
        for (int c = 0; c < static_cast<int>(voices.size()); c++) {
            uint8_t *regs = registers[static_cast<size_t>(c / VOICES)];
            const uint8_t *v = regs + (c % VOICES) * 7;
            Voice &voice = voices[static_cast<size_t>(c)];
            double level = muted[static_cast<size_t>(c)] ? 0.0 : voice.level;
            int waveform = v[4] >> 4;
            double frequency = (v[0] | (v[1] << 8)) * PAL_CLOCK / 16777216.0;
            double pulse = ((v[2] | ((v[3] & 0x0f) << 8)) / 4096.0);
            // About 20 milliseconds are shown; very high notes are thinned out to stay readable.
            double cycles = std::min(frequency * 0.02, 24.0);
            for (int i = 0; i < points; i++) {
                double t = voice.phase + cycles * i / points;
                double f = t - std::floor(t);
                double sample;
                if (waveform & 0x8) {
                    noise = noise * 1103515245u + 12345u;
                    sample = static_cast<double>((noise >> 16) & 0x7fff) / 16384.0 - 1.0;
                } else if (waveform & 0x4) {
                    sample = f < std::max(0.02, std::min(0.98, pulse)) ? 1.0 : -1.0;
                } else if (waveform & 0x2) {
                    sample = 2.0 * f - 1.0;
                } else if (waveform & 0x1) {
                    sample = f < 0.5 ? 4.0 * f - 1.0 : 3.0 - 4.0 * f;
                } else {
                    sample = 0.0;
                }
                out[c * points + i] = static_cast<float>(sample * level * 0.3);
            }
            voice.phase += cycles * 0.37;
            voice.phase -= std::floor(voice.phase);
        }
    }

private:
    bool configure(int sampleRate) {
        SidConfig config = engine.config();
        config.frequency = static_cast<uint_least32_t>(sampleRate);
        stereo = chips > 1;
        config.playback = stereo ? SidConfig::STEREO : SidConfig::MONO;
        config.sidEmulation = &builder;
        config.samplingMethod = SidConfig::INTERPOLATE;
        if (!engine.config(config)) return false;
        rate = sampleRate;
        return true;
    }

    void updateVoices(double step) {
        for (int chip = 0; chip < chips; chip++) {
            uint8_t *regs = registers[static_cast<size_t>(std::min(chip, 2))];
            if (!engine.getSidStatus(static_cast<unsigned int>(chip), regs)) continue;
            for (int v = 0; v < VOICES; v++) {
                const uint8_t *r = regs + v * 7;
                Voice &voice = voices[static_cast<size_t>(chip * VOICES + v)];
                bool gate = (r[4] & 0x01) != 0;
                if (gate && !voice.gate) voice.attacking = true;
                voice.gate = gate;
                double attack = ATTACK[r[5] >> 4];
                double decay = ATTACK[r[5] & 0x0f] * 3.0;
                double sustain = (r[6] >> 4) / 15.0;
                double release = ATTACK[r[6] & 0x0f] * 3.0;
                if (gate && voice.attacking) {
                    voice.level = std::min(1.0, voice.level + step / attack);
                    if (voice.level >= 1.0) voice.attacking = false;
                } else if (gate) {
                    voice.level = std::max(sustain, voice.level - step / decay);
                } else {
                    voice.level = std::max(0.0, voice.level - step / release);
                }
                // No waveform selected means no sound, whatever the envelope says.
                if ((r[4] >> 4) == 0) voice.level = std::min(voice.level, 0.05);
            }
        }
    }

    sidplayfp engine;
    ReSIDfpBuilder builder;
    SidTune tune;
    bool ready = false;
    bool ended = false;
    bool stereo = false;
    int chips = 1;
    int rate = 0;
    double seconds = 0.0;
    double silentFor = 0.0;
    std::vector<short> buffer;
    std::vector<Voice> voices;
    std::vector<bool> muted;
    uint8_t registers[3][32] = {};
    uint32_t noise = 12345u;
};

} // namespace

bool isSidFile(const void *data, size_t length, const std::string &path) {
    const auto *bytes = static_cast<const char *>(data);
    if (length >= 4 && (std::memcmp(bytes, "PSID", 4) == 0 || std::memcmp(bytes, "RSID", 4) == 0)) return true;
    // Compute's Sidplayer songs have no signature; their extension tells.
    size_t dot = path.rfind('.');
    if (dot == std::string::npos) return false;
    std::string ext = path.substr(dot + 1);
    std::transform(ext.begin(), ext.end(), ext.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return ext == "mus" || ext == "str";
}

Player *createSidPlayer(const std::string &path) {
    auto player = std::make_unique<SidPlayer>(path);
    return player->ok() ? player.release() : nullptr;
}
