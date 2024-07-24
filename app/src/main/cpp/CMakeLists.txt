cmake_minimum_required(VERSION 3.4.1)

enable_language(ASM)

if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm" OR CMAKE_SYSTEM_PROCESSOR MATCHES "aarch")
    if(CMAKE_SIZEOF_VOID_P EQUAL 8)
        set(ARCH "aarch64")
    else()
        set(ARCH "arm")
    endif()
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^mips.*")
    if(CMAKE_SIZEOF_VOID_P EQUAL 8)
        set(ARCH "mips64el")
    else()
        set(ARCH "mipsel")
    endif()
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^ppc.*")
    if(CMAKE_SIZEOF_VOID_P EQUAL 8)
        set(ARCH "ppc64le")
    else()
        message(FATAL_ERROR "Architecture is not supported")
    endif()
else()
    if(CMAKE_SIZEOF_VOID_P EQUAL 8)
        set(ARCH "x86_64")
    else()
        set(ARCH "x86")
    endif()
endif()

SET(CMAKE_ASM_FLAGS "${CFLAGS} -x assembler-with-cpp")

set(JNILIBS ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI})
set(KITTYMEMORY_PATH ${CMAKE_CURRENT_SOURCE_DIR}/KittyMemory)
file(GLOB KITTYMEMORY_SRC ${KITTYMEMORY_PATH}/*.cpp)
set(KEYSTONE_LIB ${KITTYMEMORY_PATH}/Deps/Keystone/libs-android/${CMAKE_ANDROID_ARCH_ABI}/libkeystone.a)

set(PHANTOM_PATH ${CMAKE_CURRENT_SOURCE_DIR}/phantom)
file(GLOB PHANTOM_SRC ${PHANTOM_PATH}/*.cpp)

set(INLINEHOOK_PATH ${CMAKE_CURRENT_SOURCE_DIR}/InlineHook)
file(GLOB INLINEHOOK_SRC_CPP ${INLINEHOOK_PATH}/*.cpp)
file(GLOB INLINEHOOK_SRC_C ${INLINEHOOK_PATH}/*.c)
file(GLOB INLINEHOOK_SRC_S ${INLINEHOOK_PATH}/*.s)

set(INLINEHOOK_SRC "")
if (ARCH EQUAL "aarch64")
    list(APPEND INLINEHOOK_SRC ${INLINEHOOK_SRC_CPP} ${INLINEHOOK_SRC_C} ${INLINEHOOK_SRC_S})
endif()

add_library(xposedlab SHARED main.cpp ${KITTYMEMORY_SRC} ${INLINEHOOK_SRC} ${PHANTOM_SRC})

target_include_directories(xposedlab PRIVATE KittyMemory phantom ffmpeg)
if (ARCH EQUAL "aarch64")
    target_include_directories(xposedlab PRIVATE InlineHook)
endif()

target_link_libraries(xposedlab -llog ${KEYSTONE_LIB})

include_directories(${ANDROID_NDK}/sysroot/usr/include)

find_library(log-lib log)
target_link_libraries(xposedlab ${log-lib})
