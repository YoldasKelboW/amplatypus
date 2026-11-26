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
    // Rows separated by '\n', channels by '|', each cell "C-5 01 A0F", optionally followed by the
    // volume column (" v40").
    virtual std::string patternText(int pattern) = 0;
    // Whether setChannelMute() works while the song plays.
    virtual bool canMute() { return true; }
    // Some replayers deliver the channel waveforms themselves instead of through a ScopeSet.
    virtual bool ownScopes() { return false; }
    // `points` samples per channel of what the last read() rendered, channel after channel.
    virtual void scopes(int, float *) {}
    // Ticks per row and beats per minute right now; 0 where the format has no such idea.
    virtual void tempo(int &speed, int &bpm) { speed = bpm = 0; }
    // Length of the song in orders (positions); 0 if unknown.
    virtual int orderCount() { return 0; }
    // Songs inside the file (C64 tunes and Amiga game music often hold several), counted from 0.
    virtual int subsongCount() { return 1; }
    virtual int subsong() { return 0; }
    // Starts subsong `index` from its beginning. Returns false if there is no such subsong.
    virtual bool selectSubsong(int) { return false; }
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

// UADE needs to know where its data files and the uadecore program are before it can play.
// `path` is the module file; UADE uses the name's prefix or extension to pick the replay routine.
void setupUade(const std::string &baseDir, const std::string &corePath);
Player *createUadePlayer(const std::string &path);

// Commodore 64: SID files and Compute's Sidplayer songs, by libsidplayfp.
bool isSidFile(const void *data, size_t length, const std::string &path);
Player *createSidPlayer(const std::string &path);

// Console and home computer music (NSF, SPC, GBS, VGM/VGZ, GYM, HES, KSS, SAP, AY), by Game Music Emu.
bool isGmeData(const void *data, size_t length, const std::string &path);
Player *createGmePlayer(const void *data, size_t length, const std::string &path);

// Atari ST music: SNDH and SC68 files by sc68, YM register dumps by StSound.
bool isAtariData(const void *data, size_t length, const std::string &path);
Player *createAtariPlayer(const void *data, size_t length, const std::string &path);

// ZX Spectrum tracker music (Pro Tracker, Sound Tracker, ASC, SQ-Tracker and others, VTX), by ZXTune.
bool isZxData(const void *data, size_t length, const std::string &path);
Player *createZxPlayer(const void *data, size_t length, const std::string &path);

// Console music ripped as xSF files: Game Boy Advance, Nintendo DS, Nintendo 64, Saturn, Dreamcast. Library
// files the song names are read from its folder.
bool isXsfData(const void *data, size_t length, const std::string &path);
Player *createXsfPlayer(const void *data, size_t length, const std::string &path);
