# Changelog

## 0.4.0 – unreleased (Phase 4: special formats)

- AHX and HivelyTracker (HVL) modules play, using the HivelyTracker replayer. Oscilloscopes, level meters,
  the pattern view and muting work for them too. The Modland index is rebuilt once to pick them up.
- Exotic Amiga formats play through UADE, which runs each format's original replay routine in an emulated
  Amiga. About a hundred Modland formats are covered, among them PreTracker, SidMon, Future Composer,
  Delta Music, Sonic Arranger and Digital Sound Studio. These modules have no channel views yet, and
  many have no known length.
- Two builds: "free" with only the freely licensed replay routines (five formats), and "full" with all.
- Two-file formats play: TFMX (mdat/smpl), Richard Joseph (sng/ins) and Audio Sculpture (adsc/as). Only the song
  is listed; its sample file is downloaded with it and deleted with it.
- Not yet: formats that need a whole folder of instruments (IFF-SMUS, Sonix and a few others).

## 0.3.0-pre2 – 2026-11-15

- Pattern view: when a module has more channels than fit on the screen, the row numbers stay in place and
  the channels can be swiped sideways. A fade at the edge shows that more channels are hidden.
- Pattern view: the last column is no longer cut off slightly on some modules.

## 0.3.0-pre1 – 2026-11-15 (Phase 3: library, playlists, radio)

- Radio with two channels: a random pick from the whole Modland archive, or a shuffle of your favourites.
  It fetches only the one module that follows the playing one. Archive modules are not added to the
  library; they stay in "Recently played" (last 30) and can be kept from there.
- Favourites: heart button in the library, the radio history and the player.
- Library: search by title or composer, favourites filter, sort by title, composer or date added.
- Playlists: create, add modules from the library or a playlist, play, remove, delete.
- Import module files from the device (also with Amiga-style names such as `mod.title`).
- Share a module as a link to its Modland address.
- Tap an oscilloscope to mute its channel, hold it to hear that channel alone.
  Muted channels keep running silently, so they come back in time.
- Settings: choose the app language (English, German or system) on Android 13 and newer.
- No AMP source: the 4champ.net API is reserved for 4champ, and direct access to AMP is not agreed yet.

## 0.2.0 – unreleased (Phase 2: search, download, play)

- Modland as the first music source: the complete file index is downloaded at most once a week, filtered to
  playable formats (about 179,000 modules) and stored in a local database with full-text search.
- Search by module name and composer; browse by format → composer → module; results load page by page.
- Modules are downloaded on demand over HTTPS (three hosts as fallback) and kept in the library.
- Playback with libopenmpt 0.8.10 (built from source, git submodule) at 48 kHz stereo.
- Background playback through a Media3 media session: notification, lock screen, headset buttons,
  audio focus, pause when headphones are unplugged.
- Full-screen player: one level meter per channel, the pattern scrolling past the play position, and the
  texts stored in the module (song message, instrument and sample names).
- Start page with a "Surprise me" button that plays a random module; format tiles in all lists;
  mini player with live channel meters.
- Per-channel oscilloscopes ("quadrascope") in the player; the pattern view scales to the screen width.
- Settings: five colour designs (Material You, Paula, Tracker, Workbench, Copper) and light/dark choice.
- Library screen with delete.
- Application ID changed to `io.github.yoldaskelbow.amplatypus` (still provisional).

## 0.1.0 – unreleased (Phase 1: analysis and skeleton)

- Android project with Gradle Kotlin DSL and version catalog (minSdk 26, targetSdk 36).
- Material 3 app shell with dynamic colour and adaptive navigation: Search, Library, Radio, Settings (placeholders).
- Placeholder adaptive icon with themed-icon support.
- Central User-Agent configuration.
- Draft of the `MusicSource` interface; AMP source flag (off in release builds).
- `docs/BUILDING.md` with low-memory build settings and the `debugAbi` property.
- CI workflow: lint, unit tests, debug APK as artifact.
- Porting notes mapping 4champ's iOS code to Android (`docs/PORTING_NOTES.md`).
