#!/usr/bin/env bash
# Clones rustpush (including its own submodules) and installs the FairPlay
# keys this build actually has to have.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
dest="$here/vendor/rustpush"

rm -rf "$dest"
mkdir -p "$here/vendor"

# rustpush's submodules - including ones nested inside another submodule,
# such as apple-private-apis/clearadi - are declared with git@github.com:
# URLs, and cloning fails outright on any machine with no SSH key registered
# with GitHub, which is every CI runner. `git submodule update` (unlike
# Cargo's own libgit2-based fetching - see the note in BUILD.md) honours
# url.insteadOf, and passing it via -c here applies to every nested clone
# --recurse-submodules triggers, not just the top-level one - so it's set
# once here rather than chased down .gitmodules file by .gitmodules file.
git -c url."https://github.com/".insteadOf="git@github.com:" \
    clone --recurse-submodules --shallow-submodules --depth 1 \
    https://github.com/TaeHagen/rustpush "$dest"

# The ten modern FairPlay keypairs src/activation.rs expects
# (certs/fairplay/*.crt / *.pem) are deliberately withheld from the public
# repository - build fails with "couldn't read certs/fairplay/....crt" for
# every one of them otherwise. The published legacy pair
# (certs/legacy-fairplay/), which *is* checked in, works the same way, so
# it's installed under each name activate() actually looks for.
fairplay="$dest/certs/fairplay"
legacy="$dest/certs/legacy-fairplay"
mkdir -p "$fairplay"
for name in \
    4056631661436364584235346952193 4056631661436364584235346952194 \
    4056631661436364584235346952195 4056631661436364584235346952196 \
    4056631661436364584235346952197 4056631661436364584235346952198 \
    4056631661436364584235346952199 4056631661436364584235346952200 \
    4056631661436364584235346952201 4056631661436364584235346952208
do
    cp "$legacy/fairplay.crt" "$fairplay/$name.crt"
    cp "$legacy/fairplay.pem" "$fairplay/$name.pem"
done

echo "rustpush vendored at $dest, with legacy FairPlay keys installed"
