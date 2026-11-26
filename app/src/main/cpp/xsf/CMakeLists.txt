# SPDX-License-Identifier: GPL-3.0-or-later
# Console music ripped as xSF files, played by the emulators ZXTune bundles (third_party/zxtune/3rdparty)
# and ZXTune's xSF players. Each emulator is built on its own, with the switches ZXTune's Makefiles use.
#   mGBA (MPL-2.0) - Game Boy Advance (GSF); it brings its own replacement for the GBA's BIOS.
#   vio2sf (GPL-2.0-or-later, from DeSmuME) - Nintendo DS (2SF).
#   lazyusf2 (GPL-2.0-or-later, from Mupen64plus) - Nintendo 64 (USF).
#   Highly Theoretical (GPL-3.0, Neill Corlett) - Sega Saturn and Dreamcast (SSF, DSF).
# PlayStation (PSF) is left out: ZXTune's PSF player carries a Sony BIOS, which cannot be shipped.
set(ZXTUNE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/zxtune)

add_library(xsf_mgba STATIC
    ${ZXTUNE_DIR}/3rdparty/mgba/src/arm/arm.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/arm/decoder-arm.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/arm/decoder-thumb.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/arm/isa-arm.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/arm/isa-thumb.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/core/cheats.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/core/config.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/core/core.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/core/interface.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/core/sync.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/core/tile-cache.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/core/timing.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gb/gb_audio.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/cheats/codebreaker.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/cheats/gameshark.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/cheats/parv3.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_audio.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_bios.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_cheats.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_core.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_dma.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_hardware.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_io.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_memory.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_overrides.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_savedata.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_serialize.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_sio.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_timer.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_vfame.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/gba_video.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/gba/hle-bios.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/platform/portable/memory.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/third-party/blip_buf/blip_buf.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/third-party/inih/ini.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/circle-buffer.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/configuration.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/crc32.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/formatting.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/hash.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/patch-ips.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/patch-ups.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/patch.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/string.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/table.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/vfs.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/vfs/vfs-file.c
    ${ZXTUNE_DIR}/3rdparty/mgba/src/util/vfs/vfs-mem.c
)
target_include_directories(xsf_mgba PRIVATE ${ZXTUNE_DIR}/3rdparty/mgba/include ${ZXTUNE_DIR}/3rdparty/mgba/src)
target_compile_definitions(xsf_mgba PRIVATE HAVE_LOCALTIME_R)
target_compile_options(xsf_mgba PRIVATE -include ${ZXTUNE_DIR}/3rdparty/mgba/defines.h -fvisibility=hidden -w -O2)

add_library(xsf_vio2sf STATIC
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/FIFO.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/MMU.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/NDSSystem.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/SPU.cpp
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/arm_instructions.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/armcpu.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/bios.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/cp15.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/isqrt.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/matrix.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/mc.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/resampler.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/state.c
    ${ZXTUNE_DIR}/3rdparty/vio2sf/desmume/thumb_instructions.c
)
target_include_directories(xsf_vio2sf PRIVATE ${ZXTUNE_DIR})
target_compile_definitions(xsf_vio2sf PRIVATE NO_ASM)
target_compile_options(xsf_vio2sf PRIVATE -fvisibility=hidden -w -O2)

add_library(xsf_lazyusf2 STATIC
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/ai/ai_controller.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/api/callbacks.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/main/main.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/main/rom.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/main/savestates.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/main/util.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/memory/memory.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/pi/cart_rom.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/pi/pi_controller.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/cached_interp.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/cp0.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/cp1.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/exception.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/interupt.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/mi_controller.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/pure_interp.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/r4300.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/r4300_core.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/recomp.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/reset.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/r4300/tlb.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rdp/rdp_core.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/ri/rdram.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/ri/rdram_detection_hack.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/ri/ri_controller.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp/rsp_core.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/alist.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/alist_audio.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/alist_naudio.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/alist_nead.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/audio.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/cicx105.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/hle.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/hvqm.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/jpeg.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/memory.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/mp3.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/musyx.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/plugin.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_hle/re2.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/rsp_lle/rsp.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/si/cic.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/si/game_controller.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/si/n64_cic_nus_6105.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/si/pif.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/si/si_controller.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/usf/resampler.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/usf/usf.c
    ${ZXTUNE_DIR}/3rdparty/lazyusf2/vi/vi_controller.c
)
target_include_directories(xsf_lazyusf2 PRIVATE ${ZXTUNE_DIR}/3rdparty/lazyusf2 ${ZXTUNE_DIR})
target_compile_definitions(xsf_lazyusf2 PRIVATE NO_TRIMMING)
target_compile_options(xsf_lazyusf2 PRIVATE -fvisibility=hidden -w -O2)

add_library(xsf_ht STATIC
    ${ZXTUNE_DIR}/3rdparty/ht/Core/arm.c
    ${ZXTUNE_DIR}/3rdparty/ht/Core/c68k/c68k.c
    ${ZXTUNE_DIR}/3rdparty/ht/Core/c68k/c68kexec.c
    ${ZXTUNE_DIR}/3rdparty/ht/Core/dcsound.c
    ${ZXTUNE_DIR}/3rdparty/ht/Core/satsound.c
    ${ZXTUNE_DIR}/3rdparty/ht/Core/sega.c
    ${ZXTUNE_DIR}/3rdparty/ht/Core/yam.c
)
target_include_directories(xsf_ht PRIVATE ${ZXTUNE_DIR})
target_compile_definitions(xsf_ht PRIVATE EMU_COMPILE EMU_LITTLE_ENDIAN LSB_FIRST C68K_NO_JUMP_TABLE NO_DYNAREC HAVE_STDINT_H)
target_compile_options(xsf_ht PRIVATE -fvisibility=hidden -w -O2)

# ZXTune's xSF readers and players, built into its library; they see the emulators' headers.
target_sources(zxtune PRIVATE
    ${ZXTUNE_DIR}/src/binary/compression/src/zlib.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/emulation/dreamcastsoundformat.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/emulation/gameboyadvancesoundformat.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/emulation/nintendodssoundformat.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/emulation/portablesoundformat.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/emulation/segasaturnsoundformat.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/emulation/ultra64soundformat.cpp
    ${ZXTUNE_DIR}/src/module/players/xsf/2sf.cpp
    ${ZXTUNE_DIR}/src/module/players/xsf/gsf.cpp
    ${ZXTUNE_DIR}/src/module/players/xsf/gsf_rom.cpp
    ${ZXTUNE_DIR}/src/module/players/xsf/memory_region.cpp
    ${ZXTUNE_DIR}/src/module/players/xsf/sdsf.cpp
    ${ZXTUNE_DIR}/src/module/players/xsf/usf.cpp
    ${ZXTUNE_DIR}/src/module/players/xsf/xsf.cpp
    ${ZXTUNE_DIR}/src/module/players/xsf/xsf_factory.cpp
    ${ZXTUNE_DIR}/src/module/players/xsf/xsf_metainformation.cpp
    ${ZXTUNE_DIR}/src/sound/impl/resampler.cpp)
target_include_directories(zxtune PRIVATE ${ZXTUNE_DIR}/3rdparty/mgba/include ${ZXTUNE_DIR}/3rdparty/mgba/src
    ${ZXTUNE_DIR}/3rdparty/lazyusf2)
# Highly Theoretical's headers need its switches; only the Saturn/Dreamcast player includes them.
set_source_files_properties(${ZXTUNE_DIR}/src/module/players/xsf/sdsf.cpp TARGET_DIRECTORY zxtune
    PROPERTIES COMPILE_DEFINITIONS "EMU_COMPILE;EMU_LITTLE_ENDIAN;LSB_FIRST;HAVE_STDINT_H")
target_link_libraries(zxtune PUBLIC xsf_mgba xsf_vio2sf xsf_lazyusf2 xsf_ht z)
