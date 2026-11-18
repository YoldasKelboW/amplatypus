#!/usr/bin/env bash
# Signs the release APKs with the release key and copies them to dist/.
# Usage: tools/sign-release.sh 1.0.0   (after ./gradlew assembleFreeRelease assembleFullRelease)
set -euo pipefail

VERSION="${1:?usage: tools/sign-release.sh <version>}"
KEYSTORE="${AMPLATYPUS_KEYSTORE:-$HOME/AMPlatypus-Key/amplatypus-release.jks}"
# apksigner from build-tools 35 and later writes signatures F-Droid cannot copy onto its own build
# (https://f-droid.org/docs/Reproducible_Builds/), so signing uses build-tools 34.
BUILD_TOOLS="$HOME/Android/Sdk/build-tools/34.0.0"
APKSIGNER="$BUILD_TOOLS/apksigner"
export JAVA_HOME="${JAVA_HOME:-$HOME/Android/jdk-21.0.13+9}"
export PATH="$JAVA_HOME/bin:$PATH"  # apksigner starts "java" from the PATH
cd "$(dirname "$0")/.."

[ -f "$KEYSTORE" ] || { echo "No key at $KEYSTORE"; exit 1; }
mkdir -p dist

# The password is read here, in your terminal, and handed to apksigner through the environment only.
read -r -s -p "Password of the release key: " AMPLATYPUS_KS_PASS
echo
export AMPLATYPUS_KS_PASS

for flavor in free full; do
    unsigned="app/build/outputs/apk/$flavor/release/app-$flavor-release-unsigned.apk"
    out="dist/amplatypus-$VERSION-$flavor.apk"
    [ -f "$unsigned" ] || { echo "Missing $unsigned - build it first."; exit 1; }
    "$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias amplatypus --ks-pass env:AMPLATYPUS_KS_PASS \
        --out "$out" "$unsigned"
    "$APKSIGNER" verify "$out"
    echo "signed: $out"
done
unset AMPLATYPUS_KS_PASS

echo
"$APKSIGNER" verify --print-certs "dist/amplatypus-$VERSION-free.apk" | grep -i "SHA-256"
sha256sum dist/amplatypus-"$VERSION"-*.apk
