// SPDX-License-Identifier: GPL-3.0-or-later
//
// A PC-98 song on 98fmplayer's drivers and YM2608 emulation, behind a small C interface. The emulator's
// headers use C11 atomics, which C++ cannot include; engine_pc98.cpp sees only this.
#pragma once

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct pc98_song pc98_song;

enum { PC98_RATE = 55467, PC98_TRACKS = 21 };

typedef struct {
    bool sounding;
    // Octave in the high nibble, note (0-11) in the low one, after pitch bends.
    uint8_t key;
    // 0-1, from the track's volume.
    float loudness;
} pc98_track;

// Reads the song at `path` and the sample banks it names from the same folder. NULL if it is neither an
// FMP nor a PMD song.
pc98_song *pc98_open(const char *path);
void pc98_close(pc98_song *song);
// Starts the song again from the beginning.
void pc98_restart(pc98_song *song);
bool pc98_is_fmp(const pc98_song *song);
// The song's main sample bank (FMP: PVI, PMD: PPC) was named but not found.
bool pc98_missing_samples(const pc98_song *song);
// Adds `frames` stereo frames at PC98_RATE to `buffer`, which must be zeroed first.
void pc98_render(pc98_song *song, int16_t *buffer, unsigned frames);
// How often the song has come round to its loop point.
unsigned pc98_loops(const pc98_song *song);
// Channel bits: 0-5 FM 1-6, 6-8 SSG 1-3, 9 ADPCM, 10 the PPZ8's PCM voices.
void pc98_set_mute(pc98_song *song, unsigned channels);
// The drivers' tracks in their order: FM 1, 2, 3, FM 3 extra 1-3, FM 4-6, SSG 1-3, ADPCM, PPZ8 1-8.
pc98_track pc98_track_status(const pc98_song *song, int track);
// A line of the song's comments (CP932), NULL past the last one.
const char *pc98_comment(pc98_song *song, int line);
// Whether the comments are PMD's: title, composer, arranger, then memo lines.
bool pc98_pmd_comments(const pc98_song *song);

#ifdef __cplusplus
}
#endif
