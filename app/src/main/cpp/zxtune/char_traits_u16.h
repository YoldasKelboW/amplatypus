// SPDX-License-Identifier: GPL-3.0-or-later
//
// ZXTune reads UTF-16 titles through std::basic_string_view<uint16_t>. libc++ dropped its generic
// std::char_traits in LLVM 19, so the NDK no longer has one for unsigned short. This one does what the
// generic one did; it is included only into ZXTune's strings/src/encoding.cpp.
#pragma once

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <ios>
#include <string>

namespace std {

template <>
struct char_traits<unsigned short> {
    using char_type = unsigned short;
    using int_type = unsigned int;
    using off_type = streamoff;
    using pos_type = streampos;
    using state_type = mbstate_t;

    static constexpr void assign(char_type &r, const char_type &a) noexcept { r = a; }
    static constexpr bool eq(char_type a, char_type b) noexcept { return a == b; }
    static constexpr bool lt(char_type a, char_type b) noexcept { return a < b; }

    static constexpr int compare(const char_type *a, const char_type *b, size_t n) noexcept {
        for (size_t i = 0; i < n; i++) {
            if (a[i] < b[i]) return -1;
            if (b[i] < a[i]) return 1;
        }
        return 0;
    }

    static constexpr size_t length(const char_type *s) noexcept {
        size_t n = 0;
        while (s[n] != 0) n++;
        return n;
    }

    static constexpr const char_type *find(const char_type *s, size_t n, const char_type &a) noexcept {
        for (size_t i = 0; i < n; i++) {
            if (s[i] == a) return s + i;
        }
        return nullptr;
    }

    static char_type *move(char_type *d, const char_type *s, size_t n) noexcept {
        return n == 0 ? d : static_cast<char_type *>(std::memmove(d, s, n * sizeof(char_type)));
    }

    static char_type *copy(char_type *d, const char_type *s, size_t n) noexcept {
        return n == 0 ? d : static_cast<char_type *>(std::memcpy(d, s, n * sizeof(char_type)));
    }

    static char_type *assign(char_type *d, size_t n, char_type a) noexcept {
        for (size_t i = 0; i < n; i++) d[i] = a;
        return d;
    }

    static constexpr int_type not_eof(int_type c) noexcept { return eq_int_type(c, eof()) ? ~eof() : c; }
    static constexpr char_type to_char_type(int_type c) noexcept { return static_cast<char_type>(c); }
    static constexpr int_type to_int_type(char_type c) noexcept { return c; }
    static constexpr bool eq_int_type(int_type a, int_type b) noexcept { return a == b; }
    static constexpr int_type eof() noexcept { return static_cast<int_type>(EOF); }
};

} // namespace std
