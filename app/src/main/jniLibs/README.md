# Bundled ARM64 runtime provenance

`arm64-v8a/libc++_shared.so` was updated on 2026-10-16 from the Android NDK
r28c (28.2.13676358) distribution at
`toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so`.

- Original NDK file SHA-256: `ab4e6c71b96b851de45a8a9bd86369e7dbc2130a44b3b4520564be94847910f2`.
- Process: copy, then run the same NDK's `llvm-strip --strip-unneeded`.
- Committed file SHA-256: `cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c`.
- All 18 symbols imported from the old C++ runtime by `libcaesarson.so` remain exported.
  Symbol compatibility does not replace on-device testing of the login path.
- License: [LLVM libc++ notices](../../../../third_party/licenses/llvm-libcxx-LICENSE.txt).

Other vendor libraries remain byte-for-byte unchanged. In particular, changing
the alignment field of `libcaesarson.so` would not fix its file/virtual address
layout or RELRO protection. Obtain an ABI-compatible vendor build or its sources;
do not disable compatibility warnings or remove the login functionality.

Run the packaged-artifact check with:

```text
python scripts/verify_native_page_size.py app/build/outputs/apk/release/app-release.apk
```

The check must pass for every packaged 64-bit library before claiming 16 KB
compatibility. A successful static check still requires a real 16 KB runtime test.
