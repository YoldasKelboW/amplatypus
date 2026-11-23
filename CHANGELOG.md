# Changelog

## 1.4.0 – 2026-11-23

- Music from consoles and home computers, played by Game_Music_Emu: NES (NSF), Super Nintendo (SPC), Game Boy
  (GBS), Sega Mega Drive and Master System (VGM, GYM), PC Engine (HES), MSX (KSS), Atari 8-bit (SAP) and
  ZX Spectrum / Amstrad CPC (AY) - about 62,000 pieces on Modland. Most sound chips' voices show as
  channels with levels and scopes and can be muted; Super Nintendo and Mega Drive FM music show no channels.
  "Consoles and home computers" in the format filter.

## 1.3.0 – 2026-11-23

- Sleep timer: the moon in the player's header stops playback after 15 to 120 minutes, or when the playing
  module ends. Timed stops fade the music out over the last 30 seconds. The time left shows next to the
  moon, and the timer keeps running while the app is closed.

## 1.2.0 – 2026-11-18

- Files with several songs - C64 tunes and much Amiga game music - show "Song 3 of 10" in the player with
  buttons to move between them, and play on into the next song when one ends. The player windows of
  the UADE and C64 views show the song number too.
- Commodore 64 music: about 63,000 SID tunes from the High Voltage SID Collection and 6,500 Sidplayer
  songs (MUS, stereo with STR) on Modland, played by libsidplayfp with the reSIDfp emulation. Three
  channels per SID chip that can be muted; levels and scopes are drawn from the SID's registers. Tunes
  play for up to three minutes, or end earlier when they fall silent. A C64 player view in the colours of
  the C64's start-up screen, and "Commodore 64" in the format filter.
- Format filter: the filter button next to Modland/AMP shows or hides whole groups - tracker modules,
  AHX/HivelyTracker, exotic Amiga formats - in search, browsing, random picks and the radio.
- New formats: IFF-SMUS (Sonix, about 750 songs; the song's instrument folder is downloaded with it)
  in the full build, and DeliTracker custom modules (about 750, they bring their own replay routine) in
  both builds.
- UADE: files a replay routine loads next to the song are found regardless of the case of their
  folder names, and songs are played through a short path, since some routines only keep short names.
- The exotic Amiga formats played by UADE get a view of their own too: a player window in the grey of
  the Amiga Workbench, as DeliTracker and EaglePlayer had, with module, author, replay routine, time,
  and a large scope and level bar for each of the four Paula channels.

## 1.1.0 – 2026-11-18

- Four more tracker views, opened like the others from the format chip in the player: Impulse Tracker
  for IT files (khaki panels, green notes), Scream Tracker 3 for S3M files (gold panels) and HivelyTracker
  for AHX and HVL files (steel-blue panels), and OctaMED for MED files in the blue, white and orange of
  the Amiga Workbench. Colours follow each program's own palette.
- Tracker views: cells that appear in several rows are now drawn in the right brightness.
- UADE formats: the level meters no longer drop to zero for a moment.

## 1.0.0 – 2026-11-18

- First release signed with the project's own key. Test builds (0.x) must be uninstalled first.
- Builds are reproducible, so F-Droid can publish the same, developer-signed APK.

## 0.5.0-pre1 – 2026-11-18

- Amiga Music Preservation (AMP) as a second archive, with the permission of the AMP staff: switch between
  Modland and AMP under the search field. Search modules and composers, open a composer's modules, see
  what is new on AMP, play a random module. Searched online; the app keeps to AMP's request limits.
- Radio: a random-module channel for AMP, and "Sounds alike", where each module sounds like the one before
  by AMP's sound analysis. Start it from an AMP module with the radio button in the player.
- Landscape and tablets: the player shows details and buttons on the left and channels or text on the
  right; on a phone held sideways the navigation moves to a rail at the side.
  The tracker views put the pattern to the right of the panels there.
- TalkBack announces each channel oscilloscope with its state and offers muting and solo as actions.
- Builds are reproducible: building in another folder gives the same APK, byte for byte.

## 0.4.0-pre2 – 2026-11-18

- Shuffle and repeat in the player: plain icons beside the main buttons. Shuffle mixes the queue and keeps
  the playing module; repeat switches between off, the whole queue and the playing module.
- New app icon: a pixel platypus with headphones. Fits every launcher shape and has a one-colour version for
  themed icons on Android 13 and newer.
- Tracker view for MOD files: tap the format chip in the player. Grey panels with position, pattern, length,
  tempo and speed, a quadrascope, and the pattern editor with level bars rising above the playing row, in
  the style of the Amiga trackers. Uses the free pixel font Press Start 2P.
- Tracker view for XM files in the style of FastTracker 2: blue-green panels, a grid of scopes, and the
  pattern in yellow on black with the volume column; many channels can be swiped sideways.
- The tracker view has its own previous, play/pause and next buttons at the bottom. Every tracker view will
  keep them in the same place, drawn in its tracker's style.

## 0.4.0-pre1 – 2026-11-17 (Phase 4: special formats)

- AHX and HivelyTracker (HVL) modules play, using the HivelyTracker replayer. Oscilloscopes, level meters,
  the pattern view and muting work for them too.
- Exotic Amiga formats play through UADE, which runs each format's original replay routine in an emulated
  Amiga. About a hundred Modland formats are covered, among them PreTracker, SidMon, Future Composer,
  Delta Music, Sonic Arranger and Digital Sound Studio. Many of them have no known length.
- Two-file formats play: TFMX (mdat/smpl), Richard Joseph (sng/ins) and Audio Sculpture (adsc/as). Only the
  song is listed; its sample file is downloaded with it and deleted with it.
- Level meters and oscilloscopes for the UADE formats, taken from the four emulated Paula channels. Their
  channels cannot be muted, and there is no pattern view for them.
- Two builds: "free" with only the freely licensed replay routines (five UADE formats), and "full" with all.
- The Modland index is rebuilt once after the update to pick up the new formats (about a minute).
- The mini player stays in view while the keyboard is open: the bottom bar steps aside and the mini player
  sits on top of the keyboard.
- A song of unknown length shows "–:––" instead of "0:00".
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
