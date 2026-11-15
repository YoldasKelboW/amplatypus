# SPDX-License-Identifier: GPL-3.0-or-later
cmake_minimum_required(VERSION 3.22)
project(amplatypus_native C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 17)

# libopenmpt is built from the git submodule. The list of source files is read from
# upstream's own Android.mk so it stays correct when the submodule is updated.
set(OPENMPT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/openmpt)
file(READ ${OPENMPT_DIR}/build/android_ndk/Android.mk OPENMPT_MK)
string(REGEX MATCHALL "(common|libopenmpt|soundlib|sounddsp)/[A-Za-z0-9_/]+\\.cpp" OPENMPT_RELATIVE "${OPENMPT_MK}")
list(REMOVE_DUPLICATES OPENMPT_RELATIVE)
set(OPENMPT_SOURCES "")
foreach(src ${OPENMPT_RELATIVE})
    list(APPEND OPENMPT_SOURCES ${OPENMPT_DIR}/${src})
endforeach()
list(LENGTH OPENMPT_SOURCES OPENMPT_SOURCE_COUNT)
if(OPENMPT_SOURCE_COUNT LESS 100)
    message(FATAL_ERROR "Only ${OPENMPT_SOURCE_COUNT} libopenmpt sources found - is the third_party/openmpt submodule checked out?")
endif()

add_library(openmpt STATIC
    ${OPENMPT_SOURCES}
    ${OPENMPT_DIR}/include/minimp3/minimp3.c
    ${OPENMPT_DIR}/include/stb_vorbis/stb_vorbis.c)
target_include_directories(openmpt
    PUBLIC ${OPENMPT_DIR}
    PRIVATE ${OPENMPT_DIR}/src ${OPENMPT_DIR}/common ${OPENMPT_DIR}/include)
target_compile_definitions(openmpt PRIVATE
    LIBOPENMPT_BUILD MPT_WITH_ZLIB MPT_WITH_MINIMP3 MPT_WITH_STBVORBIS)
target_compile_options(openmpt PRIVATE -fvisibility=hidden -fexceptions -frtti -w)

# The HivelyTracker replayer (AHX and HVL). Its "int8" is a plain char, which is unsigned on ARM,
# and its tables header defines variables, so it needs -fsigned-char and -fcommon.
set(HIVELY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/hivelytracker/Replayer_Windows)
if(NOT EXISTS ${HIVELY_DIR}/hvl_replay.c)
    message(FATAL_ERROR "hvl_replay.c not found - is the third_party/hivelytracker submodule checked out?")
endif()
add_library(hively STATIC ${HIVELY_DIR}/hvl_replay.c ${HIVELY_DIR}/hvl_tables.c)
target_include_directories(hively PUBLIC ${HIVELY_DIR})
target_compile_options(hively PRIVATE -fvisibility=hidden -fsigned-char -fcommon -w)

add_library(amplatypus SHARED engine_jni.cpp engine_openmpt.cpp engine_hively.cpp)
set_source_files_properties(engine_hively.cpp PROPERTIES COMPILE_OPTIONS "-fsigned-char")
target_link_libraries(amplatypus PRIVATE openmpt hively z m log)
target_link_options(amplatypus PRIVATE -Wl,--gc-sections)
