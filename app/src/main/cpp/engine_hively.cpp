// SPDX-License-Identifier: GPL-3.0-or-later
//
// AHX and HivelyTracker modules, played with the HivelyTracker replayer
// (Copyright (c) 2006-2018 Pete Gordon, BSD 3-Clause, see third_party/hivelytracker/LICENSE).
//
// This file must be compiled with -fsigned-char: the replayer's "int8" is a plain char.
#include "engine.h"

#include <cstdio>
#include <cstring>
#include <mutex>
#include <vector>

extern "C" {
#include "hvl_replay.h"
// Not declared in the header.
void hvl_play_irq(struct hvl_tune *ht);
void hvl_mixchunk(struct hvl_tune *ht, uint32 samples, int8 *buf1, int8 *buf2, int32 bufmod);
}

namespace {

// The replayer is set up for one output rate when a tune is parsed. The app always asks for this one.
const uint32 RATE = 48000;
// Stereo width used for AHX, 0 (mono) to 4 (hard left/right as on the Amiga). HVL files carry their own.
const uint32 AHX_STEREO = 2;
// Tunes that never signal their end are cut off here.
const double MAX_SECONDS = 20.0 * 60.0;
// Length of one waveform buffer in the replayer, as 16.16 fixed point.
const uint32 WAVE_END = 0x280u << 16;

std::once_flag tablesOnce;

// The replayer's loaders trust the file. This walks the same layout first so a truncated or
// foreign file is rejected instead of read past its end.
bool layoutFits(const uint8_t *buf, size_t length) {
    if (length < 16) return false;
    bool ahx = buf[0] == 'T';
    size_t posn = ((buf[6] & 0x0f) << 8) | buf[7];
    size_t insn = buf[12];
    size_t ssn = buf[13];
    size_t trkl = buf[10];
    size_t trkn = buf[11];
    size_t channels = ahx ? 4 : (buf[8] >> 2) + 4;
    if (posn < 1 || posn > 1000 || trkl < 1 || trkl > 64 || insn > 64 || channels > MAX_CHANNELS) return false;
    if (!ahx && buf[15] > 4) return false;
    bool firstTrackSaved = (buf[6] & 0x80) == 0;

    size_t at = (ahx ? 14 : 16) + ssn * 2 + posn * channels * 2;
    if (ahx) {
        at += trkn * trkl * 3;
        if (firstTrackSaved) at += trkl * 3;
    } else {
        for (size_t track = firstTrackSaved ? 0 : 1; track <= trkn; track++) {
            for (size_t row = 0; row < trkl; row++) {
                if (at >= length) return false;
                at += buf[at] == 0x3f ? 1 : 5;
            }
        }
    }
    for (size_t i = 1; i <= insn; i++) {
        if (at + 22 > length) return false;
        at += 22 + buf[at + 21] * (ahx ? 4 : 5);
    }
    return at <= length;
}

hvl_tune *parse(const void *data, size_t length) {
    const auto *buf = static_cast<const uint8_t *>(data);
    if (!isHivelyData(data, length) || !layoutFits(buf, length)) return nullptr;
    std::call_once(tablesOnce, hvl_InitReplayer);
    hvl_tune *tune = hvl_ParseTune(buf, static_cast<uint32>(length), RATE, AHX_STEREO);
    if (tune != nullptr && !hvl_InitSubsong(tune, 0)) {
        hvl_FreeTune(tune);
        return nullptr;
    }
    return tune;
}

uint32 samplesPerTick(const hvl_tune *tune) {
    uint32 multiplier = tune->ht_SpeedMultiplier == 0 ? 1 : tune->ht_SpeedMultiplier;
    return RATE / 50 / multiplier;
}

// The tune's text is in the Amiga's Latin-1.
std::string latin1(const char *text) {
    std::string out;
    for (const auto *p = reinterpret_cast<const unsigned char *>(text); *p != 0; p++) {
        if (*p < 0x80) {
            out += static_cast<char>(*p);
        } else {
            out += static_cast<char>(0xC0 | (*p >> 6));
            out += static_cast<char>(0x80 | (*p & 0x3F));
        }
    }
    return out;
}

// Restarts the tune and runs it silently up to `ticks`.
void runTo(hvl_tune *tune, uint64_t ticks) {
    hvl_InitSubsong(tune, 0);
    for (uint64_t t = 0; t < ticks; t++) hvl_play_irq(tune);
}

class HivelyPlayer : public Player {
public:
    HivelyPlayer(hvl_tune *tune, bool ahx, uint64_t lengthTicks) : tune(tune), ahx(ahx), lengthTicks(lengthTicks) {}
    ~HivelyPlayer() override { hvl_FreeTune(tune); }

    size_t read(int, size_t frames, float *out) override {
        size_t done = 0;
        while (done < frames) {
            if (pendingAt >= pending.size() / 2) {
                if (ticks >= lengthTicks) break;
                tick();
            }
            size_t available = pending.size() / 2 - pendingAt;
            size_t count = frames - done < available ? frames - done : available;
            for (size_t i = 0; i < count * 2; i++) out[done * 2 + i] = pending[pendingAt * 2 + i] / 32768.0f;
            pendingAt += count;
            done += count;
        }
        return done;
    }

    double duration() override { return seconds(lengthTicks); }

    double position() override {
        // Minus what has been mixed but not handed out yet.
        double ahead = static_cast<double>(pending.size() / 2 - pendingAt) / RATE;
        double at = seconds(ticks) - ahead;
        return at < 0.0 ? 0.0 : at;
    }

    void seek(double target) override {
        uint64_t wanted = target <= 0.0 ? 0 : static_cast<uint64_t>(target * RATE / samplesPerTick(tune));
        if (wanted > lengthTicks) wanted = lengthTicks;
        runTo(tune, wanted);
        ticks = wanted;
        pending.clear();
        pendingAt = 0;
    }

    void setStereoSeparation(int) override {}
    int channelCount() override { return tune->ht_Channels; }

    void setChannelMute(int channel, bool mute) override {
        if (channel >= 0 && channel < MAX_CHANNELS) muted[channel] = mute;
    }

    std::string metadata(const std::string &key) override {
        if (key == "title") return latin1(tune->ht_Name);
        if (key == "type_long") return ahx ? "AHX (Abyss' Highest eXperience)" : "HivelyTracker";
        return {};
    }

    std::string names(int kind) override {
        std::string all;
        if (kind != 1) return all;
        for (int i = 1; i <= tune->ht_InstrumentNr; i++) {
            all += latin1(tune->ht_Instruments[i].ins_Name);
            all += '\n';
        }
        return all;
    }

    float vu(int channel) override {
        if (channel < 0 || channel >= tune->ht_Channels) return 0.0f;
        return tune->ht_Voices[channel].vc_VoiceVolume / 64.0f;
    }

    void playPosition(int &order, int &pattern, int &row) override {
        // AHX has no patterns: every position names one track per channel. A position is shown as a pattern.
        order = tune->ht_PosNr;
        pattern = tune->ht_PosNr;
        row = tune->ht_NoteNr;
    }

    std::string patternText(int pattern) override {
        static const char *const NOTES[12] = {"C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-"};
        std::string text;
        if (pattern < 0 || pattern >= tune->ht_PositionNr) return text;
        const hvl_position &position = tune->ht_Positions[pattern];
        char cell[16];
        for (int r = 0; r < tune->ht_TrackLength; r++) {
            for (int c = 0; c < tune->ht_Channels; c++) {
                const hvl_step &step = tune->ht_Tracks[position.pos_Track[c]][r];
                if (c > 0) text += '|';
                if (step.stp_Note >= 1 && step.stp_Note <= 60) {
                    snprintf(cell, sizeof(cell), "%s%d ", NOTES[(step.stp_Note - 1) % 12], (step.stp_Note - 1) / 12 + 1);
                } else {
                    snprintf(cell, sizeof(cell), "... ");
                }
                text += cell;
                if (step.stp_Instrument != 0) {
                    snprintf(cell, sizeof(cell), "%02d ", step.stp_Instrument);
                } else {
                    snprintf(cell, sizeof(cell), ".. ");
                }
                text += cell;
                if (step.stp_FX != 0 || step.stp_FXParam != 0) {
                    snprintf(cell, sizeof(cell), "%X%02X", step.stp_FX & 0xF, step.stp_FXParam);
                } else {
                    snprintf(cell, sizeof(cell), "...");
                }
                text += cell;
            }
            text += '\n';
        }
        return text;
    }

private:
    double seconds(uint64_t t) { return static_cast<double>(t) * samplesPerTick(tune) / RATE; }

    void tick() {
        uint32 samples = samplesPerTick(tune);
        pending.resize(static_cast<size_t>(samples) * 2);
        pendingAt = 0;
        hvl_play_irq(tune);
        // The replayer sets the volume anew on every tick, so a muted voice keeps running and
        // comes back in step.
        for (int c = 0; c < tune->ht_Channels; c++) {
            if (muted[c]) tune->ht_Voices[c].vc_VoiceVolume = 0;
        }
        auto *left = reinterpret_cast<int8 *>(pending.data());
        hvl_mixchunk(tune, samples, left, left + 2, 4);
        ticks++;
    }

    hvl_tune *tune;
    bool ahx;
    uint64_t lengthTicks;
    uint64_t ticks = 0;
    std::vector<int16_t> pending;
    size_t pendingAt = 0;
    bool muted[MAX_CHANNELS] = {};
};

// A second copy of the tune, advanced in step with the audible one. Nothing is mixed: each voice's
// waveform is read straight from the replayer's voice buffers.
class HivelyScopes : public ScopeSet {
public:
    explicit HivelyScopes(hvl_tune *tune) : tune(tune) {}
    ~HivelyScopes() override { hvl_FreeTune(tune); }

    size_t channels() override { return tune->ht_Channels; }

    void seek(double target) override {
        uint64_t wanted = target <= 0.0 ? 0 : static_cast<uint64_t>(target * RATE / samplesPerTick(tune));
        runTo(tune, wanted);
        leftInTick = 0;
    }

    void render(int, int frames, float *out, int points) override {
        int step = frames / points;
        if (step < 1) step = 1;
        int channels = tune->ht_Channels;
        for (int frame = 0; frame < frames; frame++) {
            if (leftInTick == 0) {
                hvl_play_irq(tune);
                leftInTick = samplesPerTick(tune);
            }
            leftInTick--;
            bool wanted = frame % step == 0 && frame / step < points;
            for (int c = 0; c < channels; c++) {
                hvl_voice &voice = tune->ht_Voices[c];
                if (voice.vc_SamplePos >= WAVE_END) voice.vc_SamplePos -= WAVE_END;
                if (wanted) {
                    int value = voice.vc_MixSource[voice.vc_SamplePos >> 16];
                    if (voice.vc_RingMixSource != nullptr) {
                        if (voice.vc_RingSamplePos >= WAVE_END) voice.vc_RingSamplePos -= WAVE_END;
                        value = (value * voice.vc_RingMixSource[voice.vc_RingSamplePos >> 16]) >> 7;
                    }
                    out[c * points + frame / step] = value * voice.vc_VoiceVolume / (128.0f * 64.0f);
                }
                voice.vc_SamplePos += voice.vc_Delta;
                if (voice.vc_RingMixSource != nullptr) {
                    voice.vc_RingSamplePos += voice.vc_RingDelta;
                    if (voice.vc_RingSamplePos >= WAVE_END) voice.vc_RingSamplePos -= WAVE_END;
                }
            }
        }
    }

private:
    hvl_tune *tune;
    uint32 leftInTick = 0;
};

} // namespace

bool isHivelyData(const void *data, size_t length) {
    const auto *buf = static_cast<const uint8_t *>(data);
    if (length < 4) return false;
    if (buf[0] == 'T' && buf[1] == 'H' && buf[2] == 'X' && buf[3] < 3) return true;
    return buf[0] == 'H' && buf[1] == 'V' && buf[2] == 'L' && buf[3] < 2;
}

Player *createHivelyPlayer(const void *data, size_t length) {
    hvl_tune *tune = parse(data, length);
    if (tune == nullptr) return nullptr;
    // Find the length by running the tune once without mixing.
    uint64_t maxTicks = static_cast<uint64_t>(MAX_SECONDS * RATE / samplesPerTick(tune));
    uint64_t lengthTicks = 0;
    while (lengthTicks < maxTicks && !tune->ht_SongEndReached) {
        hvl_play_irq(tune);
        lengthTicks++;
    }
    hvl_InitSubsong(tune, 0);
    return new HivelyPlayer(tune, static_cast<const uint8_t *>(data)[0] == 'T', lengthTicks);
}

ScopeSet *createHivelyScopes(const void *data, size_t length) {
    hvl_tune *tune = parse(data, length);
    return tune == nullptr ? nullptr : new HivelyScopes(tune);
}
