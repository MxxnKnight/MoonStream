# FFmpeg audio decoders (tiny build)

The in-app player cannot play EAC3/AC3/DTS/TrueHD audio on phones that have no
hardware decoder for them — ExoPlayer fails with
`ERROR_CODE_DECODER_INIT_FAILED` (`format_supported=NO_UNSUPPORTED_TYPE`).
MoonStream bundles a **minimal, audio-only FFmpeg** so those four codecs are
decoded in software instead.

## What is in the build

| Decoder | Codec |
|---|---|
| `eac3` | Dolby Digital Plus |
| `ac3` | Dolby Digital |
| `dca` | DTS (incl. DTS-HD parsing) |
| `truehd` | Dolby TrueHD |

Everything else is disabled — no AAC/MP3/Opus/FLAC/Vorbis (the phone does those
in hardware), no video decoders, no demuxers, no network, no filters.

Result: `libffmpegJNI.so` is **~1.3 MB** for arm64-v8a and ~1.1 MB for
armeabi-v7a, well under the 5 MB budget.

## Why there is no Gradle dependency

`androidx.media3:media3-exoplayer-ffmpeg` (the coordinate once suggested in
AGENT.md) **does not exist on Maven** — Google does not publish the FFmpeg
extension, because the native library has to be built locally for legal and
packaging reasons. So the extension is vendored:

- `app/src/main/java/androidx/media3/decoder/ffmpeg/` — `FfmpegLibrary`,
  `FfmpegAudioDecoder`, `FfmpegAudioRenderer`, `FfmpegDecoderException`,
  copied verbatim from the media3 **1.4.1** `decoder_ffmpeg` module
  (Apache-2.0) so the Java side matches the media3 version the app already
  uses. Keep them in sync if media3 is ever upgraded.
- `app/src/main/jniLibs/<abi>/libffmpegJNI.so` — FFmpeg static libs
  (`avcodec`, `avutil`, `swresample`) linked together with that module's
  `ffmpeg_jni.cc` shim. `FfmpegLibrary` loads it as `ffmpegJNI`.
- `tools/ffmpeg/build_ffmpeg.sh` — the exact, repeatable build.

## Rebuilding

```sh
git clone --depth 1 --branch n6.1.1 https://github.com/FFmpeg/FFmpeg.git .ffmpeg
git clone --depth 1 --branch 1.4.1 --filter=blob:none --sparse \
    https://github.com/androidx/media.git .media3
(cd .media3 && git sparse-checkout set libraries/decoder_ffmpeg)
sdkmanager "ndk;27.2.12479018"

./tools/ffmpeg/build_ffmpeg.sh arm64   # -> app/src/main/jniLibs/arm64-v8a/
./tools/ffmpeg/build_ffmpeg.sh armv7   # -> app/src/main/jniLibs/armeabi-v7a/
```

FFmpeg 6.1 is the version media3 1.4.1's shim is written against. Two build
details that bite:

1. `--disable-everything` must appear **before** the `--enable-decoder=…`
   flags — configure applies options in order, so a later `--disable-everything`
   silently produces a library with zero decoders.
2. 32-bit ARM also needs `--disable-vulkan`; without it
   `libavcodec/vulkan_video.c` fails on `VkVideoSessionKHR` pointer width.

Verify a build really contains the decoders:

```sh
llvm-nm -C libffmpegJNI.so | grep -E 'ff_(eac3|ac3|dca|truehd)_decoder$'
```

## Wiring in the app

`PlayerScreen.kt` builds the player with a `DefaultRenderersFactory` whose
extension mode is `EXTENSION_RENDERER_MODE_PREFER` when
`FfmpegLibrary.isAvailable()`, else `EXTENSION_RENDERER_MODE_OFF` (hardware
only, no crash). `PREFER` is safe because `FfmpegAudioRenderer` only claims a
format the .so actually has — AAC and friends still go to MediaCodec. The
chosen path and the FFmpeg version are logged as `PLAYER` lines in the Logs
screen.

x86/x86_64 have no `.so` (emulators only); they fall back to hardware.

## Licenses

- FFmpeg is built **without** `--enable-gpl` and without any GPL component, so
  the resulting library is **LGPL-2.1-or-later**. It is dynamically linked into
  the APK (a separate `.so`), and the build is reproducible with the script
  above, which satisfies the LGPL relinking requirement. FFmpeg's source and
  license text: <https://ffmpeg.org/legal.html>.
- The vendored Java classes are Apache-2.0, © The Android Open Source Project;
  their license headers are kept in each file.
