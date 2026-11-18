# Publishing on F-Droid

Only the **free** build goes to F-Droid (see [UADE_LICENSING.md](UADE_LICENSING.md)). The build is
reproducible, so F-Droid can ship the APK signed with the project's own key: it builds the app from
source, compares the result with the APK attached to the GitHub release, and publishes ours if they
match. Users can then switch between the GitHub and the F-Droid APK without reinstalling.

## What makes the build reproducible

- Native code: `-ffile-prefix-map` strips the checkout folder from the libraries
  (`app/src/main/cpp/CMakeLists.txt`). Checked: builds in two different folders are byte-identical.
- No dependency metadata blob in the APK (`dependenciesInfo` in `app/build.gradle.kts`).
- The release APK is built unsigned with JDK 21, NDK 30.0.16248370 and CMake 4.1.2 – the same versions
  the recipe asks F-Droid for – and signed afterwards with `apksigner` from **build-tools 34**
  (newer apksigner versions produce signatures F-Droid cannot verify, see
  https://f-droid.org/docs/Reproducible_Builds/).
- Release APKs are built from a clean tree at the tagged commit.

## Release steps

1. Set `versionCode`/`versionName` in `app/build.gradle.kts`, add `fastlane/.../changelogs/<versionCode>.txt`
   in both languages and the CHANGELOG entry. Commit, then tag `v<versionName>` and push the tag.
2. Build unsigned release APKs from the clean tagged tree:
   `./gradlew clean assembleFreeRelease assembleFullRelease`
3. Sign them: `tools/sign-release.sh <versionName>` (asks for the key's password in the terminal).
   The APKs land in `dist/` together with their SHA-256 and the certificate fingerprint.
4. Attach `amplatypus-<version>-free.apk` and `-full.apk` to the GitHub release of the tag.

The release key is created once with `tools/create-release-key.sh`. It lives outside the repository
(`~/AMPlatypus-Key/`), with backups kept by the project owner. It never goes into the repository or CI.

## First submission

The recipe draft is [fdroid/io.github.yoldaskelbow.amplatypus.yml](fdroid/io.github.yoldaskelbow.amplatypus.yml).
Before submitting, fill in `AllowedAPKSigningKeys` (lower-case SHA-256 of the signing certificate, printed
by `tools/sign-release.sh`) and check `commit`. Then open a merge request on
https://gitlab.com/fdroid/fdroiddata adding it as `metadata/io.github.yoldaskelbow.amplatypus.yml`,
following the template there ("App inclusion").

Points to mention in the merge request:

- The project discloses that it is developed with the help of an AI coding assistant (README, NOTICE).
- Two flavours: `free` (for F-Droid, only freely licensed UADE replay routines) and `full` (GitHub only).
- Network: Modland (open file archive) and the Amiga Music Preservation API, used with the AMP staff's
  permission. No tracking, no ads, no proprietary libraries.
- Open questions for the reviewers: whether `platforms;android-37.2` is available on the build server
  (compileSdk 37.2 is required by current AndroidX), and whether the AMP API counts as a non-free
  network service (anti-feature `NonFreeNet`).
