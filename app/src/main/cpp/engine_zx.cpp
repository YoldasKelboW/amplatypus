// SPDX-License-Identifier: GPL-3.0-or-later
//
// ZX Spectrum tracker music: Pro Tracker 1-3, Sound Tracker and its compilers, ASC Sound Master,
// SQ-Tracker, Pro Sound Creator and Maker, Fast Tracker, Global Tracker, Vortex Tracker II's text files
// and VTX register dumps. Each is read and played by its replay routine from ZXTune, on ZXTune's
// emulation of the AY-3-8910. Chip Tracker plays samples instead, on ZXTune's sample engine.
//
// The app drives ZXTune's chip itself, frame by frame, with the registers the routine writes: so it can
// silence a voice (ZXTune's own mute only stops its tone generator, which leaves a constant level) and
// draw the voices' levels and waveforms from the same registers. Pro Tracker 3 files for two chips
// (TurboSound) and Chip Tracker's samples play through ZXTune's renderer, without channels.
#include "engine.h"
#include "psg_view.h"

#include <algorithm>
#include <cctype>
#include <cstring>
#include <memory>
#include <cmath>
#include <map>
#include <optional>
#include <string>
#include <vector>

#include "binary/container_factories.h"
#include "core/parameters.h"
#include "formats/chiptune/aym/protracker3.h"
#include "module/attributes.h"
#include "module/players/aym/all.h"
#include "module/players/aym/aym_base.h"
#include "module/players/aym/aym_factory.h"
#include "module/players/aym/aym_parameters.h"
#include "module/players/aym/protracker3.h"
#include "module/players/dac/all.h"
#include "module/players/dac/dac_factory.h"
#include "parameters/container.h"
#include "parameters/merged_accessor.h"

namespace {

constexpr int VOICES = PsgView::VOICES;

std::string extensionOf(const std::string &path) {
    size_t slash = path.find_last_of('/');
    std::string name = slash == std::string::npos ? path : path.substr(slash + 1);
    size_t dot = name.rfind('.');
    if (dot == std::string::npos) return {};
    std::string ext = name.substr(dot + 1);
    std::transform(ext.begin(), ext.end(), ext.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return ext;
}

void join(std::string &text, const std::string &label, const std::string &value) {
    if (value.empty()) return;
    if (!text.empty()) text += "\n";
    text += label.empty() ? value : label + ": " + value;
}

using Module::AYM::ChiptuneCreator;

struct Replayer {
    // Pro Tracker 3 has a factory of its own: its files may hold music for two chips.
    Formats::Chiptune::ProTracker3::Parser pt3 = nullptr;
    ChiptuneCreator create = nullptr;
    // Chip Tracker plays samples through the Spectrum's beeper port or a DAC, not on the AY chip.
    Module::DAC::Factory dac = nullptr;
};

// The replay routines to try for a file, the one its extension names first. Modland's extensions:
// ".st13" is Sound Tracker 1.3 and ".zxs" Sound Tracker compiled with its player, both read as STC.
std::vector<Replayer> replayersFor(const std::string &ext) {
    using namespace Module::AYM;
    if (ext == "pt3") return {{Formats::Chiptune::ProTracker3::Parse, nullptr}};
    // Vortex Tracker II's text files, made readable for ZXTune by vt2Text().
    if (ext == "vt2") return {{Formats::Chiptune::ProTracker3::VortexTracker2::Parse, nullptr}};
    // Sound Tracker 1.1 modules, inside AY_Emul's "ZXAY" container (see zxayOffsets()).
    if (ext == "st11") return {{nullptr, CreateSoundTrackerChiptune}};
    if (ext == "chi") return {{nullptr, nullptr, Module::DAC::CreateChipTrackerChiptune}};
    if (ext == "pt2") return {{nullptr, CreateProTracker2Chiptune}};
    if (ext == "pt1") return {{nullptr, CreateProTracker1Chiptune}};
    if (ext == "stc" || ext == "st13" || ext == "zxs") {
        return {{nullptr, CreateSoundTrackerCompiledChiptune}, {nullptr, CreateSoundTracker3Chiptune}, {nullptr, CreateSoundTrackerChiptune}};
    }
    if (ext == "asc") return {{nullptr, CreateASCSoundMasterChiptune}, {nullptr, CreateASCSoundMaster0Chiptune}};
    if (ext == "sqt") return {{nullptr, CreateSQTrackerChiptune}};
    if (ext == "stp" || ext == "stp2") return {{nullptr, CreateSoundTrackerProChiptune}};
    if (ext == "psc") return {{nullptr, CreateProSoundCreatorChiptune}};
    if (ext == "psm") return {{nullptr, CreateProSoundMakerChiptune}};
    if (ext == "ftc") return {{nullptr, CreateFastTrackerChiptune}};
    if (ext == "gtr") return {{nullptr, CreateGlobalTrackerChiptune}};
    if (ext == "vtx") return {{nullptr, CreateVTXChiptune}};
    return {};
}

// ZXTune throws when data does not fit the format, with its own error type as well as std::exception.
Module::Holder::Ptr load(const Replayer &replayer, const Binary::Container &data, Parameters::Container::Ptr &properties) {
    try {
        properties = Parameters::Container::Create();
        auto none = Parameters::Container::Create();
        auto factory = replayer.pt3 != nullptr ? Module::ProTracker3::CreateFactory(replayer.pt3)
                       : replayer.dac != nullptr ? Module::DAC::CreateModuleFactory(replayer.dac)
                                                 : Module::AYM::CreateModuleFactory(replayer.create);
        return factory->CreateModule(*none, data, properties);
    } catch (...) {
        return {};
    }
}

// Where the module in an AY_Emul "ZXAY" file may start. Its header points (big-endian, relative to where
// each pointer is stored) from byte 18 to the song table, and from there to the song's data, which
// begins with 8 bytes of settings before the module itself.
std::vector<size_t> zxayOffsets(const unsigned char *bytes, size_t length) {
    if (length < 24 || std::memcmp(bytes, "ZXAY", 4) != 0) return {};
    auto pointer = [&](size_t at) -> long {
        if (at + 2 > length) return -1;
        auto offset = static_cast<int16_t>(bytes[at] << 8 | bytes[at + 1]);
        return static_cast<long>(at) + offset;
    };
    long songs = pointer(18);
    long song = songs >= 0 ? pointer(static_cast<size_t>(songs) + 2) : -1;
    if (song < 0 || static_cast<size_t>(song) >= length) return {};
    std::vector<size_t> offsets = {static_cast<size_t>(song) + 8};
    // In case the settings are longer in some file.
    for (size_t at = static_cast<size_t>(song); at < static_cast<size_t>(song) + 32 && at < length; at++) {
        if (at != offsets.front()) offsets.push_back(at);
    }
    return offsets;
}

// Vortex Tracker II 2.5 and later write text that ZXTune's reader does not take: two blank lines after
// the header, an [Info] block, and "." for a switched-off flag in samples where older versions wrote it
// in lower case ("T.." for "Tne"). This writes it the old way.
std::string vt2Text(const char *data, size_t length) {
    std::string out;
    out.reserve(length);
    bool inSample = false, inInfo = false, blank = false;
    size_t start = 0;
    while (start < length) {
        size_t end = start;
        while (end < length && data[end] != '\n') end++;
        std::string line(data + start, end - start);
        start = end + 1;
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line == "[Info]") inInfo = true;
        if (inInfo) {
            if (line == "[/Info]") inInfo = false;
            continue;
        }
        if (line.empty()) {
            if (blank) continue;
            blank = true;
        } else {
            blank = false;
        }
        if (!line.empty() && line[0] == '[') {
            inSample = line.rfind("[Sample", 0) == 0;
        } else if (inSample && line.size() >= 3) {
            static const char OFF[] = "tne";
            for (size_t i = 0; i < 3; i++) {
                if (line[i] == '.') line[i] = OFF[i];
            }
        }
        out += line;
        out += "\n";
    }
    return out;
}

class ZxPlayer : public Player {
public:
    ZxPlayer(Module::Holder::Ptr holder, Parameters::Accessor::Ptr properties)
        : holder(std::move(holder)), properties(std::move(properties)) {
        ready = open(48000);
    }

    bool ok() const { return ready; }

    size_t read(int sampleRate, size_t frames, float *out) override {
        if (!ready) return 0;
        try {
            if (sampleRate != rate) {
                double at = position();
                if (!open(sampleRate)) return 0;
                seekTo(at);
            }
            size_t produced = 0;
            while (produced < frames) {
                if (pendingAt >= pending.size() && !renderFrame()) break;
                size_t count = std::min(frames - produced, (pending.size() - pendingAt) / 2);
                std::memcpy(out + produced * 2, pending.data() + pendingAt, count * 2 * sizeof(float));
                pendingAt += count * 2;
                produced += count;
            }
            played += produced;
            return produced;
        } catch (...) {
            ended = true;
            return 0;
        }
    }

    double duration() override { return lengthMs / 1000.0; }
    double position() override { return rate > 0 ? static_cast<double>(played) / rate : 0.0; }

    void seek(double seconds) override {
        if (!ready) return;
        try {
            seekTo(seconds);
        } catch (...) {
            ended = true;
        }
    }

    void setStereoSeparation(int) override {}
    int channelCount() override { return ready && tune ? VOICES : 0; }

    void setChannelMute(int channel, bool mute) override {
        if (!ready || channel < 0 || channel >= VOICES || muted[channel] == mute) return;
        muted[channel] = mute;
        // An unmuted voice gets its volume back with the next frame, even if the music does not set it again.
        if (!mute) restore[channel] = true;
    }

    bool canMute() override { return tune != nullptr; }

    std::string metadata(const std::string &key) override {
        if (!ready) return {};
        auto text = [this](Parameters::Identifier name) { return Parameters::GetString(*properties, name); };
        std::string program = text(Module::ATTR_PROGRAM);
        if (key == "title") return text(Module::ATTR_TITLE);
        if (key == "artist") return text(Module::ATTR_AUTHOR);
        if (key == "type_long") return program.empty() ? "ZX Spectrum" : "ZX Spectrum (" + program + ")";
        if (key == "tracker") return program;
        if (key == "message_raw") {
            std::string message;
            join(message, "Author", text(Module::ATTR_AUTHOR));
            join(message, "Program", program);
            join(message, "", text(Module::ATTR_COMMENT));
            join(message, "", text(Module::ATTR_STRINGS));
            return message;
        }
        return {};
    }

    std::string names(int) override { return {}; }
    float vu(int channel) override { return chip.vu(channel); }

    void playPosition(int &order, int &pattern, int &row) override {
        order = pattern = row = 0;
        auto track = trackState();
        if (track) {
            order = static_cast<int>(track->Position);
            pattern = static_cast<int>(track->Pattern);
            row = static_cast<int>(track->Line);
        }
    }

    void tempo(int &speed, int &bpm) override {
        bpm = 0;
        auto track = trackState();
        speed = track ? static_cast<int>(track->Tempo) : 0;
    }

    int orderCount() override { return positions; }
    // The rows of a pattern as the module played them (see scanPatterns()).
    std::string patternText(int pattern) override {
        auto found = patterns.find(pattern);
        // Pattern 0 is how the app asks whether there are patterns at all; not every module plays one.
        if (found == patterns.end() && pattern == 0 && !patterns.empty()) found = patterns.begin();
        if (found == patterns.end()) return {};
        std::string text;
        for (const auto &row : found->second) {
            if (!text.empty()) text += '\n';
            text += row;
        }
        return text;
    }
    bool ownScopes() override { return ready && tune != nullptr; }
    void scopes(int points, float *out) override { chip.scopes(points, out); }

private:
    bool open(int sampleRate) {
        try {
            // The module's own settings (its note table, the chip's clock) apply where the app sets nothing.
            params = Parameters::CreateMergedAccessor(Parameters::Container::Create(), properties);
            auto info = holder->GetModuleInformation();
            lengthMs = static_cast<double>(info.Duration.Get());
            positions = info.Track ? static_cast<int>(info.Track->PositionsCount) : 0;
            tune.reset();
            frames.reset();
            device.reset();
            renderer.reset();
            if (auto aym = std::dynamic_pointer_cast<const Module::AYM::Holder>(holder)) {
                // One chip: driven here, so that muted voices can be taken out of what reaches it.
                tune = aym->GetChiptune();
                frames = tune->CreateDataIterator(Module::AYM::TrackParameters::Create(params));
                device = Module::AYM::CreateChip(static_cast<uint_t>(sampleRate), params);
                frameDuration = tune->GetFrameDuration();
            } else {
                // Two chips (TurboSound): ZXTune's own renderer, without channels.
                renderer = holder->CreateRenderer(static_cast<uint_t>(sampleRate), params);
            }
            using Parameters::ZXTune::Core::AYM::Clockrate;
            clock = static_cast<double>(Parameters::GetInteger(*params, Clockrate::Key, Clockrate::Default));
            chip.setClock(clock);
            if (tune && patterns.empty()) scanPatterns();
            rate = sampleRate;
            restart();
            return true;
        } catch (...) {
            return false;
        }
    }

    /**
     * ZXTune keeps each tracker's notes in its own form and does not hand them out. So the module is run
     * once without sound, and for every row the three voices' registers are written down as a tracker
     * would show them: the note when one is struck, its volume (or "E" and the shape when the envelope
     * plays it) and the noise period. Sample and ornament numbers are not known.
     * Each cell is "note noise effect volume" in libopenmpt's layout: "C-4 1F E0E v0F".
     */
    void scanPatterns() {
        using Devices::AYM::Registers;
        auto rows = tune->CreateDataIterator(Module::AYM::TrackParameters::Create(params));
        uint8_t regs[16] = {};
        regs[Registers::MIXER] = 0x3f;
        int lastPeriod[VOICES] = {};
        int lastVolume[VOICES] = {};
        std::optional<std::pair<unsigned, unsigned>> lastRow;
        // A quarter of an hour at 50 frames a second is longer than any module's first run.
        for (int frame = 0; frame < 50 * 60 * 15; frame++) {
            auto state = rows->GetState();
            if (state.LoopCount > 0) break;
            auto changed = rows->Advance();
            for (Registers::IndicesIterator it(changed); it; ++it) regs[*it] = changed[*it];
            if (!state.Track) return;
            std::pair<unsigned, unsigned> row(state.Track->Pattern, state.Track->Line);
            if (lastRow == row) continue;
            lastRow = row;
            std::string line;
            for (int v = 0; v < VOICES; v++) {
                if (v > 0) line += '|';
                line += cell(regs, v, lastPeriod[v], lastVolume[v]);
            }
            auto &pattern = patterns[static_cast<int>(row.first)];
            if (pattern.size() <= row.second) pattern.resize(row.second + 1, "... .. ... ...|... .. ... ...|... .. ... ...");
            // A pattern played again keeps what was written the first time.
            if (pattern[row.second].find_first_not_of(".| ") == std::string::npos) pattern[row.second] = line;
        }
    }

    std::string cell(const uint8_t *r, int v, int &lastPeriod, int &lastVolume) const {
        static const char *const NAMES[] = {"C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-"};
        static const char HEX[] = "0123456789ABCDEF";
        bool tone = (r[7] & (1 << v)) == 0;
        bool noise = (r[7] & (8 << v)) == 0;
        int level = r[8 + v] & 0x1f;
        bool envelope = (level & 0x10) != 0;
        int volume = envelope ? 15 : level & 0x0f;
        int period = r[v * 2] | ((r[v * 2 + 1] & 0x0f) << 8);
        bool sounding = (tone || noise) && volume > 0;
        // A note shows where one is struck: a new pitch, or a voice that was silent.
        std::string note = "...";
        if (sounding && tone && period > 0 && (period != lastPeriod || lastVolume == 0)) {
            double hz = clock / (16.0 * period);
            int n = static_cast<int>(std::lround(12.0 * std::log2(hz / 440.0))) + 57;
            if (n >= 0 && n < 120) note = std::string(NAMES[n % 12]) + static_cast<char>('0' + n / 12);
        }
        lastPeriod = tone ? period : 0;
        lastVolume = sounding ? volume : 0;
        std::string noiseText = noise && sounding ? std::string{HEX[(r[6] >> 4) & 1], HEX[r[6] & 0x0f]} : "..";
        std::string effect = envelope && sounding ? std::string{'E', '0', HEX[r[13] & 0x0f]} : "...";
        std::string vol = sounding ? std::string{'v', '0', HEX[volume]} : "...";
        return note + " " + noiseText + " " + effect + " " + vol;
    }

    void restart() {
        std::memset(registers, 0, sizeof(registers));
        registers[Devices::AYM::Registers::MIXER] = 0x3f;
        for (bool &r : restore) r = false;
        stamp = {};
        chip.reset();
        pending.clear();
        pendingAt = 0;
        played = 0;
        ended = false;
    }

    std::optional<Module::TrackState> trackState() {
        if (!ready) return {};
        try {
            if (frames) return frames->GetState().Track;
            if (renderer) return renderer->GetState().Track;
        } catch (...) {
        }
        return {};
    }

    // The registers of the next frame, with muted voices silenced.
    Devices::AYM::Registers nextFrame() {
        using Devices::AYM::Registers;
        Registers regs = frames->Advance();
        for (Registers::IndicesIterator it(regs); it; ++it) registers[*it] = regs[*it];
        for (int v = 0; v < VOICES; v++) {
            auto volume = static_cast<Registers::Index>(Registers::VOLA + v);
            if (muted[v]) {
                regs[volume] = 0;
            } else if (restore[v]) {
                regs[volume] = registers[volume];
                restore[v] = false;
            }
        }
        return regs;
    }

    // One frame of the module: a fiftieth of a second for most, as the Spectrum's interrupt ran the music.
    bool renderFrame() {
        if (ended) return false;
        Sound::Chunk chunk;
        if (frames) {
            // A module that has come round to its loop point is over.
            if (frames->GetState().LoopCount > 0) {
                ended = true;
                return false;
            }
            Devices::AYM::DataChunk data;
            data.TimeStamp = stamp;
            data.Data = nextFrame();
            device->RenderData(data);
            stamp += frameDuration;
            chunk = device->RenderTill(stamp);
            chip.update(registers, frameDuration.Get() / 1e6, muted);
        } else {
            if (renderer->GetState().LoopCount > 0) {
                ended = true;
                return false;
            }
            chunk = renderer->Render();
        }
        pending.resize(chunk.size() * 2);
        for (size_t i = 0; i < chunk.size(); i++) {
            pending[i * 2] = chunk[i].Left() / 32768.0f;
            pending[i * 2 + 1] = chunk[i].Right() / 32768.0f;
        }
        pendingAt = 0;
        if (chunk.empty()) ended = true;
        return !chunk.empty();
    }

    void seekTo(double seconds) {
        seconds = std::min(std::max(seconds, 0.0), lengthMs / 1000.0);
        if (frames) {
            // Runs the routine up to the target, setting the chip's registers without rendering sound.
            frames->Reset();
            device->Reset();
            restart();
            auto target = static_cast<uint64_t>(seconds * 1e6 / std::max<double>(1.0, frameDuration.Get()));
            for (uint64_t f = 0; f < target && frames->GetState().LoopCount == 0; f++) {
                Devices::AYM::DataChunk data;
                data.Data = nextFrame();
                stamp += frameDuration;
                data.TimeStamp = stamp;
                device->UpdateState(data);
            }
        } else {
            renderer->SetPosition(Time::AtMillisecond(static_cast<uint_t>(seconds * 1000.0)));
            restart();
        }
        played = static_cast<uint64_t>(seconds * rate);
    }

    Module::Holder::Ptr holder;
    Parameters::Accessor::Ptr properties;
    Parameters::Accessor::Ptr params;
    // One chip.
    Module::AYM::Chiptune::Ptr tune;
    Module::AYM::DataIterator::Ptr frames;
    Devices::AYM::Chip::Ptr device;
    Time::Microseconds frameDuration = Module::AYM::BASE_FRAME_DURATION;
    Devices::AYM::Stamp stamp;
    // Two chips.
    Module::Renderer::Ptr renderer;
    bool ready = false;
    bool ended = false;
    int rate = 0;
    double lengthMs = 0.0;
    int positions = 0;
    uint64_t played = 0;
    std::vector<float> pending;
    size_t pendingAt = 0;
    // What the music last wrote to each register, mutes left out.
    uint8_t registers[16] = {};
    bool muted[VOICES] = {};
    bool restore[VOICES] = {};
    PsgView chip{1750000.0};
    double clock = 1750000.0;
    // Pattern number to its rows, each row the three cells separated by '|'.
    std::map<int, std::vector<std::string>> patterns;
};

} // namespace

bool isZxData(const void *, size_t, const std::string &path) { return !replayersFor(extensionOf(path)).empty(); }

Player *createZxPlayer(const void *data, size_t length, const std::string &path) {
    std::string ext = extensionOf(path);
    const auto *bytes = static_cast<const char *>(data);
    // What is handed to ZXTune: the file, or the module inside it at each place it may start.
    std::vector<Binary::Container::Ptr> candidates;
    try {
        if (ext == "vt2") {
            std::string text = vt2Text(bytes, length);
            candidates.push_back(Binary::CreateContainer(Binary::Dump(text.begin(), text.end())));
        } else if (ext == "st11") {
            for (size_t at : zxayOffsets(reinterpret_cast<const unsigned char *>(bytes), length)) {
                candidates.push_back(Binary::CreateContainer(Binary::View(bytes + at, length - at)));
            }
        } else {
            candidates.push_back(Binary::CreateContainer(Binary::View(data, length)));
        }
    } catch (...) {
        return nullptr;
    }
    for (const auto &container : candidates) {
        if (container == nullptr) continue;
        for (const auto &replayer : replayersFor(ext)) {
            Parameters::Container::Ptr properties;
            if (auto holder = load(replayer, *container, properties)) {
                auto player = std::make_unique<ZxPlayer>(holder, holder->GetModuleProperties());
                if (player->ok()) return player.release();
            }
        }
    }
    return nullptr;
}
