//
// Created by amin on 7/16/24.
//

#ifndef XPOSEDLAB_INLINEHOOK_HPP
#define XPOSEDLAB_INLINEHOOK_HPP

#include <stdint.h>
#include <asm/ptrace.h>

void ModifyIBored(uintptr_t uiHookAddr, void (*callback)(user_pt_regs*));

#endif //XPOSEDLAB_INLINEHOOK_HPP
