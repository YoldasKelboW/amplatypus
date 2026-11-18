<img src="fastlane/metadata/android/en-US/images/icon.png" width="96" align="right" alt="App icon: a pixel-art platypus wearing headphones">

# AMPlatypus

A free, open-source Android player for Amiga and tracker music – MOD, XM, S3M, IT and some fifty related
formats. It searches the [Modland](https://modland.com) archive (about 179,000 playable modules) and
[Amiga Music Preservation](https://amp.dascene.net), downloads what you pick and plays it with [libopenmpt](https://lib.openmpt.org/libopenmpt/). AHX and HivelyTracker
modules are played by the [HivelyTracker](https://github.com/pete-gordon/hivelytracker) replayer, and exotic
Amiga formats (PreTracker, SidMon, Future Composer, Delta Music and about a hundred more) by
[UADE](https://zakalwe.fi/uade), which runs the original replay routines in an emulated Amiga.

There are two builds. **free** contains only replay routines with a free licence and plays five of the
exotic formats; **full** contains all of them. See [docs/UADE_LICENSING.md](docs/UADE_LICENSING.md).

It is modelled on [4champ](https://github.com/sitomani/4champ), the iOS app by Aleksi Sitomaniemi.
Logic, data model and flow are ported from 4champ; the user interface is rebuilt from scratch
in Kotlin with Jetpack Compose and Material 3.

AMPlatypus is an independent project. It is not affiliated with Amiga Music Preservation (AMP),
Modland or 4champ.

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_player.png" width="200" alt="Player with one oscilloscope and level meter per channel and the running pattern">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_tracker_view.png" width="200" alt="Tracker view of a MOD file in the style of ProTracker, with level bars rising over the playing row">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_search.png" width="200" alt="Start page with search, a random-module button and browsing by format">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_radio.png" width="200" alt="Radio with two channels and the recently played list">
</p>

> **Early test version.** It works day to day on the developer's phone, but expect rough edges.
> Test builds are signed with a debug key, so the first proper release will need a fresh install.

## What it does

- **Search and browse** Modland by module name or composer, or by format → composer → module. The archive's
  index is stored on the device, so searching needs no network.
- **Player** with one oscilloscope and one level meter per channel, the pattern scrolling past the play
  position, and the texts musicians left in the module (song message, instrument and sample names).
  Tap a channel's oscilloscope to mute it, hold it to hear that channel alone. Shuffle and repeat
  (all or one) sit beside the playback controls.
- **Tracker views**: tap the format name in the player to switch to a full-screen view in the style of
  the tracker the module was made with – ProTracker for MOD files, FastTracker 2 for XM files – with
  pattern, oscilloscopes, level bars and playback buttons. More trackers will follow.
- **Background playback** with notification, lock-screen and headset controls.
- **Library** with favourites, playlists, search and sorting; import of your own module files.
- **Radio**: an endless random pick from Modland or AMP, modules that sound alike, or a shuffle of your
  favourites.
- **Material 3** throughout: dynamic colour on Android 12+, four more colour designs, light and dark,
  English and German.
- No ads, no tracking, no accounts.

## Download

Test builds are attached to the [releases](https://github.com/YoldasKelboW/amplatypus/releases).
Android 8.0 or newer.

Not there yet: muting channels of the UADE formats, formats that need a whole folder of instruments (IFF-SMUS),
Android Auto. See `CHANGELOG.md` for what changed and `docs/RUNBOOK.md` (German) for the plan.

## Music sources

Sources sit behind one interface (`MusicSource`), so the app does not depend on a single archive.

- **Modland** – the default source, searched through a locally stored index.
- **Local files** – import your own modules through the system file picker.
- **Amiga Music Preservation (AMP)** – searched online through the AMP API, with the permission of the AMP
  staff: modules, composers, the latest additions, a random-module radio, and a "sounds alike" radio based
  on AMP's sound analysis. The app keeps to the agreed limits (see `docs/PERMISSIONS.md`).

## Principles

- No ads, no tracking, no Google Play Services, no Firebase. Only free dependencies from
  Maven Central and Google Maven, so the same build can ship on F-Droid and Google Play.
- Be gentle with every server: identifying User-Agent, caching, back-off, no bulk downloads.
- Native replayers (libopenmpt, HivelyTracker, UADE) are built from source, never shipped as binaries.

## Building

    git submodule update --init --depth 1
    ./gradlew assembleDebug

See [docs/BUILDING.md](docs/BUILDING.md) for the toolchain and for settings that suit low-memory machines.
CI runs lint, unit tests and the debug build on every push.

## Credits

- [4champ](https://github.com/sitomani/4champ) © Aleksi Sitomaniemi – the app this one is modelled on.
- [Modland](https://modland.com) – the archive maintained by Coma (Daniel Johansson).
- [Amiga Music Preservation](https://amp.dascene.net) – the collection and the people maintaining it.
- [Press Start 2P](https://fonts.google.com/specimen/Press+Start+2P) by CodeMan38 – the pixel font of the
  tracker views (SIL Open Font License).
- This app is developed with the help of an AI coding assistant (Claude Code). See `NOTICE`.

## License

GPL-3.0-or-later, see `LICENSE`. Logic ported from 4champ is used under its MIT option and keeps
the original copyright notice in the file header. See `NOTICE` for third-party components.
