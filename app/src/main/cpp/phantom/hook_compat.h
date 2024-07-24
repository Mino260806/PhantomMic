//
// Created by amin on 7/24/24.
//

#ifndef PHANTOMMIC_HOOK_COMPAT_H
#define PHANTOMMIC_HOOK_COMPAT_H

#include <string>
#include "utils.h"

namespace HookCompat {
    std::string get_library_name() {
        if (get_sdk_int() <= 24) {
            return "libmedia.so";
        }

        else {
            return "libaudioclient.so";
        }
    }

    std::string get_obtainBuffer_symname() {
        if (get_sdk_int() <= 28) {
            return "_ZN7android11AudioRecord12obtainBufferEPNS0_6BufferEPK8timespecPS3_Pj";
        }
        else {
            return "_ZN7android11AudioRecord12obtainBufferEPNS0_6BufferEPK8timespecPS3_Pm";
        }
    }

    uintptr_t get_symbol(ElfScanner elfScanner, std::vector<std::string> possible_symnames) {
        for (std::string symname: possible_symnames) {
            uintptr_t sym = elfScanner.findSymbol(symname);
            if (sym != 0) {
                return sym;
            }
        }
        return 0;
    }

    uintptr_t get_stop_symbol(ElfScanner elfScanner) {
        return get_symbol(elfScanner, {
            "_ZN7android11AudioRecord4stopEv"
        });
    }

    uintptr_t get_obtainBuffer_symbol(ElfScanner elfScanner) {
        return get_symbol(elfScanner, {
                "_ZN7android11AudioRecord12obtainBufferEPNS0_6BufferEPK8timespecPS3_Pj",
                "_ZN7android11AudioRecord12obtainBufferEPNS0_6BufferEPK8timespecPS3_Pm",
        });
    }
}

#endif //PHANTOMMIC_HOOK_COMPAT_H
