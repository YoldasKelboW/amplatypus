// SPDX-License-Identifier: GPL-3.0-or-later
//
// What the JNI layer needs from a replayer. One implementation per bundled replay library.
#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

class Player {
public:
    virtual ~Player() = default;
    // Renders up to `frames` interleaved stereo float frames. Returns 0 at the end of the song.
    virtual size_t read(int sampleRate, size_t frames, float *out) = 0;
    virtual double duration() = 0;
    virtual double position() = 0;
    virtual void seek(double seconds) = 0;
    // 0-100.
    virtual void setStereoSeparation(int percent) = 0;
    virtual int channelCount() = 0;
    virtual void setChannelMute(int channel, bool mute) = 0;
    // UTF-8. Keys are libopenmpt's: title, type_long, tracker, message_raw.
    virtual std::string metadata(const std::string &key) = 0;
    // Sample names (kind 0) or instrument names (kind 1), one per line.
    virtual std::string names(int kind) = 0;
    // Level 0..1 of a channel for what was rendered last.
    virtual float vu(int channel) = 0;
    virtual void playPosition(int &order, int &pattern, int &row) = 0;
    // Rows separated by '\n', channels by '|', each cell "C-5 01 A0F".
    virtual std::string patternText(int pattern) = 0;
};

// The waveform of every channel on its own, rendered in step with a Player.
class ScopeSet {
public:
    virtual ~ScopeSet() = default;
    virtual size_t channels() = 0;
    virtual void seek(double seconds) = 0;
    // Advances by `frames` and writes `points` samples per channel to `out` (channel after channel).
    virtual void render(int sampleRate, int frames, float *out, int points) = 0;
};

// Each returns nullptr if the data is not for that replayer or cannot be loaded.
Player *createOpenMptPlayer(const void *data, size_t length);
ScopeSet *createOpenMptScopes(const void *data, size_t length, int channels);
Player *createHivelyPlayer(const void *data, size_t length);
ScopeSet *createHivelyScopes(const void *data, size_t length);
bool isHivelyData(const void *data, size_t length);
