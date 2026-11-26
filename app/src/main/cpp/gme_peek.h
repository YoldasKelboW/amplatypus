// SPDX-License-Identifier: GPL-3.0-or-later
//
// Reads what Game Music Emu keeps to itself: the registers of the SNES sound chip (S-DSP).
#pragma once

struct Music_Emu;

// Copies the S-DSP's 128 registers of an SPC file's emulator into `regs`. False for other emulators.
bool gmeSpcDspRegisters(const Music_Emu *emu, unsigned char regs[128]);
