// SPDX-License-Identifier: GPL-3.0-or-later
//
// What the app shows of the three voices of a General Instrument AY-3-8910 or Yamaha YM-2149, the
// sound chip of the Atari ST, the ZX Spectrum 128 and others, worked out from the chip's registers.
// The replayers do not hand out the voices on their own.
#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>

class PsgView {
public:
    static constexpr int VOICES = 3;

    explicit PsgView(double clock) : clock(clock) {}

    // `r` holds registers 0-13. `step` is the time in seconds since the last update.
    void update(const uint8_t *r, double step, const bool *muted) {
        for (int v = 0; v < VOICES; v++) {
            bool tone = (r[7] & (1 << v)) == 0;
            bool noise = (r[7] & (8 << v)) == 0;
            int volume = r[8 + v] & 0x1f;
            // Bit 4 hands the volume to the envelope generator: show it as fairly loud.
            double target = (tone || noise) ? ((volume & 0x10) ? 0.8 : (volume & 0x0f) / 15.0) : 0.0;
            if (muted != nullptr && muted[v]) target = 0.0;
            level[v] = target >= level[v] ? target : std::max(target, level[v] - step * 3.0);
            int period = r[v * 2] | ((r[v * 2 + 1] & 0x0f) << 8);
            frequency[v] = tone && period > 0 ? clock / (16.0 * period) : 0.0;
            noisy[v] = noise && !tone;
        }
    }

    float vu(int v) const { return v >= 0 && v < VOICES ? static_cast<float>(level[v]) : 0.0f; }

    // A square wave at the voice's pitch (or noise) as tall as its volume, about 20 ms of it.
    void scopes(int points, float *out) {
        for (int v = 0; v < VOICES; v++) {
            double cycles = std::min(frequency[v] * 0.02, 24.0);
            for (int i = 0; i < points; i++) {
                double sample;
                if (noisy[v]) {
                    seed = seed * 1103515245u + 12345u;
                    sample = static_cast<double>((seed >> 16) & 0x7fff) / 16384.0 - 1.0;
                } else if (cycles > 0.0) {
                    double t = phase[v] + cycles * i / points;
                    sample = t - std::floor(t) < 0.5 ? 1.0 : -1.0;
                } else {
                    sample = 0.0;
                }
                out[v * points + i] = static_cast<float>(sample * level[v] * 0.3);
            }
            phase[v] += cycles * 0.37;
            phase[v] -= std::floor(phase[v]);
        }
    }

    void reset() { *this = PsgView(clock); }

    void setClock(double hz) { clock = hz; }

private:
    double clock;
    double level[VOICES] = {};
    double frequency[VOICES] = {};
    double phase[VOICES] = {};
    bool noisy[VOICES] = {};
    uint32_t seed = 12345u;
};
