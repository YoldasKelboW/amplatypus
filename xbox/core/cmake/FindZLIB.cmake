# SPDX-License-Identifier: GPL-3.0-or-later
# Answers Game Music Emu's find_package(ZLIB) with the zlib built in xbox/core/CMakeLists.txt.
if(NOT TARGET ZLIB::ZLIB)
    add_library(ZLIB::ZLIB ALIAS amp_zlib)
endif()
get_target_property(ZLIB_INCLUDE_DIRS amp_zlib INTERFACE_INCLUDE_DIRECTORIES)
set(ZLIB_INCLUDE_DIR ${ZLIB_INCLUDE_DIRS})
set(ZLIB_LIBRARIES ZLIB::ZLIB)
set(ZLIB_FOUND TRUE)
