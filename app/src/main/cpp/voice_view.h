// SPDX-License-Identifier: GPL-3.0-or-later
//
// Levels and sketched waveforms for sound chips whose emulator does not hand out its voices: what each
// voice plays is read from the chip's registers (how loud, what pitch) and drawn as a wave of that pitch
// and height. The shape is only a hint; the real voice may sound quite different.
#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

class VoiceView {
public:
    enum Shape { SINE, SQUARE, NOISE };

    explicit VoiceView(int voices = 0) { resize(voices); }

    void resize(int voices) {
        auto n = static_cast<size_t>(std::max(0, voices));
        level.assign(n, 0.0);
        target.assign(n, 0.0);
        frequency.assign(n, 0.0);
        phase.assign(n, 0.0);
        shape.assign(n, SINE);
    }

    int size() const { return static_cast<int>(level.size()); }

    // What voice `v` plays now: loudness 0..1, pitch in Hz (0 for none), and how to draw it.
    void set(int v, double loudness, double hz, Shape drawn = SINE) {
        if (v < 0 || v >= size()) return;
        auto i = static_cast<size_t>(v);
        target[i] = std::clamp(loudness, 0.0, 1.0);
        frequency[i] = hz;
        shape[i] = drawn;
    }

    // Moves the meters towards what was last set: up at once, down slowly, as a level meter does.
    void advance(double seconds, const std::vector<bool> &muted) {
        for (size_t i = 0; i < level.size(); i++) {
            double goal = i < muted.size() && muted[i] ? 0.0 : target[i];
            level[i] = goal >= level[i] ? goal : std::max(goal, level[i] - seconds * 2.5);
        }
    }

    float vu(int v) const { return v >= 0 && v < size() ? static_cast<float>(level[static_cast<size_t>(v)]) : 0.0f; }

    // About 20 ms of each voice, voice after voice.
    void scopes(int points, float *out) {
        for (size_t v = 0; v < level.size(); v++) {
            double cycles = std::min(frequency[v] * 0.02, 24.0);
            for (int i = 0; i < points; i++) {
                double sample = 0.0;
                if (shape[v] == NOISE) {
                    seed = seed * 1103515245u + 12345u;
                    sample = static_cast<double>((seed >> 16) & 0x7fff) / 16384.0 - 1.0;
                } else if (cycles > 0.0) {
                    double t = phase[v] + cycles * i / points;
                    sample = shape[v] == SQUARE ? (t - std::floor(t) < 0.5 ? 1.0 : -1.0) : std::sin(t * 2.0 * M_PI);
                }
                out[v * static_cast<size_t>(points) + static_cast<size_t>(i)] = static_cast<float>(sample * level[v] * 0.3);
            }
            phase[v] += cycles * 0.37;
            phase[v] -= std::floor(phase[v]);
        }
    }

    void reset() {
        std::fill(level.begin(), level.end(), 0.0);
        std::fill(target.begin(), target.end(), 0.0);
        std::fill(frequency.begin(), frequency.end(), 0.0);
    }

private:
    std::vector<double> level, target, frequency, phase;
    std::vector<Shape> shape;
    uint32_t seed = 12345u;
};
