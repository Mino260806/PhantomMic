#!/bin/bash

PWD=$(pwd)
SOURCE_PATH=${PWD}/keystone
BUILD_PATH=${PWD}/_keystone_builds_ios
ARCH_TARGETS="arm64 arm64e"

rm -rf ${BUILD_PATH}

git clone https://github.com/keystone-engine/keystone.git keystone

for ARCH_TARGET in ${ARCH_TARGETS}
do
    TARGET_BUILD_PATH=${BUILD_PATH}/${ARCH_TARGET}/build
    echo "~: Building ${ARCH_TARGET} :~"
    echo "~: Path: ${TARGET_BUILD_PATH} :~"

    mkdir -p ${TARGET_BUILD_PATH}
    cd ${TARGET_BUILD_PATH}

    cmake ${SOURCE_PATH} -G"Unix Makefiles" \
    -DCMAKE_SYSTEM_NAME=iOS \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=10.0 \
    -DCMAKE_OSX_ARCHITECTURES=${ARCH_TARGET} \
    -DCMAKE_XCODE_ATTRIBUTE_ONLY_ACTIVE_ARCH=NO \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=0

    make -j32

    cd -

    cp ${TARGET_BUILD_PATH}/llvm/lib/libkeystone.a ${BUILD_PATH}/${ARCH_TARGET}
    echo "Binary built ${BUILD_PATH}/${ARCH_TARGET}/libkeystone.a"
done