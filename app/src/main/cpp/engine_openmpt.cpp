// SPDX-License-Identifier: GPL-3.0-or-later
//
// libopenmpt behind the Player interface. The call sequence follows MPTReplayer.m of
// 4champ (Copyright (c) Aleksi Sitomaniemi, used under the MIT license).
#include "engine.h"

#include <libopenmpt/libopenmpt.h>
#include <libopenmpt/libopenmpt_ext.h>
#include <vector>

namespace {

std::string take(const char *value) {
    if (value == nullptr) return {};
    std::string text(value);
    openmpt_free_string(value);
    return text;
}

openmpt_module_ext *open(const void *data, size_t length) {
    return openmpt_module_ext_create_from_memory(data, length, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr);
}

void seekModule(openmpt_module *m, double seconds) {
    if (seconds <= 0.0) {
        openmpt_module_set_position_order_row(m, 0, 0);
    } else {
        openmpt_module_set_position_seconds(m, seconds);
    }
}

// The ext module is needed for muting single channels; the plain module used by all other calls
// lives inside it.
class OpenMptPlayer : public Player {
public:
    explicit OpenMptPlayer(openmpt_module_ext *ext) : ext(ext), m(openmpt_module_ext_get_module(ext)) {}
    ~OpenMptPlayer() override { openmpt_module_ext_destroy(ext); }

    size_t read(int sampleRate, size_t frames, float *out) override {
        return openmpt_module_read_interleaved_float_stereo(m, sampleRate, frames, out);
    }
    double duration() override { return openmpt_module_get_duration_seconds(m); }
    double position() override { return openmpt_module_get_position_seconds(m); }
    void seek(double seconds) override { seekModule(m, seconds); }
    void setStereoSeparation(int percent) override {
        // App range is 0-100, libopenmpt's is 0-200.
        openmpt_module_set_render_param(m, OPENMPT_MODULE_RENDER_STEREOSEPARATION_PERCENT, percent * 2);
    }
    int channelCount() override { return openmpt_module_get_num_channels(m); }

    void setChannelMute(int channel, bool mute) override {
        openmpt_module_ext_interface_interactive interactive;
        if (openmpt_module_ext_get_interface(ext, LIBOPENMPT_EXT_C_INTERFACE_INTERACTIVE, &interactive, sizeof(interactive)) != 0) {
            // The channel's volume is set to zero instead of using set_channel_mute_status: a muted channel is
            // not rendered at all, so its note stops moving and resumes out of time when it is switched back
            // on. At zero volume it keeps playing inaudibly and comes back exactly in step.
            interactive.set_channel_volume(ext, channel, mute ? 0.0 : 1.0);
        }
    }

    std::string metadata(const std::string &key) override { return take(openmpt_module_get_metadata(m, key.c_str())); }

    std::string names(int kind) override {
        std::string all;
        int32_t count = kind == 0 ? openmpt_module_get_num_samples(m) : openmpt_module_get_num_instruments(m);
        for (int32_t i = 0; i < count; i++) {
            std::string name = take(kind == 0 ? openmpt_module_get_sample_name(m, i) : openmpt_module_get_instrument_name(m, i));
            for (char &c : name) if (c == '\n' || c == '\r') c = ' ';
            all += name;
            all += '\n';
        }
        return all;
    }

    float vu(int channel) override { return openmpt_module_get_current_channel_vu_mono(m, channel); }

    void playPosition(int &order, int &pattern, int &row) override {
        order = openmpt_module_get_current_order(m);
        pattern = openmpt_module_get_current_pattern(m);
        row = openmpt_module_get_current_row(m);
    }

    void tempo(int &speed, int &bpm) override {
        speed = openmpt_module_get_current_speed(m);
        bpm = static_cast<int>(openmpt_module_get_current_tempo2(m) + 0.5);
    }

    int orderCount() override { return openmpt_module_get_num_orders(m); }

    std::string patternText(int pattern) override {
        int32_t rows = openmpt_module_get_pattern_num_rows(m, pattern);
        int32_t channels = openmpt_module_get_num_channels(m);
        std::string text;
        text.reserve(static_cast<size_t>(rows) * channels * 11);
        for (int32_t r = 0; r < rows; r++) {
            for (int32_t c = 0; c < channels; c++) {
                if (c > 0) text += '|';
                text += take(openmpt_module_format_pattern_row_channel_command(m, pattern, r, c, OPENMPT_MODULE_COMMAND_NOTE));
                text += ' ';
                text += take(openmpt_module_format_pattern_row_channel_command(m, pattern, r, c, OPENMPT_MODULE_COMMAND_INSTRUMENT));
                text += ' ';
                text += take(openmpt_module_format_pattern_row_channel_command(m, pattern, r, c, OPENMPT_MODULE_COMMAND_EFFECT));
                text += take(openmpt_module_format_pattern_row_channel_command(m, pattern, r, c, OPENMPT_MODULE_COMMAND_PARAMETER));
            }
            text += '\n';
        }
        return text;
    }

private:
    openmpt_module_ext *ext;
    openmpt_module *m;
};

// libopenmpt cannot hand out the audio of a single channel. For the scopes the module is therefore
// loaded once more per channel with every other channel muted, and these copies are rendered in
// step with the audible one.
class OpenMptScopes : public ScopeSet {
public:
    ~OpenMptScopes() override {
        for (openmpt_module_ext *ext : modules) openmpt_module_ext_destroy(ext);
    }

    bool load(const void *data, size_t length, int channels) {
        for (int c = 0; c < channels; c++) {
            openmpt_module_ext *ext = open(data, length);
            if (ext == nullptr) return false;
            modules.push_back(ext);
            openmpt_module_ext_interface_interactive interactive;
            if (openmpt_module_ext_get_interface(ext, LIBOPENMPT_EXT_C_INTERFACE_INTERACTIVE, &interactive, sizeof(interactive)) == 0) {
                return false;
            }
            for (int other = 0; other < channels; other++) interactive.set_channel_mute_status(ext, other, other != c);
            // The cheapest settings are enough for a picture.
            openmpt_module *m = openmpt_module_ext_get_module(ext);
            openmpt_module_set_render_param(m, OPENMPT_MODULE_RENDER_INTERPOLATIONFILTER_LENGTH, 1);
            openmpt_module_set_render_param(m, OPENMPT_MODULE_RENDER_VOLUMERAMPING_STRENGTH, 0);
        }
        return true;
    }

    size_t channels() override { return modules.size(); }

    void seek(double seconds) override {
        for (openmpt_module_ext *ext : modules) seekModule(openmpt_module_ext_get_module(ext), seconds);
    }

    void render(int sampleRate, int frames, float *out, int points) override {
        scratch.resize(static_cast<size_t>(frames));
        int step = frames / points;
        if (step < 1) step = 1;
        for (size_t c = 0; c < modules.size(); c++) {
            openmpt_module *m = openmpt_module_ext_get_module(modules[c]);
            size_t rendered = openmpt_module_read_float_mono(m, sampleRate, static_cast<size_t>(frames), scratch.data());
            for (int i = 0; i < points; i++) {
                size_t index = static_cast<size_t>(i) * step;
                out[c * points + i] = index < rendered ? scratch[index] : 0.0f;
            }
        }
    }

private:
    std::vector<openmpt_module_ext *> modules;
    std::vector<float> scratch;
};

} // namespace

Player *createOpenMptPlayer(const void *data, size_t length) {
    openmpt_module_ext *ext = open(data, length);
    return ext == nullptr ? nullptr : new OpenMptPlayer(ext);
}

ScopeSet *createOpenMptScopes(const void *data, size_t length, int channels) {
    auto *scopes = new OpenMptScopes();
    if (!scopes->load(data, length, channels)) {
        delete scopes;
        return nullptr;
    }
    return scopes;
}
