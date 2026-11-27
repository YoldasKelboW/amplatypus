// SPDX-License-Identifier: GPL-3.0-or-later
//
// The Android app's players (engine.h) for the Xbox app: a C interface the C# code calls through
// P/Invoke. Every call takes the handle amp_load returned.
#pragma once

#include <stddef.h>

#ifdef AMP_CORE_EXPORTS
#define AMP_API extern "C" __declspec(dllexport)
#else
#define AMP_API extern "C" __declspec(dllimport)
#endif

// Loads a module from its bytes; `name` (UTF-8) is its file name, whose extension some formats need.
// Null if no player here knows it.
AMP_API void *amp_load(const void *data, int length, const char *name);
AMP_API void amp_free(void *player);
// Renders up to `frames` interleaved stereo floats; 0 at the end.
AMP_API int amp_read(void *player, int sample_rate, int frames, float *out);
AMP_API double amp_duration(void *player);
AMP_API double amp_position(void *player);
AMP_API void amp_seek(void *player, double seconds);
AMP_API int amp_channels(void *player);
AMP_API void amp_mute(void *player, int channel, int mute);
AMP_API float amp_level(void *player, int channel);
AMP_API int amp_subsongs(void *player);
AMP_API int amp_select_subsong(void *player, int index);
// Copies the UTF-8 value of `key` ("title", "artist", "type_long", "message_raw") into `buffer`;
// returns its full length in bytes.
AMP_API int amp_metadata(void *player, const char *key, char *buffer, int size);
// Where the module is: order (position), pattern and row; zeros for formats without patterns.
AMP_API void amp_play_position(void *player, int *order, int *pattern, int *row);
AMP_API int amp_order_count(void *player);
// Ticks per row and beats per minute; zeros where the format has no such idea.
AMP_API void amp_tempo(void *player, int *speed, int *bpm);
// Copies a pattern's rows (separated by '\n', channels by '|', cells like "C-5 01 A0F") into `buffer`;
// returns the full length in bytes, 0 for formats without patterns.
AMP_API int amp_pattern(void *player, int pattern, char *buffer, int size);
// Scopes: `points` samples per channel of what was rendered last, channel after channel, into `out`.
// Players that draw their own (consoles) answer amp_own_scopes with 1 and fill them with amp_player_scopes.
// For the others a scope set runs a copy of the module per channel in step with playback: create it from
// the same bytes, seek it where the player is, and render it with the same frame counts.
AMP_API int amp_own_scopes(void *player);
AMP_API void amp_player_scopes(void *player, int points, float *out);
AMP_API void *amp_scopes_create(const void *data, int length, int channels);
AMP_API void amp_scopes_free(void *scopes);
// How many channels amp_scopes_render writes.
AMP_API int amp_scopes_channels(void *scopes);
AMP_API void amp_scopes_seek(void *scopes, double seconds);
AMP_API void amp_scopes_render(void *scopes, int sample_rate, int frames, float *out, int points);
// Whether amp_mute works while the song plays.
AMP_API int amp_can_mute(void *player);
// Extensions (lower case, ';'-separated) of the console and home computer players, which Modland files in
// their own folders, and of the tracker formats libopenmpt takes.
AMP_API const char *amp_tracker_extensions(void);
// Extensions (lower case, ';'-separated) the players here take.
AMP_API const char *amp_extensions(void);
