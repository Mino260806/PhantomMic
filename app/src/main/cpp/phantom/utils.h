//
// Created by amin on 7/24/24.
//

#ifndef PHANTOMMIC_UTILS_H
#define PHANTOMMIC_UTILS_H


#include <sys/system_properties.h>

static int m_sdk_int = 0;

int get_sdk_int() {
    if (m_sdk_int == 0) {
        char sdkIntStr[PROP_VALUE_MAX];
        __system_property_get("ro.build.version.sdk", sdkIntStr);
        m_sdk_int = atoi(sdkIntStr);
    }

    return m_sdk_int;
}

#endif //PHANTOMMIC_UTILS_H
