// SPDX-License-Identifier: GPL-3.0-or-later
//
// Game Music Emu keeps the SNES sound chip's registers private: the emulator holds the SPC (Snes_Spc
// apu), which holds the S-DSP (Spc_Dsp dsp), whose registers can be read through its public read().
// Only the two private members on the way are reached here, by the rule that lets an explicit template
// instantiation name private members (C++17 [temp.spec]/6). The library itself is not changed.
//
// This file is built as part of Game Music Emu's own target, so it sees its classes with exactly the
// options they were built with.
#include "gme_peek.h"

#include "Spc_Emu.h"
#include "gme.h"

namespace {

template <typename Tag, typename Tag::type Member>
struct Reach {
    friend typename Tag::type reach(Tag) { return Member; }
};

struct SpcApu {
    using type = Snes_Spc Spc_Emu::*;
    friend type reach(SpcApu);
};
template struct Reach<SpcApu, &Spc_Emu::apu>;

struct SpcDsp {
    using type = Spc_Dsp Snes_Spc::*;
    friend type reach(SpcDsp);
};
template struct Reach<SpcDsp, &Snes_Spc::dsp>;

} // namespace

bool gmeSpcDspRegisters(const Music_Emu *emu, unsigned char regs[128]) {
    if (emu == nullptr || gme_type(emu) != gme_spc_type) return false;
    // The type says it is an Spc_Emu; there is no RTTI to ask.
    const auto *spc = static_cast<const Spc_Emu *>(emu);
    const Spc_Dsp &dsp = (spc->*reach(SpcApu())).*reach(SpcDsp());
    for (int r = 0; r < Spc_Dsp::register_count; r++) regs[r] = static_cast<unsigned char>(dsp.read(r));
    return true;
}
