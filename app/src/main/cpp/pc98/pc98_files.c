// SPDX-License-Identifier: GPL-3.0-or-later
//
// What 98fmplayer's front ends provide, for the app: reading the song and the sample banks it names
// (.PVI, .PPZ, .PPC, .PZI) from the song's folder, in any case. 98fmplayer's own Unix version converts
// file names with iconv, which Android only has from version 9 on; the app needs no conversion.
//
// The YM2608's rhythm sounds sit in a ROM of Yamaha's, which cannot be shipped: drums written for the
// chip's rhythm section stay silent. Everything else plays.
#define _POSIX_C_SOURCE 200809L
#include "common/fmplayer_drumrom.h"
#include "common/fmplayer_file.h"

#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

static void *read_whole(const char *path, size_t maxsize, size_t *filesize, enum fmplayer_file_error *error) {
  FILE *f = fopen(path, "rb");
  if (!f) {
    if (error) *error = FMPLAYER_FILE_ERR_NOTFOUND;
    return 0;
  }
  void *buf = 0;
  long size = -1;
  if (fseek(f, 0, SEEK_END) == 0) size = ftell(f);
  if (size < 0 || fseek(f, 0, SEEK_SET) != 0) {
    if (error) *error = FMPLAYER_FILE_ERR_FILEIO;
    goto done;
  }
  if (maxsize && (size_t)size > maxsize) {
    if (error) *error = FMPLAYER_FILE_ERR_BADFILE_SIZE;
    goto done;
  }
  buf = malloc(size ? (size_t)size : 1);
  if (!buf) {
    if (error) *error = FMPLAYER_FILE_ERR_NOMEM;
    goto done;
  }
  if (fread(buf, 1, (size_t)size, f) != (size_t)size) {
    if (error) *error = FMPLAYER_FILE_ERR_FILEIO;
    free(buf);
    buf = 0;
    goto done;
  }
  *filesize = (size_t)size;
done:
  fclose(f);
  return buf;
}

void *fmplayer_fileread(const void *pathptr, const char *pcmname, const char *extension, size_t maxsize,
                        size_t *filesize, enum fmplayer_file_error *error) {
  const char *path = pathptr;
  if (!pcmname) return read_whole(path, maxsize, filesize, error);
  // A sample bank named in the song: "NAME" (with or without its extension), next to the song.
  char wanted[256];
  size_t namelen = strlen(pcmname);
  if (namelen + (extension ? strlen(extension) : 0) + 1 > sizeof(wanted)) {
    if (error) *error = FMPLAYER_FILE_ERR_NOTFOUND;
    return 0;
  }
  strcpy(wanted, pcmname);
  if (extension && !(namelen > 4 && strcasecmp(pcmname + namelen - 4, extension) == 0)) strcat(wanted, extension);
  const char *slash = strrchr(path, '/');
  size_t dirlen = slash ? (size_t)(slash - path) : 1;
  char *dir = strndup(slash ? path : ".", dirlen);
  if (!dir) {
    if (error) *error = FMPLAYER_FILE_ERR_NOMEM;
    return 0;
  }
  void *buf = 0;
  DIR *d = opendir(dir);
  if (d) {
    struct dirent *entry;
    while ((entry = readdir(d))) {
      if (strcasecmp(entry->d_name, wanted) != 0) continue;
      size_t full = strlen(dir) + 1 + strlen(entry->d_name) + 1;
      char *found = malloc(full);
      if (found) {
        snprintf(found, full, "%s/%s", dir, entry->d_name);
        buf = read_whole(found, maxsize, filesize, error);
        free(found);
      }
      break;
    }
    closedir(d);
  }
  free(dir);
  if (!buf && error) *error = FMPLAYER_FILE_ERR_NOTFOUND;
  return buf;
}

// The name is only shown by 98fmplayer's own display, which the app does not use.
char *fmplayer_path_filename_sjis(const void *path) {
  (void)path;
  return 0;
}

void *fmplayer_path_dup(const void *path) { return strdup((const char *)path); }

bool fmplayer_drum_rom_load(struct opna_drum *drum) {
  (void)drum;
  return false;
}

bool fmplayer_drum_loaded(void) { return false; }
