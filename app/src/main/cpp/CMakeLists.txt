cmake_minimum_required(VERSION 3.4.1)

enable_language(ASM)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
SET(CMAKE_ASM_FLAGS "${CFLAGS} -x assembler-with-cpp")

set(JNILIBS ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI})
set(KITTYMEMORY_PATH ${CMAKE_CURRENT_SOURCE_DIR}/KittyMemory)
file(GLOB KITTYMEMORY_SRC ${KITTYMEMORY_PATH}/*.cpp)
set(KEYSTONE_LIB ${KITTYMEMORY_PATH}/Deps/Keystone/libs-android/${CMAKE_ANDROID_ARCH_ABI}/libkeystone.a)

set(AND64INLINEHOOK_PATH ${CMAKE_CURRENT_SOURCE_DIR}/And64InlineHook)
file(GLOB AND64INLINEHOOK_SRC ${AND64INLINEHOOK_PATH}/*.cpp)

set(INLINEHOOK_PATH ${CMAKE_CURRENT_SOURCE_DIR}/InlineHook)
file(GLOB INLINEHOOK_SRC_CPP ${INLINEHOOK_PATH}/*.cpp)
file(GLOB INLINEHOOK_SRC_C ${INLINEHOOK_PATH}/*.c)
file(GLOB INLINEHOOK_SRC_S ${INLINEHOOK_PATH}/*.s)

add_library(xposedlab SHARED main.cpp ${KITTYMEMORY_SRC} ${AND64INLINEHOOK_SRC}
        ${INLINEHOOK_SRC_CPP} ${INLINEHOOK_SRC_C} ${INLINEHOOK_SRC_S})

target_include_directories(xposedlab PRIVATE KittyMemory And64InlineHook InlineHook libutils binder
        minimp3 libsamplerate mpg123 AudioFile ffmpeg)
target_link_libraries(xposedlab -llog ${KEYSTONE_LIB})

include_directories(${ANDROID_NDK}/sysroot/usr/include)

find_library(log-lib log)
target_link_libraries(xposedlab ${log-lib})
