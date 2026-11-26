// SPDX-License-Identifier: GPL-3.0-or-later
#include "pc98_chip.h"

#include "common/fmplayer_common.h"
#include "common/fmplayer_file.h"
#include "fmdriver/fmdriver.h"
#include "fmdriver/ppz8.h"
#include "libopna/opna.h"
#include "libopna/opnatimer.h"

#include <stdlib.h>
#include <string.h>

struct pc98_song {
    struct fmplayer_file *file;
    struct fmdriver_work work;
    struct opna opna;
    struct opna_timer timer;
    struct ppz8 ppz8;
    uint8_t adpcm[256 * 1024];
    unsigned mute;
};

static void apply_mute(pc98_song *song) {
    static const unsigned CHIP[10] = {LIBOPNA_CHAN_FM_1, LIBOPNA_CHAN_FM_2, LIBOPNA_CHAN_FM_3, LIBOPNA_CHAN_FM_4,
                                      LIBOPNA_CHAN_FM_5, LIBOPNA_CHAN_FM_6, LIBOPNA_CHAN_SSG_1, LIBOPNA_CHAN_SSG_2,
                                      LIBOPNA_CHAN_SSG_3, LIBOPNA_CHAN_ADPCM};
    unsigned mask = 0;
    for (int c = 0; c < 10; c++) {
        if (song->mute & (1u << c)) mask |= CHIP[c];
    }
    opna_set_mask(&song->opna, mask);
    ppz8_set_mask(&song->ppz8, (song->mute & (1u << 10)) ? 0xffu : 0u);
}

pc98_song *pc98_open(const char *path) {
    enum fmplayer_file_error error = FMPLAYER_FILE_ERR_OK;
    struct fmplayer_file *file = fmplayer_file_alloc(path, &error);
    if (!file) return 0;
    pc98_song *song = calloc(1, sizeof(*song));
    if (!song) {
        fmplayer_file_free(file);
        return 0;
    }
    song->file = file;
    pc98_restart(song);
    return song;
}

void pc98_close(pc98_song *song) {
    if (!song) return;
    fmplayer_file_free(song->file);
    free(song);
}

void pc98_restart(pc98_song *song) {
    fmplayer_init_work_opna(&song->work, &song->ppz8, &song->opna, &song->timer, song->adpcm);
    // One loop: the drivers count how often the song has come round.
    fmplayer_file_load(&song->work, song->file, 1);
    apply_mute(song);
}

bool pc98_is_fmp(const pc98_song *song) { return song->file->type == FMPLAYER_FILE_TYPE_FMP; }

// Only the main bank (FMP: PVI, PMD: PPC) counts. Many FMP songs name their PVI as a PPZ8 bank too
// without using it, and that one fails to load as such.
bool pc98_missing_samples(const pc98_song *song) { return song->work.pcmerror[0]; }

void pc98_render(pc98_song *song, int16_t *buffer, unsigned frames) { opna_timer_mix(&song->timer, buffer, frames); }

unsigned pc98_loops(const pc98_song *song) { return song->work.loop_cnt; }

void pc98_set_mute(pc98_song *song, unsigned channels) {
    song->mute = channels;
    apply_mute(song);
}

pc98_track pc98_track_status(const pc98_song *song, int track) {
    pc98_track result = {false, 0xff, 0.0f};
    if (track < 0 || track >= FMDRIVER_TRACK_NUM) return result;
    const struct fmdriver_track_status *status = &song->work.track_status[track];
    if (!status->playing || status->key == 0xff || (status->key & 0x0f) == 0x0f) return result;
    // FM volumes run to 127, SSG and PPZ8 to 15, ADPCM to 255.
    float full = track == FMDRIVER_TRACK_ADPCM ? 255.0f : (track >= FMDRIVER_TRACK_SSG_1 ? 15.0f : 127.0f);
    float level = status->volume / full;
    result.sounding = true;
    result.key = status->actual_key;
    result.loudness = level > 1.0f ? 1.0f : level;
    return result;
}

const char *pc98_comment(pc98_song *song, int line) {
    if (!song->work.get_comment) return 0;
    return song->work.get_comment(&song->work, line);
}

bool pc98_pmd_comments(const pc98_song *song) { return song->work.comment_mode_pmd; }
