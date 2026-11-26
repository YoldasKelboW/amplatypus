// SPDX-License-Identifier: GPL-3.0-or-later
//
// ZX Spectrum tracker music: Pro Tracker 1-3, Sound Tracker and its compilers, ASC Sound Master,
// SQ-Tracker, Pro Sound Creator and Maker, Fast Tracker, Global Tracker and VTX register dumps. Each
// is read and played by its replay routine from ZXTune, on ZXTune's emulation of the AY-3-8910.
//
// The app drives ZXTune's chip itself, frame by frame, with the registers the routine writes: so it can
// silence a voice (ZXTune's own mute only stops its tone generator, which leaves a constant level) and
// draw the voices' levels and waveforms from the same registers. Pro Tracker 3 files for two chips
// (TurboSound) play through ZXTune's renderer, without channels.
#include "engine.h"
#include "psg_view.h"

#include <algorithm>
#include <cctype>
#include <cstring>
#include <memory>
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
};

// The replay routines to try for a file, the one its extension names first. Modland's extensions:
// ".st13" is Sound Tracker 1.3 and ".zxs" Sound Tracker compiled with its player, both read as STC.
std::vector<Replayer> replayersFor(const std::string &ext) {
    using namespace Module::AYM;
    if (ext == "pt3") return {{Formats::Chiptune::ProTracker3::Parse, nullptr}};
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
                                               : Module::AYM::CreateModuleFactory(replayer.create);
        return factory->CreateModule(*none, data, properties);
    } catch (...) {
        return {};
    }
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
    std::string patternText(int) override { return {}; }
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
            chip.setClock(static_cast<double>(Parameters::GetInteger(*params, Clockrate::Key, Clockrate::Default)));
            rate = sampleRate;
            restart();
            return true;
        } catch (...) {
            return false;
        }
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
};

} // namespace

bool isZxData(const void *, size_t, const std::string &path) { return !replayersFor(extensionOf(path)).empty(); }

Player *createZxPlayer(const void *data, size_t length, const std::string &path) {
    std::shared_ptr<const Binary::Container> container;
    try {
        container = Binary::CreateContainer(Binary::View(data, length));
    } catch (...) {
        return nullptr;
    }
    if (container == nullptr) return nullptr;
    for (const auto &replayer : replayersFor(extensionOf(path))) {
        Parameters::Container::Ptr properties;
        if (auto holder = load(replayer, *container, properties)) {
            auto player = std::make_unique<ZxPlayer>(holder, holder->GetModuleProperties());
            if (player->ok()) return player.release();
        }
    }
    return nullptr;
}
