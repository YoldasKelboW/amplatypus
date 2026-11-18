#!/usr/bin/env bash
# Creates the release signing key of AMPlatypus. Run it once, by hand, in a terminal.
# The key stays outside the repository; only you know its password.
set -euo pipefail

KEYTOOL="${JAVA_HOME:-$HOME/Android/jdk-21.0.13+9}/bin/keytool"
DIR="$HOME/AMPlatypus-Key"
KEYSTORE="$DIR/amplatypus-release.jks"

if [ -e "$KEYSTORE" ]; then
    echo "A key already exists at $KEYSTORE - nothing done."
    exit 1
fi
mkdir -p "$DIR"
chmod 700 "$DIR"

echo "Creating $KEYSTORE"
echo "You will be asked for a password twice. Choose a long one and write it down safely."
echo "Without the key and its password the app can never be updated again."
echo
# The certificate only names the GitHub account; it is public in every APK.
"$KEYTOOL" -genkeypair -v \
    -keystore "$KEYSTORE" -storetype PKCS12 \
    -alias amplatypus -keyalg RSA -keysize 4096 -validity 20000 \
    -dname "CN=YoldasKelboW, OU=AMPlatypus"
chmod 600 "$KEYSTORE"

echo
echo "Done: $KEYSTORE"
echo "Now make two backups of the folder $DIR: on your second laptop and on a USB stick."
