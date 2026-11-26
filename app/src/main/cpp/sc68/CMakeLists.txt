# SPDX-License-Identifier: GPL-3.0-or-later
# sc68 (GPL-3.0-or-later) by Benjamin Gerard: Atari ST music in SNDH and SC68 files, played by its
# 68000 emulation (emu68) with the Atari ST's YM-2149 sound chip and MFP timer (io68). Built from the
# git submodule; the one header configure would generate is in gen/.
set(SC68_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/sc68)
if(NOT EXISTS ${SC68_DIR}/libsc68/src/api68.c)
    message(FATAL_ERROR "sc68 not found - is the third_party/sc68 submodule checked out?")
endif()

# The source lists of upstream's Makefile.am files.
set(SC68_SOURCES "")
foreach(f api68 conf68 libsc68 mixer68)
    list(APPEND SC68_SOURCES ${SC68_DIR}/libsc68/src/${f}.c)
endforeach()
foreach(f emu68 error68 getea68 inst68 ioplug68 mem68 line0_68 line1_68 line2_68 line3_68 line4_68 line5_68
          line6_68 line7_68 line8_68 line9_68 lineA_68 lineB_68 lineC_68 lineD_68 lineE_68 lineF_68 table68)
    list(APPEND SC68_SOURCES ${SC68_DIR}/libsc68/emu68/${f}.c)
endforeach()
foreach(f io68 mfp_io mfpemul mw_io mwemul paula_io paulaemul shifter_io ym_envel ym_blep ym_dump ym_io ym_puls ymemul)
    list(APPEND SC68_SOURCES ${SC68_DIR}/libsc68/io68/${f}.c)
endforeach()
# Dialogs of desktop players; the library calls into them but the app never opens one.
foreach(f dial68 dial_conf dial_tsel dial_finf)
    list(APPEND SC68_SOURCES ${SC68_DIR}/libsc68/dial68/${f}.c)
endforeach()
file(GLOB FILE68_SOURCES CONFIGURE_DEPENDS ${SC68_DIR}/file68/src/*.c)
# The network and audio-output file systems compile to stubs without FILE68_CURL and FILE68_AO.
list(APPEND SC68_SOURCES ${FILE68_SOURCES}
    ${SC68_DIR}/unice68/unice68_unpack.c
    ${SC68_DIR}/unice68/unice68_version.c)

add_library(sc68 STATIC ${SC68_SOURCES})
target_include_directories(sc68
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/gen ${CMAKE_CURRENT_SOURCE_DIR}/gen/sc68 ${SC68_DIR}/libsc68 ${SC68_DIR}/file68 ${SC68_DIR}/unice68
    PRIVATE ${SC68_DIR}/libsc68/sc68 ${SC68_DIR}/libsc68/emu68 ${SC68_DIR}/libsc68/io68 ${SC68_DIR}/file68/sc68
            ${SC68_DIR}/file68/src ${SC68_DIR}/libsc68/src)
# NDEBUG: sc68's asserts would end the whole app on odd files. USE_REPLAY68: the replay routines some
# SC68 files need are built in (file68/src/replay.inc.h), not loaded from disk or the web.
target_compile_definitions(sc68 PRIVATE
    HAVE_CONFIG_H=1 HAVE_STDINT_H=1 HAVE_STDLIB_H=1 HAVE_STRING_H=1 HAVE_LIMITS_H=1 HAVE_ASSERT_H=1 HAVE_UNISTD_H=1
    HAVE_FCNTL_H=1 HAVE_SYS_TYPES_H=1 HAVE_FSYNC=1 HAVE_LIBGEN_H=1 HAVE_BASENAME=1 HAVE_TIMEDB_INC_H=1
    NDEBUG=1 USE_REPLAY68=1 HAVE_ZLIB_H=1 USE_UNICE68=1 USE_Z=1 EMU68_EXPORT=1 IO68_EXPORT=1 FILE68_EXPORT=1 SC68_EXPORT=1
    PACKAGE_STRING="sc68" PACKAGE_VERSION="3.0.0" PACKAGE_URL="http://sc68.atari.org")
target_compile_options(sc68 PRIVATE -fvisibility=hidden -w -O2)
# Its headers need these too where the app includes them.
target_compile_definitions(sc68 PUBLIC HAVE_STDINT_H=1 HAVE_SYS_TYPES_H=1)
target_link_libraries(sc68 PUBLIC z)
