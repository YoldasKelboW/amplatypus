// SPDX-License-Identifier: GPL-3.0-or-later
//
// Console music ripped as "xSF" files: Game Boy Advance (GSF, by mGBA), Nintendo DS (2SF, by vio2sf),
// Nintendo 64 (USF, by lazyusf2), Sega Saturn and Dreamcast (SSF and DSF, by Highly Theoretical). Each
// file holds the game's own sound driver and data, run in an emulation of the console's sound hardware.
// ZXTune reads the files and drives the emulators.
//
// Most songs are "mini" files of a few hundred bytes that name a library file of the game ("_lib" tag),
// which may name further ones. They are read from the song's folder, where the app downloads them.
//
// PlayStation files (PSF) are not played: their emulator needs Sony's BIOS, which cannot be shipped.
#include "engine.h"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <cstring>
#include <dirent.h>
#include <fstream>
#include <iterator>
#include <memory>
#include <string>
#include <vector>

#include "binary/container_factories.h"
#include "module/additional_files.h"
#include "module/attributes.h"
#include "module/players/xsf/all.h"
#include "module/players/xsf/xsf_factory.h"
#include "parameters/container.h"
#include "parameters/merged_accessor.h"

namespace {

// How many rounds of library files to follow; real files need one or two.
constexpr int MAX_LIBRARY_DEPTH = 8;
// The emulators cannot jump: a seek runs them silently up to the target, which on a phone can take a
// minute for a Dreamcast song. It is done in slices of this much time per read, while silence plays.
constexpr double SEEK_SLICE_SECONDS = 0.02;

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

struct Kind {
    Module::XSF::Factory create;
    const char *system;
    const char *format;
};

bool kindOf(const std::string &ext, Kind &kind) {
    using namespace Module::XSF;
    if (ext == "gsf" || ext == "minigsf") kind = {CreateGSFModule, "Game Boy Advance", "GSF"};
    else if (ext == "2sf" || ext == "mini2sf") kind = {Create2SFModule, "Nintendo DS", "2SF"};
    else if (ext == "usf" || ext == "miniusf") kind = {CreateUSFModule, "Nintendo 64", "USF"};
    else if (ext == "ssf" || ext == "minissf") kind = {CreateSDSFModule, "Sega Saturn", "SSF"};
    else if (ext == "dsf" || ext == "minidsf") kind = {CreateSDSFModule, "Sega Dreamcast", "DSF"};
    else return false;
    return true;
}

// Reads `name` from `dir`. Tags and file names on disk may differ in case; the first match counts.
Binary::Container::Ptr readBeside(const std::string &dir, const std::string &name) {
    std::string path = dir + "/" + name;
    std::ifstream exact(path, std::ios::binary);
    if (!exact) {
        path.clear();
        if (DIR *folder = opendir(dir.c_str())) {
            std::string wanted = lower(name);
            while (dirent *entry = readdir(folder)) {
                if (lower(entry->d_name) == wanted) {
                    path = dir + "/" + entry->d_name;
                    break;
                }
            }
            closedir(folder);
        }
        if (path.empty()) return {};
    }
    std::ifstream file(path, std::ios::binary);
    if (!file) return {};
    Binary::Dump bytes((std::istreambuf_iterator<char>(file)), {});
    if (bytes.empty()) return {};
    return Binary::CreateContainer(std::move(bytes));
}

class XsfPlayer : public Player {
public:
    XsfPlayer(Module::Holder::Ptr holder, Kind kind) : holder(std::move(holder)), kind(kind) {
        try {
            properties = this->holder->GetModuleProperties();
            lengthMs = static_cast<double>(this->holder->GetModuleInformation().Duration.Get());
            ready = open(48000);
        } catch (...) {
            ready = false;
        }
    }

    bool ok() const { return ready; }

    size_t read(int sampleRate, size_t frames, float *out) override {
        if (!ready) return 0;
        try {
            if (sampleRate != rate) {
                double at = position();
                if (!open(sampleRate)) return 0;
                seek(at);
            }
            if (seeking) {
                std::fill(out, out + frames * 2, 0.0f);
                skipAhead();
                return frames;
            }
            size_t produced = 0;
            while (produced < frames) {
                if (pendingAt >= pending.size() && !renderMore()) break;
                size_t count = std::min(frames - produced, (pending.size() - pendingAt) / 2);
                std::memcpy(out + produced * 2, pending.data() + pendingAt, count * 2 * sizeof(float));
                pendingAt += count * 2;
                produced += count;
            }
            played += produced;
            emulated += produced;
            return produced;
        } catch (...) {
            ended = true;
            return 0;
        }
    }

    double duration() override { return lengthMs / 1000.0; }
    // Where the music is: while seeking, how far the emulator has got.
    double position() override { return rate > 0 ? static_cast<double>(emulated) / rate : 0.0; }

    void seek(double seconds) override {
        if (!ready) return;
        try {
            seconds = std::min(std::max(seconds, 0.0), lengthMs / 1000.0);
            // Backwards means from the start again.
            if (seconds * rate < emulated && !open(rate)) return;
            target = static_cast<uint64_t>(seconds * rate);
            seeking = target > emulated;
            pending.clear();
            pendingAt = 0;
            ended = false;
        } catch (...) {
            ended = true;
        }
    }

    void setStereoSeparation(int) override {}
    int channelCount() override { return 0; }
    void setChannelMute(int, bool) override {}
    bool canMute() override { return false; }

    std::string metadata(const std::string &key) override {
        if (!ready) return {};
        auto text = [this](Parameters::Identifier name) { return Parameters::GetString(*properties, name); };
        if (key == "title") return text(Module::ATTR_TITLE);
        if (key == "artist") return text(Module::ATTR_AUTHOR);
        if (key == "type_long") return std::string(kind.system) + " (" + kind.format + ")";
        // Where trackers name their tracker: the game the music is from.
        if (key == "tracker") return text(Module::ATTR_PROGRAM);
        if (key == "message_raw") {
            std::string message;
            auto add = [&message](const std::string &label, const std::string &value) {
                if (value.empty()) return;
                if (!message.empty()) message += "\n";
                message += label.empty() ? value : label + ": " + value;
            };
            add("Game", text(Module::ATTR_PROGRAM));
            add("Artist", text(Module::ATTR_AUTHOR));
            add("Year", text(Module::ATTR_DATE));
            add("", text(Module::ATTR_COMMENT));
            return message;
        }
        return {};
    }

    std::string names(int) override { return {}; }
    float vu(int) override { return 0.0f; }
    void playPosition(int &order, int &pattern, int &row) override { order = pattern = row = 0; }
    std::string patternText(int) override { return {}; }

private:
    bool open(int sampleRate) {
        try {
            auto params = Parameters::CreateMergedAccessor(Parameters::Container::Create(), properties);
            renderer = holder->CreateRenderer(static_cast<uint_t>(sampleRate), params);
            rate = sampleRate;
            pending.clear();
            pendingAt = 0;
            played = emulated = target = 0;
            seeking = ended = false;
            return renderer != nullptr;
        } catch (...) {
            return false;
        }
    }

    // Renders on towards the seek target, throwing the sound away, for a slice of time.
    void skipAhead() {
        auto until = std::chrono::steady_clock::now() + std::chrono::duration<double>(SEEK_SLICE_SECONDS);
        while (emulated < target && std::chrono::steady_clock::now() < until) {
            if (renderer->GetState().LoopCount > 0) break;
            Sound::Chunk chunk = renderer->Render();
            if (chunk.empty()) break;
            emulated += chunk.size();
        }
        if (emulated >= target || std::chrono::steady_clock::now() < until) seeking = false;
    }

    bool renderMore() {
        if (ended) return false;
        // The song ends where its tags say (or after the default length), not where ZXTune would loop it.
        if (renderer->GetState().LoopCount > 0 || position() * 1000.0 >= lengthMs) {
            ended = true;
            return false;
        }
        Sound::Chunk chunk = renderer->Render();
        if (chunk.empty()) {
            ended = true;
            return false;
        }
        pending.resize(chunk.size() * 2);
        for (size_t i = 0; i < chunk.size(); i++) {
            pending[i * 2] = chunk[i].Left() / 32768.0f;
            pending[i * 2 + 1] = chunk[i].Right() / 32768.0f;
        }
        pendingAt = 0;
        return true;
    }

    Module::Holder::Ptr holder;
    Kind kind;
    Parameters::Accessor::Ptr properties;
    Module::Renderer::Ptr renderer;
    bool ready = false;
    bool ended = false;
    int rate = 0;
    double lengthMs = 0.0;
    uint64_t played = 0;   // frames handed out
    uint64_t emulated = 0; // frames the emulator has produced, heard or skipped
    uint64_t target = 0;   // where a seek goes
    bool seeking = false;
    std::vector<float> pending;
    size_t pendingAt = 0;
};

} // namespace

bool isXsfData(const void *data, size_t length, const std::string &path) {
    Kind kind;
    // Every xSF file starts with "PSF" and a version byte.
    return length >= 16 && std::memcmp(data, "PSF", 3) == 0 && kindOf(extensionOf(path), kind);
}

Player *createXsfPlayer(const void *data, size_t length, const std::string &path) {
    Kind kind;
    if (!kindOf(extensionOf(path), kind)) return nullptr;
    try {
        auto container = Binary::CreateContainer(Binary::View(data, length));
        auto properties = Parameters::Container::Create();
        auto none = Parameters::Container::Create();
        Module::Holder::Ptr holder = Module::XSF::CreateModuleFactory(kind.create)->CreateModule(*none, *container, properties);
        if (holder == nullptr) return nullptr;
        // The library files the song names, and those they name in turn.
        std::string dir = path.substr(0, path.find_last_of('/'));
        if (auto *files = const_cast<Module::AdditionalFiles *>(dynamic_cast<const Module::AdditionalFiles *>(holder.get()))) {
            for (int round = 0; round < MAX_LIBRARY_DEPTH; round++) {
                auto missing = files->Enumerate();
                if (missing.empty()) break;
                for (const auto &name : missing) {
                    auto library = readBeside(dir, name);
                    if (library == nullptr) return nullptr;
                    files->Resolve(name, library);
                }
            }
            if (!files->Enumerate().empty()) return nullptr;
        }
        auto player = std::make_unique<XsfPlayer>(holder, kind);
        return player->ok() ? player.release() : nullptr;
    } catch (...) {
        return nullptr;
    }
}
