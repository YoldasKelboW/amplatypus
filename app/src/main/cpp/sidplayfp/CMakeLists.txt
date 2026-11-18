# SPDX-License-Identifier: GPL-3.0-or-later
# libsidplayfp (GPL-2.0-or-later) with the reSIDfp emulation of the C64's SID chip, for SID and
# Sidplayer (MUS/STR) files. Built from the git submodule; the files configure would generate are in
# gen/ (see tools/gen_sidplayfp.sh).
set(SIDPLAYFP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/libsidplayfp/src)
if(NOT EXISTS ${SIDPLAYFP_DIR}/player.cpp)
    message(FATAL_ERROR "libsidplayfp not found - is the third_party/libsidplayfp submodule checked out?")
endif()

file(GLOB_RECURSE SIDPLAYFP_SOURCES CONFIGURE_DEPENDS
    ${SIDPLAYFP_DIR}/*.cpp)
# Left out: the other emulations and hardware SID drivers, the STIL reader, tests.
list(FILTER SIDPLAYFP_SOURCES EXCLUDE REGEX "/builders/(resid-builder|hardsid-builder|exsid-builder)/")
list(FILTER SIDPLAYFP_SOURCES EXCLUDE REGEX "/utils/STILview/")
list(FILTER SIDPLAYFP_SOURCES EXCLUDE REGEX "/resample/test\\.cpp$")
list(FILTER SIDPLAYFP_SOURCES EXCLUDE REGEX "/test[^/]*/")
list(FILTER SIDPLAYFP_SOURCES EXCLUDE REGEX "/utils/md5Gcrypt")

add_library(sidplayfp STATIC ${SIDPLAYFP_SOURCES})
target_include_directories(sidplayfp
    PUBLIC ${SIDPLAYFP_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/gen
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/gen/residfp ${SIDPLAYFP_DIR}/builders/residfp-builder ${SIDPLAYFP_DIR}/builders/residfp-builder/residfp ${SIDPLAYFP_DIR}/sidplayfp)
target_compile_definitions(sidplayfp PRIVATE HAVE_CONFIG_H)
target_compile_options(sidplayfp PRIVATE -fvisibility=hidden -w -O2)
