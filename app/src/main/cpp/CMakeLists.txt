# SPDX-License-Identifier: GPL-3.0-or-later
cmake_minimum_required(VERSION 3.22)
project(amplatypus_native C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 17)

# Reproducible builds: source paths end up in the libraries (assert messages, __FILE__), so the
# checkout folder is cut off. Otherwise a build in another folder, as on F-Droid's server, differs.
get_filename_component(REPO_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../.. ABSOLUTE)
add_compile_options(-ffile-prefix-map=${REPO_ROOT}/=)

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

# UADE: libuade is linked into the app's library, uadecore is a program of its own. Gradle only
# packages files called lib*.so, so the program gets such a name and is put next to the libraries.
add_subdirectory(uade)
set_target_properties(uadecore PROPERTIES
    OUTPUT_NAME uadecore PREFIX "lib" SUFFIX ".so"
    RUNTIME_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY})

add_library(amplatypus SHARED engine_jni.cpp engine_openmpt.cpp engine_hively.cpp engine_uade.cpp)
set_source_files_properties(engine_hively.cpp PROPERTIES COMPILE_OPTIONS "-fsigned-char")
target_link_libraries(amplatypus PRIVATE openmpt hively uade z m log)
target_link_options(amplatypus PRIVATE -Wl,--gc-sections)
# Nothing links against uadecore, so make sure it is built along with the library.
add_dependencies(amplatypus uadecore)
