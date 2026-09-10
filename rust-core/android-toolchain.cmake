# Wraps the NDK's own toolchain file, pinning the ABI first.
#
# unicorn's CMakeLists has a correct Android branch, but it keys off
# ANDROID_ABI - which only the NDK toolchain file defines. Without it the
# build falls through to host-compiler detection, decides it is targeting
# x86_64, and adds -mcx16, which aarch64 clang rejects.
set(ANDROID_ABI arm64-v8a)
set(ANDROID_PLATFORM android-26)
include("/root/dev/android-sdk/ndk/27.2.12479018/build/cmake/android.toolchain.cmake")
