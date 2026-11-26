# SPDX-License-Identifier: GPL-3.0-or-later
# ZXTune (LGPL-3.0) by Vitamin/CAIG: the replay routines of the ZX Spectrum trackers and its emulation of
# the AY-3-8910, built from the git submodule. Only the part for AY music is built: no archives, no
# other chips, no Boost (NO_L10N leaves out the translations, which need it). LHA unpacking for VTX files
# comes from lhasa (ISC), and string formatting from fmt (MIT), both bundled with ZXTune.
set(ZXTUNE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/zxtune)
if(NOT EXISTS ${ZXTUNE_DIR}/src/module/players/aym/aym_base.h)
    message(FATAL_ERROR "ZXTune not found - is the third_party/zxtune submodule checked out?")
endif()

add_library(zxtune STATIC
    ${ZXTUNE_DIR}/3rdparty/fmt/src/format.cc
    ${ZXTUNE_DIR}/src/binary/compression/src/lha.cpp
    ${ZXTUNE_DIR}/src/binary/format/composite.cpp
    ${ZXTUNE_DIR}/src/binary/format/expression.cpp
    ${ZXTUNE_DIR}/src/binary/format/full/factory.cpp
    ${ZXTUNE_DIR}/src/binary/format/grammar.cpp
    ${ZXTUNE_DIR}/src/binary/format/lexic_analysis.cpp
    ${ZXTUNE_DIR}/src/binary/format/matching.cpp
    ${ZXTUNE_DIR}/src/binary/format/scanning.cpp
    ${ZXTUNE_DIR}/src/binary/format/static_expression.cpp
    ${ZXTUNE_DIR}/src/binary/format/syntax.cpp
    ${ZXTUNE_DIR}/src/binary/src/base64.cpp
    ${ZXTUNE_DIR}/src/binary/src/container.cpp
    ${ZXTUNE_DIR}/src/binary/src/crc.cpp
    ${ZXTUNE_DIR}/src/binary/src/view.cpp
    ${ZXTUNE_DIR}/src/debug/src/log.cpp
    ${ZXTUNE_DIR}/src/devices/aym/src/aym.cpp
    ${ZXTUNE_DIR}/src/devices/aym/src/turbosound.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/ascsoundmaster.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/ayc.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/fasttracker.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/globaltracker.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/prosoundcreator.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/prosoundmaker_compiled.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/protracker1.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/protracker2.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/protracker3_compiled.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/protracker3_vortex.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/psg.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/soundtracker3.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/soundtracker_compiled.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/soundtracker.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/soundtrackerpro_compiled.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/sqtracker_compiled.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/turbosound.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/aym/ym_vtx.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/common/builder_meta_stub.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/common/container.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/common/decoder.cpp
    ${ZXTUNE_DIR}/src/formats/chiptune/common/metainfo.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/ascsoundmaster.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/ayc.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/aym_base.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/aym_base_stream.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/aym_base_track.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/aym_factory.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/aym_parameters.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/aym_properties_helper.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/fasttracker.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/freq_tables.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/globaltracker.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/prosoundcreator.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/prosoundmaker.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/protracker1.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/protracker2.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/protracker3.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/psg.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/soundtracker.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/soundtrackerpro.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/sqtracker.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/ts.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/turbosound.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/vortex.cpp
    ${ZXTUNE_DIR}/src/module/players/aym/ymvtx.cpp
    ${ZXTUNE_DIR}/src/module/players/duration.cpp
    ${ZXTUNE_DIR}/src/module/players/iterator.cpp
    ${ZXTUNE_DIR}/src/module/players/parameters.cpp
    ${ZXTUNE_DIR}/src/module/players/pipeline.cpp
    ${ZXTUNE_DIR}/src/module/players/properties_helper.cpp
    ${ZXTUNE_DIR}/src/module/players/properties_meta.cpp
    ${ZXTUNE_DIR}/src/module/players/streaming.cpp
    ${ZXTUNE_DIR}/src/module/players/track_builder.cpp
    ${ZXTUNE_DIR}/src/module/players/tracking.cpp
    ${ZXTUNE_DIR}/src/parameters/src/container.cpp
    ${ZXTUNE_DIR}/src/parameters/src/convert.cpp
    ${ZXTUNE_DIR}/src/parameters/src/merged.cpp
    ${ZXTUNE_DIR}/src/parameters/src/serialize.cpp
    ${ZXTUNE_DIR}/src/parameters/src/tracking.cpp
    ${ZXTUNE_DIR}/src/sound/impl/fft_analyzer.cpp
    ${ZXTUNE_DIR}/src/sound/impl/gainer.cpp
    ${ZXTUNE_DIR}/src/sound/impl/mixer.cpp
    ${ZXTUNE_DIR}/src/sound/impl/mixer_params.cpp
    ${ZXTUNE_DIR}/src/sound/impl/parameters.cpp
    ${ZXTUNE_DIR}/src/sound/impl/render_params.cpp
    ${ZXTUNE_DIR}/src/strings/src/casing.cpp
    ${ZXTUNE_DIR}/src/strings/src/encoding.cpp
    ${ZXTUNE_DIR}/src/strings/src/format.cpp
    ${ZXTUNE_DIR}/src/strings/src/optimize.cpp
    ${ZXTUNE_DIR}/src/strings/src/prefixed_index.cpp
    ${ZXTUNE_DIR}/src/strings/src/sanitize.cpp
    ${ZXTUNE_DIR}/src/strings/src/template.cpp
    ${ZXTUNE_DIR}/src/strings/src/trim.cpp
    ${ZXTUNE_DIR}/src/tools/src/error.cpp
    ${ZXTUNE_DIR}/src/tools/src/progress_callback.cpp
    ${ZXTUNE_DIR}/src/tools/src/range_checker.cpp
    ${ZXTUNE_DIR}/3rdparty/lhasa/lib/crc16.c
    ${ZXTUNE_DIR}/3rdparty/lhasa/lib/lha_decoder.c
    ${ZXTUNE_DIR}/3rdparty/lhasa/lib/null_decoder.c
    ${ZXTUNE_DIR}/3rdparty/lhasa/lib/lh1_decoder.c
    ${ZXTUNE_DIR}/3rdparty/lhasa/lib/lh5_decoder.c
    ${ZXTUNE_DIR}/3rdparty/lhasa/lib/lh6_decoder.c
    ${ZXTUNE_DIR}/3rdparty/lhasa/lib/lh7_decoder.c
    ${ZXTUNE_DIR}/3rdparty/lhasa/lib/lz5_decoder.c
    ${ZXTUNE_DIR}/3rdparty/lhasa/lib/lzs_decoder.c
    ${ZXTUNE_DIR}/3rdparty/lhasa/lib/pm2_decoder.c
)
target_include_directories(zxtune PUBLIC ${ZXTUNE_DIR}/include ${ZXTUNE_DIR}/src ${ZXTUNE_DIR}
    ${ZXTUNE_DIR}/3rdparty/fmt/include)
target_include_directories(zxtune PRIVATE ${ZXTUNE_DIR}/3rdparty/lhasa/lib)
target_compile_definitions(zxtune PRIVATE NDEBUG)
# fmt 9.1 checks format strings with consteval, which newer clang rejects inside fmt itself; without the
# check the strings are checked at run time, as before C++20.
target_compile_definitions(zxtune PUBLIC FMT_CONSTEVAL=)
target_compile_definitions(zxtune PUBLIC NO_L10N
    # ZXTune names its sources in error messages relative to this folder. The build maps the repository's
    # path away (-ffile-prefix-map), so __FILE__ starts here.
    SOURCES_ROOT="third_party/zxtune")
target_compile_features(zxtune PUBLIC cxx_std_20)
target_compile_options(zxtune PRIVATE -fvisibility=hidden -w -O2)
set_source_files_properties(${ZXTUNE_DIR}/src/strings/src/encoding.cpp PROPERTIES
    COMPILE_OPTIONS "-include;${CMAKE_CURRENT_SOURCE_DIR}/char_traits_u16.h")
