# SPDX-License-Identifier: GPL-3.0-or-later
# StSound (MIT) by Arnaud Carré: YM files, register dumps of the Atari ST's (and Amstrad CPC's)
# YM-2149 sound chip, LHA-packed. Built from the git submodule.
set(STSOUND_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/StSound/StSoundLibrary)
if(NOT EXISTS ${STSOUND_DIR}/StSoundLibrary.h)
    message(FATAL_ERROR "StSound not found - is the third_party/StSound submodule checked out?")
endif()
add_library(stsound STATIC
    ${STSOUND_DIR}/digidrum.cpp ${STSOUND_DIR}/Ym2149Ex.cpp ${STSOUND_DIR}/Ymload.cpp ${STSOUND_DIR}/YmMusic.cpp
    ${STSOUND_DIR}/YmUserInterface.cpp ${STSOUND_DIR}/LZH/LzhLib.cpp)
target_include_directories(stsound PUBLIC ${STSOUND_DIR})
target_compile_options(stsound PRIVATE -fvisibility=hidden -w -O2)
