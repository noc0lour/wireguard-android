cmake_minimum_required(VERSION 3.4.1)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")

# Work around https://github.com/android-ndk/ndk/issues/602
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -fuse-ld=gold")

add_executable(libwg-quick.so wg-quick.c)
target_compile_options(libwg-quick.so PUBLIC -O3 -std=gnu11 -Wall -pedantic -Wno-missing-field-initializers)

file(GLOB WG_SOURCES wireguard/src/tools/*.c libmnl/src/*.c)
add_executable(libwg.so ${WG_SOURCES})
target_compile_options(libwg.so PUBLIC "-I${CMAKE_CURRENT_SOURCE_DIR}libmnl/src/" "-I${CMAKE_CURRENT_SOURCE_DIR}/libmnl/include/" "-I${CMAKE_CURRENT_SOURCE_DIR}/wireguard/src/tools/" -O3 -std=gnu11 -D_GNU_SOURCE -DHAVE_VISIBILITY_HIDDEN -DRUNSTATEDIR=\"\\\"/data/data/com.wireguard.android/cache\\\"\" -Wno-pointer-arith -Wno-unused-parameter)

add_custom_target(libwg-go.so WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/libwg-go" COMMENT "Building wireguard-go" VERBATIM COMMAND make
    ANDROID_ARCH_NAME=${ANDROID_ARCH_NAME}
    ANDROID_C_COMPILER=${ANDROID_C_COMPILER}
    ANDROID_TOOLCHAIN_ROOT=${ANDROID_TOOLCHAIN_ROOT}
    ANDROID_LLVM_TRIPLE=${ANDROID_LLVM_TRIPLE}
    ANDROID_SYSROOT=${ANDROID_SYSROOT}
    CFLAGS=${CMAKE_C_FLAGS}\ -Wno-unused-command-line-argument
    LDFLAGS=${CMAKE_SHARED_LINKER_FLAGS}\ -fuse-ld=gold
    DESTDIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
)
# Hack to make it actually build as part of the default target
add_dependencies(libwg.so libwg-go.so)
