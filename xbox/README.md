# Amplatypus for Xbox

A first version of Amplatypus as a UWP app, for an Xbox in developer mode (and for Windows). It plays
modules through libopenmpt (MOD, XM, S3M, IT, MED and the other formats libopenmpt knows):

- from **Modland**: the app downloads Modland's file list once a week, as the Android app does, keeps the
  formats it plays (about 180,000 modules) and searches them on the Xbox. *Surprise me* plays a random
  pick. Modules are fetched from Modland's mirrors when played and kept in the app's cache.
- from **this Xbox**: the music library, or a folder you choose.

Besides libopenmpt it has two of the Android app's players, built for UWP as `amplatypus_core.dll`
(`xbox/core`): Game Music Emu for console and home computer music (NES, SNES, Game Boy, Mega Drive and Master
System, PC Engine, MSX, Atari 8-bit, ZX Spectrum/Amstrad CPC AY files) and the HivelyTracker replayer (AHX,
HVL). The other players (UADE, C64, Atari ST, Spectrum trackers, PC-98, xSF), AMP and the radio are not part
of it yet.

Everything works with the controller: LB and RB switch between Modland and this Xbox, the stick or D-pad
moves between the lists and the buttons, A plays the module under the focus.

## Getting the package

The [Xbox workflow](../.github/workflows/xbox.yml) builds it on GitHub (Actions → Xbox → Run workflow,
or on every change to `xbox/`). The run's artifact `amplatypus-xbox` holds:

- `Amplatypus.Xbox_<version>_x64_Test/` with the app package (`.msix`) and its `Dependencies/x64/`
  (the C++ runtime and .NET Native framework packages),
- `Amplatypus.cer`, the certificate the package is signed with. Each run makes a new one.

## Installing on an Xbox in developer mode

1. Switch the Xbox to developer mode (Dev Mode Activation app) and open the Dev Home app on it.
2. Turn on the Xbox Device Portal in Dev Home and open the address it shows in a browser on your PC.
3. In the Device Portal, under *Home → My games & apps → Add*, choose the `.msix` file, then on the next
   page add the packages from `Dependencies/x64/`, and install.
4. Put module files in the Xbox's music library (for example from a USB stick), or pick a folder in the
   app with *Choose a folder*.

On Windows, install the certificate into *Local Machine → Trusted People* first, then open the `.msix`.

## Building it yourself

Visual Studio 2022 with the *Universal Windows Platform development* workload. Build
`third_party/openmpt/build/vs2022win10uwp/libopenmpt-small.vcxproj` (ReleaseShared, x64), copy the
DLLs from `third_party/openmpt/bin/release/vs2022-win10uwp-shared/amd64/` to `xbox/native/x64/`, then open
`xbox/Amplatypus.Xbox.sln`. The core players: configure `xbox/core` with CMake for UWP
(`-G "Visual Studio 17 2022" -A x64 -DCMAKE_SYSTEM_NAME=WindowsStore -DCMAKE_SYSTEM_VERSION=10.0`), build
`amplatypus_core` in Release and copy the DLL to `xbox/native/x64/` as well.
