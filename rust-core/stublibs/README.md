# Stub libraries

`libpthread.a` is deliberately empty.

Unicorn's build emits `-lpthread`, which is correct on glibc and wrong on
Android: bionic puts the pthread implementation inside libc itself and ships no
separate library, so the link fails with "unable to find library -lpthread".
An empty archive satisfies the reference without adding anything.

Note this is the *only* thing that gets stubbed. `__clear_cache` also comes up
missing and must NOT be stubbed - unicorn JITs guest code, and that symbol is
what flushes the instruction cache afterwards. Stubbing it links fine and then
executes stale instructions. `build.sh` links the NDK's real compiler-rt
builtins for it instead.
