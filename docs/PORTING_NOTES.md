# Porting notes: 4champ (iOS) → Android

Source analysed: `sitomani/4champ` at its default branch, November 2026 (about 13,000 lines of Swift/Objective-C),
with the `openmpt` and `uade-ios` submodules. Sections marked *to be detailed* were only skimmed in Phase 1
and get a closer read in the phase that implements them.

## 1. Where 4champ gets its data

4champ does not talk to amp.dascene.net directly. It uses a private API run by its developer at 4champ.net,
which is restricted to the 4champ app and requires an API key. **This app does not use that API.**

For AMP, access has to be agreed with the AMP staff directly; until then the app has no AMP source
(see `docs/PERMISSIONS.md`). What carries over from 4champ is the shape of the features – search by module,
composer, group and text, drill-down from group to composers to modules, "new in AMP", random radio – not the
network layer.

## 2. iOS → Android mapping

| 4champ (iOS) | Role | Android counterpart |
|---|---|---|
| `Networking/Endpoints.swift`, `APIRequest.swift`, `NetworkClient.swift` | Endpoint table, request building, JSON decoding | OkHttp client with User-Agent interceptor and HTTP cache, `kotlinx.serialization` models, `ApiConfig` as the single place for the base URL |
| `Networking/ModuleFetcher.swift` | Resolve link → download → gunzip → save, with progress states | Coroutine-based fetcher exposing a `Flow` of states (`ResolvingPath`, `Downloading(progress)`, `Unpacking`, `Failed`, `Done`), `GZIPInputStream`, files in app-specific storage |
| `Scenes/Search/*` | Search in four modes, drill-down group → composers → modules, batch download, sort toggle | Compose screens + ViewModels, Material 3 `SearchBar`, filter chips for the mode, list detail navigation |
| `Scenes/Local/*`, `ModuleStorage.swift` | Local library, favourites, deletion *(to be detailed)* | Room database + repository, Compose list with sort and filter |
| `Data/ModuleInfo` (Core Data) | `modId, modName, modAuthor, modType, modSize, modLocalPath, modURL, modFavorite, added, lastPlayed, playCount, loop, serviceId, serviceKey, radioOnly, shared, …` | Room entity `Module` with the same columns |
| `Data/Playlist` (Core Data) | `plId, plName, position, playhead, playmode, locked`, ordered many-to-many to modules | Room entities `Playlist` and `PlaylistEntry(playlistId, moduleId, position)` |
| `Data/Structs.swift` (`MMD`) | In-memory module descriptor; equality by id; `supported()` = known format and empty note | Kotlin data class; constants `radioBufferLen = 3`, `searchDelay = 0.3 s`, `stereoSeparationDefault = 40`, `sessionHistoryLen = 30` carried over (radio prefetch reduced, see below) |
| `Scenes/Playlists/*`, `PlaylistSelector/*` | Playlists and "add to playlist" picker *(to be detailed)* | Compose screens, modal bottom sheet as picker |
| `Scenes/Radio/*` | Channels `all`, `new`, `local`, `custom`; buffer of upcoming modules; session history; skips unsupported formats | Radio controller feeding the Media3 queue. **Deviation:** prefetch at most the *next* module (iOS buffers 3) to spare the server |
| `Replay/Replay.{h,m}` | Audio output and dispatch to a replayer by format | Native engine (C++ via JNI), output through Oboe at 48 kHz stereo |
| `Replay/OpenMPT/MPTReplayer` | libopenmpt wrapper: load from memory, read stereo, seek, repeat, stereo separation, interpolation filter, Amiga resampler, sample/instrument names, pattern data, per-channel volume | Same calls from C++; iOS renders at 44.1 kHz, Android will render at 48 kHz |
| `Replay/Hively/HVLReplayer` | AHX/THX/HVL | Phase 4, same replayer source built with the NDK |
| `Replay/UADE/UADEReplayer` | Exotic Amiga formats (long list), in-process UADE | Phase 4, see licence notes |
| `Replay/ModulePlayer.swift` | Play queue, play modes, observers, lock-screen info *(to be detailed)* | Media3 `MediaSessionService` with a custom `Player` backed by the native engine |
| `Scenes/Visualizer/*` | Per-channel volume bars, pattern view, shader visualiser | Now-playing screen, see section 4 |
| `Scenes/Settings/*` | Stereo separation, interpolation, Amiga resampler | Compose settings screen, DataStore |
| `Scenes/About/*` | Credits and licences | "About" and "Licenses" screens |
| `Utils/RefreshLatestOperation.swift` | Background refresh of the newest id, badge, local notification | WorkManager job, optional notification |
| `Utils/ModuleSharing.swift` | Share link to a module page | Android share sheet; app link handling to be decided |
| `Scenes/CarPlay/*` | CarPlay | Optional: Android Auto through the Media3 session |
| UIKit/SwiftUI views, routers, presenters | UI plumbing | Not ported; rebuilt in Compose |

### Format routing (from the iOS replayers)

- **libopenmpt:** 667, 669, AMF, AMS, DBM, DIGI, DMF, DSM, DSYM, DTM, FAR, FMT, FST, GDM, GTK, GT2, ICE, IT, IMF,
  J2B, M15, MDL, MED, MMCMP, MMS, MO3, MOD, MTPM, MT2, MTM, NST, OCT, OK, OKT, OSS, OXM, PLM, PPM, PSM, PT36, PTM,
  S3M, SFX, SFX2, ST26, STK, STM, STP, STX, SYNMOD, ULT, UMX, WOW, XM, XMF
- **HivelyTracker:** THX, AHX, HVL
- **UADE:** everything else in `UADEReplayer.supportedFormats` (AAM, ABK, AON, BP, DSS, DW, FC, HIP, …)

Modules in formats outside these lists are reported as unsupported and skipped by the radio.

## 3. Platform decisions

- UI is Material 3 throughout: dynamic colour on Android 12+, a fallback scheme below, adaptive navigation
  (bar / rail / drawer) via `NavigationSuiteScaffold`, edge-to-edge, predictive back.
  The iOS look of 4champ is deliberately not imitated.
- Only official Jetpack libraries and other free dependencies; no Play Services. One build serves F-Droid,
  IzzyOnDroid and Google Play.
- `compileSdk` is 37.2 because current AndroidX releases require it; `targetSdk` stays 36, `minSdk` 26.

## 4. Visualiser requirement

The now-playing screen gets a small visualiser per channel, as in tracker software and in 4champ, modelled
on ProTracker: one VU meter per channel that jumps up on a note and falls back, and an oscilloscope per
channel ("quadrascope" for four-channel modules), scaling to more channels for XM/S3M/IT.
Data sources: libopenmpt's per-channel VU values (`get_current_channel_vu_mono/left/right`), which 4champ
uses through `volumeOnChannel`, for the meters. True per-channel scopes need per-channel sample data, which
libopenmpt does not expose directly; the approach (channel-muted side renders or a patch to the mixer) is to
be decided in Phase 5. The spectrum view from the mixed PCM buffer stays as an additional mode.

**Status after Phase 2:** per-channel VU meters (segmented, time-aligned with the audio output), the scrolling
pattern view and the module texts are implemented in the full-screen player. Per-channel oscilloscopes are
done by loading the module once more per channel with all other channels muted (libopenmpt's `interactive`
extension) and rendering those copies in step with the audible one, only while the player is on screen and
only up to 32 channels. The spectrum view is still open.

## 5. The source layer (`MusicSource`)

The app must not depend on one archive. Everything above the source layer (UI, library, playlists, radio)
talks to `MusicSource` only; the draft lives in `app/src/main/kotlin/…/source/MusicSource.kt`.

| Operation | Modland | Local files |
|---|---|---|
| `search` | Full-text query on the local index (Room FTS): module name, composer | Query on the library |
| `browse` | Format → composer → module, from the local index | Files picked through the system file picker |
| `details` | Row of the local index | File metadata |
| `random` | Random row of the local index | Random library entry |
| `download` | HTTPS from one of three hosts | Copy from the picked URI |

An AMP source would slot in as a third implementation once its API and terms are agreed with the AMP staff.

- A module is identified by `ModuleRef(sourceId, key)`. This replaces 4champ's pair `serviceId`/`serviceKey`,
  which was added to the iOS data model for exactly this purpose (see its journal entry of 10 Aug 2021).
- Sources declare `capabilities`, so the UI hides what a source cannot do (Modland has no group or text search,
  AMP has no format browsing).
- Sources that need permission are gated by a build flag (`BuildConfig.AMP_SOURCE_ENABLED` for AMP) and stay
  off in release builds until the permission is recorded in `docs/PERMISSIONS.md`.
- The index is fetched at most once a week and directories are never listed live.

### Modland (researched 2026-11-13)

- **Index:** `https://modland.com/allmods.zip` (5.8 MB) holds one file, `allmods.txt` (31 MB, about 516,000
  lines, regenerated daily). Each line is `<size in bytes><TAB><path>`, the path being
  `Format/Composer/[sub folders/]file.ext`. Ten entries have only two path parts and are skipped.
- **Filtering:** only extensions libopenmpt can play are imported, about 179,000 modules. Most of the rest
  are console and chip formats (SID, SPC, PSF, …) that are out of scope.
- **Hosts:** `modland.com`, `ftp.modland.com` and `modland.ziphoid.com` all serve
  `/pub/modules/<path>` over HTTPS, so no FTP library is needed. Files are served uncompressed.
- **Known rough edges:** a few formats nest a sub-format below the format folder
  (`Ad Lib/Screamtracker 3 AdLib/<composer>/…`), where the second path part is not the composer; unknown
  authors are filed under `- unknown` or `_unknown`; co-operations sit in `coop-<name>` sub folders.
- Formats with prefix-style names (`mdat.*`, `ash.*`) belong to UADE and come with Phase 4.

## 6. Licences

- 4champ: dual GPL/MIT. Ported logic is taken under MIT, with the copyright notice kept in the file header.
- libopenmpt: BSD-3-Clause. HivelyTracker replayer: BSD-3-Clause.
- **UADE – open point.** `COPYING` in `uade-ios` says the UAE-derived code is "licensed under the GNU GPL"
  without naming a version (the bundled text is GPLv2), the sound core in `amigasrc/score/` is LGPL, and the
  files under `players/` carry "various different licenses". Whether every GPL part is "version 2 or later"
  (needed to combine with GPL-3.0-or-later) could not be confirmed from the file headers in Phase 1.
  This must be settled before Phase 4; until then no UADE code is part of the build. If parts turn out to be
  GPL-2.0-only or non-free, UADE goes into a separate build flavour or is left out.

## 7. Open questions

1. Whether and how the app may access AMP: API, usage policy and keys, to be agreed with the AMP staff.
2. UADE licence compatibility (above).
3. Final app name and application ID. "AMPlatypus" contains "AMP"; 4champ itself had to drop "AMP" from its
   name in 2013 after a trademark dispute.
