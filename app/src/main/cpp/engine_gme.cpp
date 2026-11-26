// SPDX-License-Identifier: GPL-3.0-or-later
//
// Console and home computer music, played by Game Music Emu: NES (NSF, NSFE), SNES (SPC), Game Boy
// (GBS), Sega Mega Drive and Master System (VGM, VGZ, GYM), PC Engine (HES), MSX (KSS), Atari 8-bit
// (SAP) and ZX Spectrum / Amstrad CPC (AY).
//
// Where the emulator can, it renders every voice into a stereo pair of its own ("multi-channel"); the
// player mixes them itself and so knows each voice's level and waveform, and the voices show as
// channels. The SNES emulator and the Mega Drive FM sound cannot. Their voices still show as channels
// and can be muted; level and pitch come from the chips' registers: the SNES sound chip's are read from
// the emulator (gme_peek.cpp), the Mega Drive's are followed in the file itself (fm_track.h). Their
// waveforms are only sketched from that (voice_view.h).
#include "engine.h"
#include "fm_track.h"
#include "gme_peek.h"
#include "voice_view.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include <gme/gme.h>
#include <zlib.h>

namespace {

// Files that do not say how long they are play this long, then fade out.
constexpr int DEFAULT_LENGTH_MS = 150000;
constexpr int FADE_MS = 8000;
constexpr int PAIRS = 8;          // stereo pairs in multi-channel output
constexpr int SCOPE_FRAMES = 1024; // waveform history per voice
// Game Music Emu's own silence detection renders seconds ahead, which delays muting by as much; the
// player watches for silence itself instead. A track that has played ends after this much silence,
// one that never makes a sound after this long.
constexpr double SILENCE_SECONDS = 4.0;
constexpr double NEVER_LOUD_SECONDS = 2.5;
constexpr int GYM_HEADER = 0x1AC;
// HES and KSS files cannot say how many songs they hold: they claim 256, most of them silent. Each is
// tried briefly at a low rate when the file is loaded, and only those that make a sound are offered.
constexpr int SCAN_RATE = 8000;
constexpr int SCAN_MS = 400;
constexpr int SPC_VOICES = 8;

bool claimsAllTracks(gme_type_t type) { return type == gme_hes_type || type == gme_kss_type; }

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

// The file type from the first bytes, or else from the extension. Modland keeps AY files as ".emul".
gme_type_t identify(const void *data, size_t length, const std::string &path) {
    if (length >= 4) {
        const char *header = gme_identify_header(data);
        if (header != nullptr && header[0] != '\0') {
            gme_type_t type = gme_identify_extension(header);
            if (type != nullptr) return type;
        }
    }
    std::string ext = extensionOf(path);
    if (ext == "emul") ext = "ay";
    // Extensions Game Music Emu shares with other formats are only trusted with the right header.
    static const char *const BY_NAME[] = {"vgz", "vgm", "gym", "spc", "nsf", "nsfe", "gbs", "hes", "kss", "sap", "ay"};
    for (const char *known : BY_NAME) {
        if (ext == known) return gme_identify_extension(ext.c_str());
    }
    return nullptr;
}

// Packed GYM files (most of Modland's) hold their data zlib-compressed after the header, which Game Music
// Emu does not read. They are unpacked here into a plain GYM file.
std::vector<char> unpackGym(const char *data, size_t length) {
    std::vector<char> file(data, data + length);
    if (length <= GYM_HEADER || std::memcmp(data, "GYMX", 4) != 0) return file;
    const auto *packed = reinterpret_cast<const unsigned char *>(data + GYM_HEADER - 4);
    uLongf size = packed[0] | packed[1] << 8 | packed[2] << 16 | static_cast<uLongf>(packed[3]) << 24;
    if (size == 0 || size > 64u * 1024 * 1024) return file;
    std::vector<char> plain(GYM_HEADER + size);
    std::memcpy(plain.data(), data, GYM_HEADER);
    std::memset(plain.data() + GYM_HEADER - 4, 0, 4);
    if (uncompress(reinterpret_cast<Bytef *>(plain.data() + GYM_HEADER), &size,
                   reinterpret_cast<const Bytef *>(data + GYM_HEADER), static_cast<uLong>(length - GYM_HEADER)) != Z_OK) {
        return file;
    }
    plain.resize(GYM_HEADER + size);
    return plain;
}

void join(std::string &text, const std::string &line) {
    if (line.empty()) return;
    if (!text.empty()) text += "\n";
    text += line;
}

class GmePlayer : public Player {
public:
    GmePlayer(gme_type_t type, const void *data, size_t length)
        : type(type), file(unpackGym(static_cast<const char *>(data), length)) {
        ready = open(48000) && findTracks() && start(0);
    }

    ~GmePlayer() override {
        if (info != nullptr) gme_free_info(info);
        if (emu != nullptr) gme_delete(emu);
    }

    bool ok() const { return ready; }

    size_t read(int sampleRate, size_t frames, float *out) override {
        if (!ready) return 0;
        if (sampleRate != rate) {
            // The emulator's rate is fixed when it is created: make a new one and go back to where we were.
            int at = gme_tell(emu);
            if (!open(sampleRate) || !start(track)) return 0;
            gme_seek(emu, at);
            followFrom(at / 1000.0);
        }
        if (gme_track_ended(emu) || ended) return 0;
        size_t channels = multi ? PAIRS * 2 : 2;
        buffer.resize(frames * channels);
        if (gme_play(emu, static_cast<int>(buffer.size()), buffer.data()) != nullptr) return 0;
        if (view == View::SPC || view == View::FM) followVoices(static_cast<double>(frames) / rate);
        std::fill(peak.begin(), peak.end(), 0.0f);
        bool loud = false;
        for (size_t i = 0; i < frames; i++) {
            const short *frame = buffer.data() + i * channels;
            if (!multi) {
                out[i * 2] = frame[0] / 32768.0f;
                out[i * 2 + 1] = frame[1] / 32768.0f;
                if (std::abs(frame[0]) > 64 || std::abs(frame[1]) > 64) loud = true;
                continue;
            }
            int left = 0, right = 0;
            for (int p = 0; p < PAIRS; p++) {
                int l = frame[p * 2], r = frame[p * 2 + 1];
                left += l;
                right += r;
                float level = static_cast<float>(std::max(std::abs(l), std::abs(r))) / 32768.0f;
                peak[static_cast<size_t>(p)] = std::max(peak[static_cast<size_t>(p)], level);
                history[static_cast<size_t>(p) * SCOPE_FRAMES + historyPos] = (l + r) / 65536.0f;
            }
            historyPos = (historyPos + 1) % SCOPE_FRAMES;
            if (std::abs(left) > 64 || std::abs(right) > 64) loud = true;
            out[i * 2] = std::clamp(left / 32768.0f, -1.0f, 1.0f);
            out[i * 2 + 1] = std::clamp(right / 32768.0f, -1.0f, 1.0f);
        }
        // Meters fall back slowly instead of flickering with every block.
        for (size_t p = 0; p < level.size(); p++) level[p] = std::max(peak[p], level[p] * 0.85f);
        // Muted voices count as silence too, so a file whose voices are all muted ends like a silent one.
        double step = static_cast<double>(frames) / rate;
        played += step;
        everLoud = everLoud || loud;
        silentFor = loud ? 0.0 : silentFor + step;
        if ((everLoud && silentFor >= SILENCE_SECONDS) || (!everLoud && played >= NEVER_LOUD_SECONDS)) ended = true;
        return frames;
    }

    double duration() override { return (lengthMs + FADE_MS) / 1000.0; }
    double position() override { return ready ? gme_tell(emu) / 1000.0 : 0.0; }

    void seek(double seconds) override {
        if (!ready) return;
        gme_seek(emu, static_cast<int>(std::max(0.0, seconds) * 1000.0));
        followFrom(gme_tell(emu) / 1000.0);
        silentFor = 0.0;
        ended = false;
    }

    void setStereoSeparation(int) override {}
    // Voices the player can see on their own, or whose registers it can read, are offered as channels.
    int channelCount() override {
        if (!ready) return 0;
        if (multi) return gme_voice_count(emu);
        return view == View::NONE ? 0 : voices.size();
    }

    void setChannelMute(int channel, bool mute) override {
        if (!ready || channel < 0 || channel >= channelCount()) return;
        if (static_cast<size_t>(channel) >= muted.size()) muted.resize(static_cast<size_t>(channel) + 1, false);
        muted[static_cast<size_t>(channel)] = mute;
        gme_mute_voice(emu, channel, mute ? 1 : 0);
    }

    std::string metadata(const std::string &key) override {
        if (!ready || info == nullptr) return {};
        std::string song = info->song, game = info->game, author = info->author;
        if (key == "title") return !song.empty() ? song : game;
        if (key == "artist") return author;
        if (key == "type_long") return std::string(gme_type_system(type)) + " (" + formatName() + ")";
        // Shown where trackers name their tracker: the game the music is from.
        if (key == "tracker") return !song.empty() && song != game ? game : std::string(info->copyright);
        if (key == "message_raw") {
            std::string text;
            join(text, game.empty() ? "" : "Game: " + game);
            join(text, author.empty() ? "" : "Author: " + author);
            join(text, std::string(info->copyright).empty() ? "" : "Copyright: " + std::string(info->copyright));
            join(text, std::string(info->dumper).empty() ? "" : "Ripped by: " + std::string(info->dumper));
            if (subsongCount() > 1) join(text, "Song " + std::to_string(track + 1) + " of " + std::to_string(subsongCount()));
            join(text, info->comment);
            return text;
        }
        return {};
    }

    // The sound chip's voices, where trackers list their samples.
    std::string names(int kind) override {
        if (!ready || kind != 0) return {};
        std::string text;
        for (int v = 0; v < gme_voice_count(emu); v++) {
            const char *name = gme_voice_name(emu, v);
            join(text, name != nullptr ? name : "");
        }
        return text;
    }

    float vu(int channel) override {
        if (view == View::SPC || view == View::FM) return voices.vu(channel);
        if (!multi || channel < 0) return 0.0f;
        if (static_cast<size_t>(channel) < muted.size() && muted[static_cast<size_t>(channel)]) return 0.0f;
        // Voices past the eighth share the last pair. A single voice rarely goes past half of full
        // scale (the chip's voices share its output), so the meter shows twice the level.
        return std::min(1.0f, 2.0f * level[static_cast<size_t>(std::min(channel, PAIRS - 1))]);
    }

    void playPosition(int &order, int &pattern, int &row) override { order = pattern = row = 0; }
    std::string patternText(int) override { return {}; }
    bool ownScopes() override { return ready && (multi || view != View::NONE); }

    void scopes(int points, float *out) override {
        if (!multi) {
            voices.scopes(points, out);
            return;
        }
        int voices = channelCount();
        for (int c = 0; c < voices; c++) {
            int pair = std::min(c, PAIRS - 1);
            bool silent = static_cast<size_t>(c) < muted.size() && muted[static_cast<size_t>(c)];
            for (int i = 0; i < points; i++) {
                size_t at = (historyPos + static_cast<size_t>(SCOPE_FRAMES * i / points)) % SCOPE_FRAMES;
                out[c * points + i] = silent ? 0.0f : history[static_cast<size_t>(pair) * SCOPE_FRAMES + at] * 2.0f;
            }
        }
    }

    int subsongCount() override { return ready ? static_cast<int>(tracks.size()) : 1; }
    int subsong() override { return track; }

    bool selectSubsong(int index) override {
        if (!ready || index < 0 || index >= subsongCount()) return false;
        return start(index);
    }

private:
    bool open(int sampleRate) {
        if (emu != nullptr) gme_delete(emu);
        emu = gme_new_emu_multi_channel(type, sampleRate);
        if (emu == nullptr) return false;
        if (gme_load_data(emu, file.data(), static_cast<long>(file.size())) != nullptr) {
            gme_delete(emu);
            emu = nullptr;
            return false;
        }
        rate = sampleRate;
        multi = gme_multi_channel(emu) != 0;
        gme_ignore_silence(emu, 1);
        view = View::NONE;
        if (multi) {
            view = View::MULTI;
        } else if (type == gme_spc_type) {
            view = View::SPC;
        } else if ((type == gme_vgm_type || type == gme_vgz_type || type == gme_gym_type) && gme_voice_count(emu) == FmTrack::VOICES &&
                   fm.load(file, type == gme_gym_type)) {
            // Mega Drive files; Master System FM music (YM2413) has no YM2612 and is not followed.
            view = View::FM;
        }
        voices.resize(view == View::SPC ? SPC_VOICES : view == View::FM ? FmTrack::VOICES : 0);
        return true;
    }

    // Reads the voices' registers after `seconds` more of playback.
    void followVoices(double seconds) {
        if (view == View::SPC) {
            unsigned char regs[128];
            if (gmeSpcDspRegisters(emu, regs)) {
                for (int v = 0; v < SPC_VOICES; v++) {
                    const unsigned char *r = regs + v * 0x10;
                    int volume = std::max(std::abs(static_cast<int>(static_cast<int8_t>(r[0]))),
                                          std::abs(static_cast<int>(static_cast<int8_t>(r[1]))));
                    int envelope = r[8] & 0x7F;
                    int pitch = (r[2] | r[3] << 8) & 0x3FFF;
                    // Pitch 0x1000 plays a sample at the chip's 32 kHz; drawn as if it held a 16-sample cycle.
                    double hz = pitch * 32000.0 / 4096.0 / 16.0;
                    bool noise = (regs[0x3D] >> v & 1) != 0;
                    // Envelope times volume; the square root makes quiet voices show, as a meter in decibels would.
                    voices.set(v, std::sqrt(envelope / 127.0 * volume / 128.0), hz, noise ? VoiceView::NOISE : VoiceView::SINE);
                }
            }
        } else {
            followed += seconds;
            fm.advanceTo(followed);
            fm.decayPcm(seconds);
            for (int v = 0; v < FmTrack::VOICES; v++) {
                double loudness, hz;
                fm.voice(v, loudness, hz);
                VoiceView::Shape shape = v == FmTrack::PCM ? VoiceView::NOISE
                                       : v == FmTrack::PSG ? (fm.psgNoiseLoudest() ? VoiceView::NOISE : VoiceView::SQUARE)
                                                           : VoiceView::SINE;
                voices.set(v, loudness, hz, shape);
            }
        }
        voices.advance(seconds, muted);
    }

    // After a jump or a new start: the Mega Drive file is read again from the beginning up to `seconds`.
    void followFrom(double seconds) {
        voices.reset();
        if (view != View::FM) return;
        fm.restart();
        followed = std::max(0.0, seconds);
        fm.advanceTo(followed);
    }

    // The songs worth offering: all of them, or for HES and KSS those that make a sound.
    bool findTracks() {
        int count = std::max(1, gme_track_count(emu));
        tracks.clear();
        if (!claimsAllTracks(type)) {
            for (int t = 0; t < count; t++) tracks.push_back(t);
            return true;
        }
        Music_Emu *scan = gme_new_emu(type, SCAN_RATE);
        if (scan == nullptr) return false;
        if (gme_load_data(scan, file.data(), static_cast<long>(file.size())) == nullptr) {
            gme_ignore_silence(scan, 1);
            std::vector<short> samples(SCAN_RATE * SCAN_MS / 1000 * 2);
            for (int t = 0; t < count; t++) {
                if (gme_start_track(scan, t) != nullptr) continue;
                if (gme_play(scan, static_cast<int>(samples.size()), samples.data()) != nullptr) continue;
                bool loud = std::any_of(samples.begin(), samples.end(), [](short v) { return std::abs(v) > 200; });
                if (loud) tracks.push_back(t);
            }
        }
        gme_delete(scan);
        // Nothing to hear: most likely music for a sound chip the emulator lacks (MSX FM sound).
        return !tracks.empty();
    }

    bool start(int index) {
        if (index < 0 || index >= static_cast<int>(tracks.size())) return false;
        int number = tracks[static_cast<size_t>(index)];
        if (gme_start_track(emu, number) != nullptr) return false;
        track = index;
        if (info != nullptr) gme_free_info(info);
        info = nullptr;
        gme_track_info(emu, &info, number);
        lengthMs = info != nullptr && info->play_length > 0 ? info->play_length : DEFAULT_LENGTH_MS;
        gme_set_fade_msecs(emu, lengthMs, FADE_MS);
        // Muting is set per voice on the emulator; a new track keeps it, a new emulator does not.
        for (size_t c = 0; c < muted.size(); c++) gme_mute_voice(emu, static_cast<int>(c), muted[c] ? 1 : 0);
        std::fill(level.begin(), level.end(), 0.0f);
        std::fill(history.begin(), history.end(), 0.0f);
        followFrom(0.0);
        played = silentFor = 0.0;
        everLoud = ended = false;
        return true;
    }

    std::string formatName() const {
        std::string ext = lower(gme_type_extension(type));
        std::transform(ext.begin(), ext.end(), ext.begin(), [](unsigned char c) { return static_cast<char>(std::toupper(c)); });
        return ext;
    }

    gme_type_t type;
    std::vector<char> file;
    Music_Emu *emu = nullptr;
    gme_info_t *info = nullptr;
    bool ready = false;
    bool multi = false;
    // How the voices are seen: rendered apart, or from the registers of the SNES or Mega Drive chips.
    enum class View { NONE, MULTI, SPC, FM };
    View view = View::NONE;
    VoiceView voices;
    FmTrack fm;
    double followed = 0.0;
    int rate = 0;
    int track = 0;            // index into tracks
    std::vector<int> tracks;  // the file's song numbers that are offered
    int lengthMs = DEFAULT_LENGTH_MS;
    std::vector<short> buffer;
    std::vector<bool> muted;
    std::vector<float> peak = std::vector<float>(PAIRS, 0.0f);
    std::vector<float> level = std::vector<float>(PAIRS, 0.0f);
    std::vector<float> history = std::vector<float>(PAIRS * SCOPE_FRAMES, 0.0f);
    size_t historyPos = 0;
    double played = 0.0;
    double silentFor = 0.0;
    bool everLoud = false;
    bool ended = false;
};

} // namespace

bool isGmeData(const void *data, size_t length, const std::string &path) {
    return identify(data, length, path) != nullptr;
}

Player *createGmePlayer(const void *data, size_t length, const std::string &path) {
    gme_type_t type = identify(data, length, path);
    if (type == nullptr) return nullptr;
    auto player = std::make_unique<GmePlayer>(type, data, length);
    return player->ok() ? player.release() : nullptr;
}
