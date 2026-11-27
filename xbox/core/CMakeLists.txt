# SPDX-License-Identifier: GPL-3.0-or-later
# The Android app's players, built for UWP as one DLL (amplatypus_core.dll) with a small C interface
# (core_api.h) that the Xbox app calls through P/Invoke. libopenmpt comes as its own DLL.
#
# Configure for UWP: cmake -G "Visual Studio 17 2022" -A x64 -DCMAKE_SYSTEM_NAME=WindowsStore
#                          -DCMAKE_SYSTEM_VERSION=10.0 xbox/core
cmake_minimum_required(VERSION 3.22)
project(amplatypus_core C CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# The static libraries end up in a DLL.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# UWP projects treat MSVC's "unsafe CRT function" warnings as errors (/sdl); the players' C code uses
# fopen, strncpy and friends as the C standard has them.
if(MSVC)
    add_compile_definitions(_CRT_SECURE_NO_WARNINGS _CRT_NONSTDC_NO_DEPRECATE)
    add_compile_options(/sdl-)
endif()

set(REPO ${CMAKE_CURRENT_SOURCE_DIR}/../..)
set(ENGINES ${REPO}/app/src/main/cpp)

# zlib from the copy libopenmpt keeps (third_party/openmpt/include/zlib); cmake/FindZLIB.cmake hands it
# to Game Music Emu, which looks for zlib with find_package.
file(GLOB ZLIB_SOURCES ${REPO}/third_party/openmpt/include/zlib/*.c)
add_library(amp_zlib STATIC ${ZLIB_SOURCES})
target_include_directories(amp_zlib PUBLIC ${REPO}/third_party/openmpt/include/zlib)
target_compile_definitions(amp_zlib PRIVATE _CRT_SECURE_NO_WARNINGS _CRT_NONSTDC_NO_DEPRECATE)
# Outside Windows (a quick check build on Linux) zlib's file functions need unistd.h.
if(NOT WIN32)
    target_compile_definitions(amp_zlib PRIVATE Z_HAVE_UNISTD_H)
endif()
list(PREPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)

# Game Music Emu: consoles and home computers, with the Nuked YM2612, as in the Android build.
set(GME_BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(GME_BUILD_STATIC ON CACHE BOOL "" FORCE)
set(GME_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(GME_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GME_ENABLE_UBSAN OFF CACHE BOOL "" FORCE)
set(GME_YM2612_EMU "Nuked" CACHE STRING "" FORCE)
set(GME_ZLIB ON CACHE BOOL "" FORCE)
add_subdirectory(${REPO}/third_party/game-music-emu ${CMAKE_CURRENT_BINARY_DIR}/gme EXCLUDE_FROM_ALL)
target_sources(gme_static PRIVATE ${ENGINES}/gme_peek.cpp)
target_include_directories(gme_static PRIVATE ${REPO}/third_party/game-music-emu/gme)

# HivelyTracker replayer (AHX, HVL).
set(HIVELY_DIR ${REPO}/third_party/hivelytracker/Replayer_Windows)
add_library(hively STATIC ${HIVELY_DIR}/hvl_replay.c ${HIVELY_DIR}/hvl_tables.c)
target_include_directories(hively PUBLIC ${HIVELY_DIR})
target_compile_definitions(hively PRIVATE _CRT_SECURE_NO_WARNINGS)
# Its tables are defined in a header that two files include; MSVC merges them as C does by tradition,
# GCC and clang only with -fcommon (as in the Android build).
if(NOT MSVC)
    target_compile_options(hively PRIVATE -fcommon -fsigned-char)
endif()

add_library(amplatypus_core SHARED
    core_api.cpp
    ${ENGINES}/engine_gme.cpp
    ${ENGINES}/engine_hively.cpp)
target_include_directories(amplatypus_core PRIVATE ${ENGINES})
# M_PI and friends.
target_compile_definitions(amplatypus_core PRIVATE _USE_MATH_DEFINES _CRT_SECURE_NO_WARNINGS AMP_CORE_EXPORTS)
target_link_libraries(amplatypus_core PRIVATE gme_static hively amp_zlib)
