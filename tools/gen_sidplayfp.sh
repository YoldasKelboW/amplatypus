#!/usr/bin/env bash
# Regenerates app/src/main/cpp/sidplayfp/gen/ for the libsidplayfp submodule (third_party/libsidplayfp).
#
# libsidplayfp's configure script writes a few headers, and its build assembles three small 6502
# programs (the PSID driver and Compute's Sidplayer) with the xa65 assembler into C byte arrays.
# The headers are written here from the templates. The three .bin files are taken from the
# official release archive of the same version, which ships them already assembled; with xa65
# installed they can instead be rebuilt from the .a65 sources in the submodule (see its Makefile.am).
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=2.9.0
SRC=third_party/libsidplayfp/src
GEN=app/src/main/cpp/sidplayfp/gen
mkdir -p "$GEN/sidplayfp" "$GEN/residfp"

IFS=. read -r MAJ MIN LEV <<< "$VERSION"
sed -e "s/@LIB_MAJOR@/$MAJ/;s/@LIB_MINOR@/$MIN/;s/@LIB_LEVEL@/$LEV/" \
    "$SRC/sidplayfp/sidversion.h.in" > "$GEN/sidplayfp/sidversion.h"
sed -e "s/@RESID_BRANCH_HINTS@/1/;s/@HAVE_BUILTIN_EXPECT@/1/;s/@PACKAGE_VERSION@/$VERSION/;s/@RESID_INLINING@/1/;s/@RESID_INLINE@/inline/" \
    "$SRC/builders/residfp-builder/residfp/siddefs-fp.h.in" > "$GEN/residfp/siddefs-fp.h"

if command -v xa >/dev/null; then
    for a65 in "$SRC/psiddrv.a65" "$SRC/sidtune/sidplayer1.a65" "$SRC/sidtune/sidplayer2.a65"; do
        o65=$(mktemp)
        xa -R -G "$a65" -o "$o65"
        od -v -An -w8 -tx1 "$o65" | sed -re 's/[[:alnum:]]+/0x&,/g' > "$GEN/$(basename "${a65%.a65}").bin"
        rm "$o65"
    done
else
    tmp=$(mktemp -d)
    curl -sSfL -o "$tmp/release.tar.gz" \
        "https://github.com/libsidplayfp/libsidplayfp/releases/download/v$VERSION/libsidplayfp-$VERSION.tar.gz"
    echo "5143f621841efe1abc4646fe5389b24d3d6b73752f38049112ecb8fd69eddd4a  $tmp/release.tar.gz" | sha256sum -c -
    tar -xzf "$tmp/release.tar.gz" -C "$tmp"
    cp "$tmp/libsidplayfp-$VERSION/src/psiddrv.bin" "$GEN/"
    cp "$tmp/libsidplayfp-$VERSION/src/sidtune/sidplayer1.bin" "$tmp/libsidplayfp-$VERSION/src/sidtune/sidplayer2.bin" "$GEN/"
    rm -rf "$tmp"
fi
echo "Done. config.h in $GEN is written by hand."
