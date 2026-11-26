# SPDX-License-Identifier: GPL-3.0-or-later
# NEC PC-98 music: the FMP and PMD sound drivers re-implemented by 98fmplayer (BSD-2-Clause, Takamichi
# Horikawa), on its emulation of the YM2608 (OPNA) and of the PPZ8 PCM driver. Built from the git
# submodule; the front-end parts are replaced by pc98_files.c.
set(FMPLAYER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/98fmplayer)
if(NOT EXISTS ${FMPLAYER_DIR}/fmdriver/fmdriver.h)
    message(FATAL_ERROR "98fmplayer not found - is the third_party/98fmplayer submodule checked out?")
endif()
add_library(pc98 STATIC
    ${FMPLAYER_DIR}/common/fmplayer_file.c
    ${FMPLAYER_DIR}/common/fmplayer_work_opna.c
    ${FMPLAYER_DIR}/fmdriver/fmdriver_common.c
    ${FMPLAYER_DIR}/fmdriver/fmdriver_fmp.c
    ${FMPLAYER_DIR}/fmdriver/fmdriver_pmd.c
    ${FMPLAYER_DIR}/fmdriver/ppz8.c
    ${FMPLAYER_DIR}/libopna/opna.c
    ${FMPLAYER_DIR}/libopna/opnaadpcm.c
    ${FMPLAYER_DIR}/libopna/opnadrum.c
    ${FMPLAYER_DIR}/libopna/opnafm.c
    ${FMPLAYER_DIR}/libopna/opnassg.c
    ${FMPLAYER_DIR}/libopna/opnassg-sinc-c.c
    ${FMPLAYER_DIR}/libopna/opnatimer.c
    pc98_files.c
    pc98_chip.c)
target_include_directories(pc98 PRIVATE ${FMPLAYER_DIR})
target_include_directories(pc98 PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(pc98 PRIVATE -fvisibility=hidden -w -O2)
