# Amplatypus for Xbox

A first version of Amplatypus as a UWP app, for an Xbox in developer mode (and for Windows). It plays
module files from the music library, or from a folder you choose, through libopenmpt: MOD, XM, S3M, IT,
MED and the other formats libopenmpt knows. The Android app's other players (UADE, C64, consoles,
Atari, Spectrum, PC-98), the archives (Modland, AMP) and the radio are not part of it yet.

Everything works with the controller: the stick or D-pad moves between the list and the buttons, A plays
the module under the focus.

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
`xbox/Amplatypus.Xbox.sln`.
