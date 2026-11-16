# Building

Command-line tools are enough; Android Studio is not required.

## Toolchain

- JDK 21 (e.g. Eclipse Temurin)
- Android SDK command-line tools, with `platforms/android-37.2`, `platform-tools` and a current `build-tools`
- For the native replayers: NDK 30.0.16248370 and CMake 4.1.2, both installed through the SDK manager
- The git submodules: `git submodule update --init --depth 1`

Point Gradle at the SDK with `ANDROID_HOME`, or with a `local.properties` file in the project root:

    sdk.dir=/path/to/Android/Sdk

## Commands

    ./gradlew assembleDebug                      # debug APKs in app/build/outputs/apk/{free,full}/debug/
    ./gradlew lintFullDebug testFreeDebugUnitTest testFullDebugUnitTest   # what CI checks
    adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk

## Two builds: free and full

The exotic Amiga formats are played by UADE, which runs each format's original replay routine in an
emulated Amiga. Only a few of those routines have a free licence (see `UADE_LICENSING.md`):

- `free` contains the freely licensed routines only. This is the build for F-Droid.
- `full` contains all routines that UADE ships. Its version name ends in `-full`.

Everything else is identical. Both have the same application id, so one replaces the other on a device.

## Files generated from UADE

UADE's own build generates part of its source (the 68000 emulation and a few configuration headers)
with programs that have to run on the build machine. Because the Android build cross-compiles, these
files are generated once and kept in `app/src/main/cpp/uade/gen/`. After updating the
`third_party/uade` submodule, regenerate and commit them:

    tools/gen_uade.sh

## Low-memory machines (8 GB RAM)

Put this into `~/.gradle/gradle.properties`:

    org.gradle.jvmargs=-Xmx2g
    org.gradle.workers.max=2
    kotlin.daemon.jvmargs=-Xmx1g

Limit debug builds to the ABI of your test device, so native code is compiled once instead of four times:

    ./gradlew assembleDebug -PdebugAbi=arm64-v8a

To make that permanent, add `debugAbi=arm64-v8a` to `~/.gradle/gradle.properties`.
Release builds always contain all ABIs.

## Trying the optimised build

Debug builds run the Kotlin code unoptimised, so the visualiser uses several times more CPU than it will in a
release. To measure or judge performance, install a release build signed with the debug key:

    ./gradlew assembleFullRelease -PlocalRelease -PdebugAbi=arm64-v8a
    adb install -r app/build/outputs/apk/full/release/app-full-release.apk

## Release builds

Release signing is set up before the first public release. The key never goes into the repository.
