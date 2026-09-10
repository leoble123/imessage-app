#!/usr/bin/env bash
# Clones rustpush and rewrites its SSH submodule URLs to HTTPS, because
# Cargo's submodule fetch ignores git's insteadOf rewrites and will fail
# outright on any machine without SSH keys registered with GitHub.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
dest="$here/vendor/rustpush"

rm -rf "$dest"
mkdir -p "$here/vendor"
git clone --depth 1 https://github.com/TaeHagen/rustpush "$dest"

cd "$dest"
sed -i 's|git@github.com:|https://github.com/|g' .gitmodules
git submodule sync
git submodule update --init --recursive --depth 1
echo "rustpush vendored at $dest"
