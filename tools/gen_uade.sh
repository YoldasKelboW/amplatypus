#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Regenerates app/src/main/cpp/uade/gen from the UADE submodule. Run it after updating
# third_party/uade and commit the result.
#
# UADE's own build writes these files with its configure script and with two helper programs
# (build68k, gencpu) that must run on the build machine. The Android build cross-compiles, so
# the files are generated once here and kept in the repository.
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
uade="$root/third_party/uade"
out="$root/app/src/main/cpp/uade/gen"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cc=${CC:-cc}

rm -rf "$out"
mkdir -p "$out/uade"

# The 68k emulation: table68k -> cpudefs.c -> cpuemu.c, cpustbl.c, cputbl.h.
inc="-I$out -I$uade/src -I$uade/src/include -I$uade/src/frontends/include"
$cc -O1 -w $inc -o "$work/build68k" "$uade/src/build68k.c"
"$work/build68k" < "$uade/src/table68k" > "$out/cpudefs.c"
$cc -O1 -w $inc -o "$work/gencpu" "$uade/src/gencpu.c" "$uade/src/readcpu.c" "$out/cpudefs.c" "$uade/src/missing.c"
(cd "$out" && "$work/gencpu")

# What configure writes for a Unix target.
printf '#include "sd-sound-generic.c"\n' > "$out/sd-sound.c"
printf '#include "sd-sound-generic.h"\n' > "$out/sd-sound.h"

cat > "$out/uade/sysincludes.h" <<EOT
#include <netinet/in.h>
#include <sys/select.h>
EOT

cat > "$out/uade/compilersupport.h" <<EOT
#ifndef _UADE_COMPILER_SUPPORT_H_
#define _UADE_COMPILER_SUPPORT_H_
#define likely(x)	__builtin_expect(!!(x), 1)
#define unlikely(x)	__builtin_expect(!!(x), 0)
#endif
EOT

# Android's C library has memmem() but no canonicalize_file_name(), so that replacement is used.
{
    printf '#ifndef _UADE_OSSUPPORT_H_\n#define _UADE_OSSUPPORT_H_\n\n'
    printf '#include <uade/unixsupport.h>\n#include <zakalwe/string.h>\n\n'
    cat "$uade/compat/canonrep.h"
    printf '#endif\n'
} > "$out/uade/ossupport.h"
{
    printf '#include <uade/ossupport.h>\n\n'
    cat "$uade/src/frontends/common/unixsupport.c"
    cat "$uade/compat/canonrep.c"
} > "$out/ossupport.c"

# Paths are set at run time by the app, so the compiled-in defaults are placeholders.
cat > "$out/uade/options.h" <<EOT
#ifndef _UADE_OPTIONS_H_
#define _UADE_OPTIONS_H_
#define UADE_CONFIG_USER_MODE (0)
#define UADE_CONFIG_BASE_DIR "/nonexistent/uade"
#define UADE_CONFIG_UADE_CORE "/nonexistent/uadecore"
#define UADE_CONFIG_HAVE_URANDOM
#define UADE_VERSION "$(cat "$uade/version")"

#endif /* _UADE_OPTIONS_H_ */
EOT

# What libzakalwe's configure writes. Android gained reallocarray() in API 29.
mkdir -p "$out/zakalwe"
printf '#include <zakalwe/ztree.h>\n' > "$out/zakalwe/tree.h"
cat > "$out/zakalwe/config.h" <<EOT
#ifndef _Z_CONFIG_H_
#define _Z_CONFIG_H_

#if defined(__ANDROID__) && __ANDROID_API__ < 29
#define _Z_NEED_REALLOCARRAY 1
#endif
#define _Z_SYSCALL_SELECT 1
#define _Z_HAS_MKDTEMP 1

#endif
EOT

echo "Generated from UADE $(cat "$uade/version"), commit $(git -C "$uade" rev-parse --short HEAD)." > "$out/README"
ls "$out" "$out/uade" "$out/zakalwe"
