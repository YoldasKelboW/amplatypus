// SPDX-License-Identifier: GPL-3.0-or-later
//
// Follows a Mega Drive VGM or GYM file's writes to its sound chips, in step with playback, to learn what
// each voice plays: VGM and GYM files are recordings of exactly those writes. Game Music Emu plays the
// file; this only reads along, for the channel view.
//
// Voices are numbered as Game Music Emu numbers them: FM 1-6 of the YM2612, its DAC (PCM), and the
// SN76489's four voices together (PSG).
#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

#include <zlib.h>

class FmTrack {
public:
    static constexpr int VOICES = 8;
    static constexpr int PCM = 6, PSG = 7;
    static constexpr double RATE = 44100.0; // VGM's and GYM's clock for waits

    // `file` is a VGM (plain or gzip-packed) or an unpacked GYM file. False if it has no YM2612.
    bool load(const std::vector<char> &file, bool gym) {
        data.clear();
        this->gym = gym;
        if (gym) {
            size_t start = file.size() >= 4 && std::memcmp(file.data(), "GYMX", 4) == 0 ? GYM_HEADER : 0;
            if (file.size() <= start) return false;
            data.assign(file.begin() + static_cast<long>(start), file.end());
            begin = 0;
            loop = SIZE_MAX;
            clock = DEFAULT_CLOCK;
            restart();
            return true;
        }
        data = gunzip(file);
        if (data.size() < 0x40 || std::memcmp(data.data(), "Vgm ", 4) != 0) return false;
        uint32_t version = u32(0x08);
        clock = data.size() >= 0x30 ? u32(0x2C) & 0x3FFFFFFF : 0;
        if (version < 0x110 || clock == 0) return false; // older files keep the YM2612 at 0x10 with the YM2413
        begin = version >= 0x150 && u32(0x34) != 0 ? 0x34 + u32(0x34) : 0x40;
        loop = u32(0x1C) != 0 ? 0x1C + u32(0x1C) : SIZE_MAX;
        if (begin >= data.size()) return false;
        restart();
        return true;
    }

    void restart() {
        pos = begin;
        time = 0.0;
        std::memset(regs, 0, sizeof(regs));
        for (bool &k : keyOn) k = false;
        for (int &a : psgAtten) a = 15;
        for (int &p : psgPeriod) p = 0;
        psgLatch = 0;
        dac = 0x80;
        dacPeak = 0.0;
        dacEnabled = false;
        pcmBank.clear();
        pcmPos = 0;
        finished = false;
    }

    // Reads on until `seconds` into the file.
    void advanceTo(double seconds) {
        double until = seconds * RATE;
        while (!finished && time < until) step();
    }

    // What voice `v` plays now: loudness 0..1 and pitch in Hz.
    void voice(int v, double &loudness, double &hz) const {
        loudness = hz = 0.0;
        if (v >= 0 && v < 6) {
            int port = v / 3, ch = v % 3;
            if (v == 5 && dacEnabled) return; // the DAC takes FM 6's place
            if ((reg(port, 0xB4 + ch) & 0xC0) == 0) return; // sent to neither speaker
            if (!keyOn[v]) return;
            loudness = carrierVolume(port, ch);
            int fnum = (reg(port, 0xA4 + ch) & 7) << 8 | reg(port, 0xA0 + ch);
            int block = reg(port, 0xA4 + ch) >> 3 & 7;
            hz = fnum * std::ldexp(1.0, block) * clock / (144.0 * 2097152.0);
        } else if (v == PCM) {
            if (dacEnabled) loudness = dacPeak;
        } else if (v == PSG) {
            // The loudest of the four, at its pitch.
            for (int c = 0; c < 4; c++) {
                // 2 dB per step, on the same 48 dB meter as the FM voices.
                double l = psgAtten[c] >= 15 ? 0.0 : 1.0 - psgAtten[c] * 2.0 / 48.0;
                if (l > loudness) {
                    loudness = l;
                    hz = c < 3 && psgPeriod[c] > 0 ? PSG_CLOCK / (32.0 * psgPeriod[c]) : 0.0;
                }
            }
        }
    }

    bool psgNoiseLoudest() const {
        int best = 0;
        for (int c = 1; c < 4; c++) if (psgAtten[c] < psgAtten[best]) best = c;
        return best == 3 && psgAtten[3] < 15;
    }

    // PCM peaks are held between reads; this lets them fall.
    void decayPcm(double seconds) { dacPeak = std::max(0.0, dacPeak - seconds * 3.0); }

private:
    static constexpr size_t GYM_HEADER = 0x1AC;
    static constexpr double DEFAULT_CLOCK = 7670453.0;
    static constexpr double PSG_CLOCK = 3579545.0;

    static std::vector<char> gunzip(const std::vector<char> &file) {
        if (file.size() < 2 || static_cast<unsigned char>(file[0]) != 0x1F || static_cast<unsigned char>(file[1]) != 0x8B) return file;
        std::vector<char> out;
        z_stream z{};
        if (inflateInit2(&z, 16 + MAX_WBITS) != Z_OK) return {};
        z.next_in = reinterpret_cast<Bytef *>(const_cast<char *>(file.data()));
        z.avail_in = static_cast<uInt>(file.size());
        char chunk[65536];
        int code = Z_OK;
        while (code == Z_OK) {
            z.next_out = reinterpret_cast<Bytef *>(chunk);
            z.avail_out = sizeof(chunk);
            code = inflate(&z, Z_NO_FLUSH);
            out.insert(out.end(), chunk, chunk + (sizeof(chunk) - z.avail_out));
            if (out.size() > 64u * 1024 * 1024) break;
        }
        inflateEnd(&z);
        return code == Z_STREAM_END ? out : std::vector<char>();
    }

    uint32_t u32(size_t at) const {
        if (at + 4 > data.size()) return 0;
        const auto *p = reinterpret_cast<const unsigned char *>(data.data() + at);
        return p[0] | p[1] << 8 | p[2] << 16 | static_cast<uint32_t>(p[3]) << 24;
    }

    int byte(size_t at) const { return at < data.size() ? static_cast<unsigned char>(data[at]) : 0; }
    int reg(int port, int addr) const { return regs[port][addr & 0xFF]; }

    // How loud a channel's carrier operators are, from their total level (0.75 dB per step), on a meter
    // that spans 48 dB.
    double carrierVolume(int port, int ch) const {
        static const int OP_REG[4] = {0x40, 0x48, 0x44, 0x4C}; // operators 1-4
        static const int CARRIERS[8] = {0x8, 0x8, 0x8, 0x8, 0xA, 0xE, 0xE, 0xF};
        int algorithm = reg(port, 0xB0 + ch) & 7;
        int best = 127;
        for (int op = 0; op < 4; op++) {
            if (CARRIERS[algorithm] & (1 << op)) best = std::min(best, reg(port, OP_REG[op] + ch) & 0x7F);
        }
        return std::max(0.0, 1.0 - best * 0.75 / 48.0);
    }

    void writeFm(int port, int addr, int data) {
        regs[port][addr] = static_cast<uint8_t>(data);
        if (port != 0) return;
        if (addr == 0x28) {
            int ch = data & 3;
            if (ch == 3) return;
            if (data & 4) ch += 3;
            keyOn[ch] = (data & 0xF0) != 0;
        } else if (addr == 0x2A) {
            setDac(data);
        } else if (addr == 0x2B) {
            dacEnabled = (data & 0x80) != 0;
        }
    }

    void setDac(int value) {
        dac = value;
        dacPeak = std::max(dacPeak, std::abs(value - 0x80) / 128.0 * 2.0);
        dacPeak = std::min(dacPeak, 1.0);
    }

    void writePsg(int data) {
        if (data & 0x80) {
            psgLatch = data >> 5 & 3;
            if (data & 0x10) {
                psgAtten[psgLatch] = data & 0x0F;
            } else if (psgLatch < 3) {
                psgPeriod[psgLatch] = (psgPeriod[psgLatch] & 0x3F0) | (data & 0x0F);
            }
        } else if (psgLatch < 3) {
            psgPeriod[psgLatch] = (psgPeriod[psgLatch] & 0x00F) | (data & 0x3F) << 4;
        }
    }

    void end() {
        if (loop != SIZE_MAX && loop < data.size()) pos = loop; else finished = true;
    }

    void step() {
        if (pos >= data.size()) { end(); return; }
        int cmd = byte(pos);
        if (gym) {
            switch (cmd) {
            case 0x00: time += RATE / 60.0; pos += 1; break;
            case 0x01: writeFm(0, byte(pos + 1), byte(pos + 2)); pos += 3; break;
            case 0x02: writeFm(1, byte(pos + 1), byte(pos + 2)); pos += 3; break;
            case 0x03: writePsg(byte(pos + 1)); pos += 2; break;
            default: pos += 1; break;
            }
            return;
        }
        switch (cmd) {
        case 0x4F: pos += 2; return;
        case 0x50: writePsg(byte(pos + 1)); pos += 2; return;
        case 0x52: writeFm(0, byte(pos + 1), byte(pos + 2)); pos += 3; return;
        case 0x53: writeFm(1, byte(pos + 1), byte(pos + 2)); pos += 3; return;
        case 0x61: time += byte(pos + 1) | byte(pos + 2) << 8; pos += 3; return;
        case 0x62: time += 735; pos += 1; return;
        case 0x63: time += 882; pos += 1; return;
        case 0x66: end(); return;
        case 0x67: {
            int type = byte(pos + 2);
            size_t size = u32(pos + 3) & 0x7FFFFFFF;
            if (type == 0x00 && pos + 7 + size <= data.size()) {
                pcmBank.insert(pcmBank.end(), data.begin() + static_cast<long>(pos + 7), data.begin() + static_cast<long>(pos + 7 + size));
            }
            pos += 7 + size;
            return;
        }
        case 0xE0: pcmPos = u32(pos + 1); pos += 5; return;
        default: break;
        }
        if (cmd >= 0x70 && cmd <= 0x7F) { time += (cmd & 0x0F) + 1; pos += 1; return; }
        if (cmd >= 0x80 && cmd <= 0x8F) {
            if (pcmPos < pcmBank.size()) setDac(static_cast<unsigned char>(pcmBank[pcmPos]));
            pcmPos++;
            time += cmd & 0x0F;
            pos += 1;
            return;
        }
        // Everything else is skipped by its length.
        if (cmd >= 0x30 && cmd <= 0x3F) pos += 2;
        else if ((cmd >= 0x40 && cmd <= 0x4E) || (cmd >= 0x51 && cmd <= 0x5F) || (cmd >= 0xA0 && cmd <= 0xBF)) pos += 3;
        else if (cmd >= 0xC0 && cmd <= 0xDF) pos += 4;
        else if (cmd >= 0xE1 && cmd <= 0xFF) pos += 5;
        else if (cmd == 0x90 || cmd == 0x91 || cmd == 0x95) pos += 5;
        else if (cmd == 0x92) pos += 6;
        else if (cmd == 0x93) pos += 11;
        else if (cmd == 0x94) pos += 2;
        else pos += 1;
    }

    std::vector<char> data;
    bool gym = false;
    size_t begin = 0, loop = SIZE_MAX, pos = 0;
    double clock = DEFAULT_CLOCK;
    double time = 0.0;
    bool finished = false;
    uint8_t regs[2][256] = {};
    bool keyOn[6] = {};
    int psgAtten[4] = {15, 15, 15, 15};
    int psgPeriod[3] = {};
    int psgLatch = 0;
    int dac = 0x80;
    double dacPeak = 0.0;
    bool dacEnabled = false;
    std::vector<char> pcmBank;
    size_t pcmPos = 0;
};
