#!/usr/bin/env bash
# Cross-compiles the core for arm64 and generates the Kotlin bindings.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
app="$here/../app"
target=aarch64-linux-android

: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to your NDK, e.g. \$ANDROID_HOME/ndk/27.2.12479018}"
tc="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"

mkdir -p "$here/.cargo"
cat > "$here/.cargo/config.toml" <<CFG
[target.$target]
linker = "$tc/aarch64-linux-android26-clang"
ar = "$tc/llvm-ar"

[env]
CC_$target = "$tc/aarch64-linux-android26-clang"
CXX_$target = "$tc/aarch64-linux-android26-clang++"
AR_$target = "$tc/llvm-ar"
RANLIB_$target = "$tc/llvm-ranlib"
CFG

cd "$here"
cargo build --release --target "$target"

mkdir -p "$app/src/main/jniLibs/arm64-v8a" "$app/build/generated/uniffi"
cp "target/$target/release/libimessage_core.so" "$app/src/main/jniLibs/arm64-v8a/"

cargo run --release --bin uniffi-bindgen -- generate \
  --library "target/$target/release/libimessage_core.so" \
  --language kotlin --out-dir "$app/build/generated/uniffi" 2>/dev/null \
  || echo "bindgen step needs the uniffi-bindgen binary target; see BUILD.md"

echo "core built -> $app/src/main/jniLibs/arm64-v8a/libimessage_core.so"
