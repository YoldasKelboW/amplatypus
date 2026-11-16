# SPDX-License-Identifier: GPL-3.0-or-later
#
# UADE (Unix Amiga Delitracker Emulator) for the exotic Amiga formats. Two things are built:
#   uadecore - the Amiga emulator, a program of its own that libuade starts as a child process
#   uade     - libuade, the static library the app talks to
# Files that UADE's configure and helper programs would generate are kept in gen/
# (see tools/gen_uade.sh).
set(UADE_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/uade)
set(BENCODE_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/bencodetools)
set(ZAKALWE_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/libzakalwe)
set(UADE_GEN ${CMAKE_CURRENT_LIST_DIR}/gen)
foreach(needed ${UADE_DIR}/src/uademain.c ${BENCODE_DIR}/bencode.c ${ZAKALWE_DIR}/array.c)
    if(NOT EXISTS ${needed})
        message(FATAL_ERROR "${needed} not found - are the third_party submodules checked out?")
    endif()
endforeach()

set(UADE_INCLUDES
    ${UADE_GEN} ${UADE_DIR}/src ${UADE_DIR}/src/include ${UADE_DIR}/src/frontends/include
    ${UADE_DIR}/src/frontends/common ${ZAKALWE_DIR}/include ${BENCODE_DIR}/include)

set(ZAKALWE_SOURCES "")
foreach(name array base bitfield endianess file input math mem norm_inv_table_65536 random string str_array time unix-support)
    list(APPEND ZAKALWE_SOURCES ${ZAKALWE_DIR}/${name}.c)
endforeach()
add_library(zakalwe STATIC ${ZAKALWE_SOURCES})
target_include_directories(zakalwe PUBLIC ${UADE_GEN} ${ZAKALWE_DIR}/include)
target_compile_options(zakalwe PRIVATE -fvisibility=hidden -w)
target_compile_definitions(zakalwe PUBLIC _GNU_SOURCE)

add_library(bencodetools STATIC ${BENCODE_DIR}/bencode.c)
target_include_directories(bencodetools PUBLIC ${BENCODE_DIR}/include)
target_compile_options(bencodetools PRIVATE -fvisibility=hidden -w)

# libuade
set(UADE_LIB_SOURCES ${UADE_GEN}/ossupport.c)
foreach(name unixatomic uadeipc amifilemagic eagleplayer unixwalkdir effects uadecontrol uadeconf
        uadestate uadeutils md5 rmc songdb songinfo vparray support fifo)
    list(APPEND UADE_LIB_SOURCES ${UADE_DIR}/src/frontends/common/${name}.c)
endforeach()
add_library(uade STATIC ${UADE_LIB_SOURCES})
target_include_directories(uade PUBLIC ${UADE_INCLUDES})
target_compile_options(uade PRIVATE -fvisibility=hidden -w)
target_link_libraries(uade PUBLIC bencodetools zakalwe m)

# The emulator. The generated 68k code is one huge file that upstream compiles in eight parts.
set(UADE_CORE_SOURCES
    ${UADE_GEN}/cpustbl.c ${UADE_GEN}/cpudefs.c ${UADE_GEN}/sd-sound.c ${UADE_GEN}/ossupport.c
    ${UADE_DIR}/src/frontends/common/uadeipc.c ${UADE_DIR}/src/frontends/common/uadeutils.c
    ${UADE_DIR}/src/frontends/common/unixatomic.c)
foreach(name main newcpu memory custom cia audio missing md-support cfgfile fpp debug readcpu uade
        uademain sinctable text_scope write_audio state_detection uade_logging binary_deduper)
    list(APPEND UADE_CORE_SOURCES ${UADE_DIR}/src/${name}.c)
endforeach()
add_executable(uadecore ${UADE_CORE_SOURCES})
foreach(part RANGE 1 8)
    add_library(uadecpu${part} OBJECT ${UADE_GEN}/cpuemu.c)
    target_compile_definitions(uadecpu${part} PRIVATE PART_${part})
    target_include_directories(uadecpu${part} PRIVATE ${UADE_INCLUDES})
    target_compile_options(uadecpu${part} PRIVATE -fomit-frame-pointer -w)
    target_sources(uadecore PRIVATE $<TARGET_OBJECTS:uadecpu${part}>)
endforeach()
target_include_directories(uadecore PRIVATE ${UADE_INCLUDES})
target_compile_options(uadecore PRIVATE -fomit-frame-pointer -w)
target_link_libraries(uadecore PRIVATE zakalwe m)
