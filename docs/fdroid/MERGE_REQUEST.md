# Text for the fdroiddata merge request

Title: `New app: AMPlatypus`

Description:

```
AMPlatypus is a free, open-source player for Amiga and tracker music (MOD, XM, S3M, IT, AHX/HVL and
about a hundred exotic Amiga formats). It searches the Modland archive and Amiga Music Preservation,
downloads what the user picks and plays it with libopenmpt, the HivelyTracker replayer and UADE.

* Source: https://github.com/YoldasKelboW/amplatypus (GPL-3.0-or-later)
* Flavours: `free` is the one for F-Droid. It contains only the UADE replay routines with a free
  licence (see docs/UADE_LICENSING.md). `full` contains all of them and is distributed on GitHub only.
* All native code (libopenmpt, HivelyTracker, UADE) is built from source via git submodules.
* Reproducible build: the APK attached to the GitHub release is built from the tag with the same JDK,
  NDK and CMake and signed afterwards with apksigner from build-tools 34, so `Binaries` and
  `AllowedAPKSigningKeys` are set. Builds in two different folders were byte-identical.
* Network: Modland (open file archive) and the Amiga Music Preservation API, used with the
  permission of the AMP staff. No tracking, no ads, no proprietary libraries.
* Disclosure: the app is developed with the help of an AI coding assistant; this is stated in the
  README, NOTICE and the store description.

Open points I am unsure about:
* compileSdk is 37.2 (current AndroidX needs it), so the recipe installs `platforms;android-37.2`.
* Whether the AMP API counts as a non-free network service (NonFreeNet).

Thanks for reviewing!
```

Then tick the boxes of the "App inclusion" template that apply.
