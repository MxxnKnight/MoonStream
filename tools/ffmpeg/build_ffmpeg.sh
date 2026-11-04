#!/bin/bash
#
# Builds the tiny FFmpeg used by the in-app player and drops the resulting
# libffmpegJNI.so into app/src/main/jniLibs/.
#
#   ./tools/ffmpeg/build_ffmpeg.sh arm64        # arm64-v8a
#   ./tools/ffmpeg/build_ffmpeg.sh armv7        # armeabi-v7a
#
# Only eac3, ac3, dca (DTS) and truehd are compiled in — the four decoders
# phones like the POCO M4 Pro lack in hardware. Everything else (aac, mp3,
# opus, flac, vorbis, video) is deliberately absent so the .so stays ~1.3 MB.
#
# The Java side is vendored in app/src/main/java/androidx/media3/decoder/ffmpeg/
# (Apache-2.0, copied from the media3 1.4.1 `decoder_ffmpeg` module) and the
# JNI shim is that module's ffmpeg_jni.cc, linked straight against the static
# FFmpeg libs. This is why no Gradle dependency is involved: media3 does not
# publish the FFmpeg extension to Maven.
#
# One-time setup:
#   git clone --depth 1 --branch n6.1.1 https://github.com/FFmpeg/FFmpeg.git .ffmpeg
#   git clone --depth 1 --branch 1.4.1 --filter=blob:none --sparse \
#       https://github.com/androidx/media.git .media3
#   (cd .media3 && git sparse-checkout set libraries/decoder_ffmpeg)
#   sdkmanager "ndk;27.2.12479018"
#
# See docs/FFMPEG.md for the full explanation and the license notes.
set -eu

ARCH="${1:-arm64}"
API="${2:-24}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NDK="${ANDROID_NDK:-$HOME/android-sdk/ndk/27.2.12479018}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
SRC="${FFMPEG_SRC:-$ROOT/.ffmpeg}"
JNI="${MEDIA3_SRC:-$ROOT/.media3}/libraries/decoder_ffmpeg/src/main/jni"
OUT="$ROOT/app/src/main/jniLibs"
JOBS="$(nproc 2>/dev/null || echo 4)"

case "$ARCH" in
    arm64) ABI_DIR="arm64-v8a"; FF_ARCH="aarch64"; CROSS="aarch64-linux-android"; CPU="armv8-a"
           EXTRA_CFLAGS=""; EXTRA_LDFLAGS="" ;;
    armv7) ABI_DIR="armeabi-v7a"; FF_ARCH="arm"; CROSS="armv7a-linux-androideabi"; CPU="armv7-a"
           EXTRA_CFLAGS="-march=armv7-a -mfloat-abi=softfp"; EXTRA_LDFLAGS="-Wl,--fix-cortex-a8" ;;
    *) echo "usage: $0 [arm64|armv7] [api]"; exit 1 ;;
esac

[ -d "$SRC" ] || { echo "FFmpeg source not found at $SRC (see header)"; exit 1; }
[ -d "$JNI" ] || { echo "media3 decoder_ffmpeg not found at $JNI (see header)"; exit 1; }

cd "$SRC"
make distclean >/dev/null 2>&1 || true
# --disable-everything must come BEFORE the enables: configure applies options
# in order, so a later --disable-everything silently wipes the decoders.
./configure \
    --target-os=android --arch="$FF_ARCH" --cpu="$CPU" \
    --cross-prefix="$TOOLCHAIN/$CROSS$API-" \
    --nm="$TOOLCHAIN/llvm-nm" --ar="$TOOLCHAIN/llvm-ar" \
    --ranlib="$TOOLCHAIN/llvm-ranlib" --strip="$TOOLCHAIN/llvm-strip" \
    --enable-static --disable-shared \
    --disable-everything --enable-swresample \
    --enable-decoder=eac3 --enable-decoder=ac3 \
    --enable-decoder=dca --enable-decoder=truehd \
    --disable-doc --disable-programs --disable-avdevice --disable-avformat \
    --disable-swscale --disable-postproc --disable-avfilter --disable-network \
    --disable-encoders --disable-muxers --disable-demuxers --disable-parsers \
    --disable-protocols --disable-bsfs --disable-hwaccels --disable-vulkan \
    --disable-symver \
    --extra-cflags="$EXTRA_CFLAGS" --extra-ldflags="$EXTRA_LDFLAGS" \
    --libdir="$SRC/android-libs/$ABI_DIR" > "$SRC/configure.log" 2>&1
make -j"$JOBS" > "$SRC/make.log" 2>&1
make install-libs >> "$SRC/make.log" 2>&1

LIBS="$SRC/android-libs/$ABI_DIR"
mkdir -p "$OUT/$ABI_DIR"
# LINKING — this is where the v1 build broke (moon's bug 2026-11-04:
# "Dolby still fails"). clang++ links the C++ runtime DYNAMICALLY by default,
# so the first .so needed libc++_shared.so, which is not in the APK; dlopen
# failed, FfmpegLibrary.isAvailable() returned false, the player quietly fell
# back to MediaCodec and EAC3 died exactly as before. FFmpeg and the JNI shim
# are plain C (no std::, no exceptions, no RTTI), so the runtime is dropped
# entirely instead of embedding ~300 KB of libc++_static:
#   -nostdlib++        no implicit -lc++ (nothing in the shim needs it)
#   --no-undefined     a missing symbol fails the LINK, not the phone
#   --exclude-libs,ALL keep FFmpeg's symbols out of the export table
"$TOOLCHAIN/$CROSS$API-clang++" -shared -O2 -fPIC -std=c++11 \
    -o "$OUT/$ABI_DIR/libffmpegJNI.so" "$JNI/ffmpeg_jni.cc" \
    -I"$SRC" -L"$LIBS" -lavcodec -lswresample -lavutil -llog \
    -nostdlib++ -Wl,--no-undefined \
    -Wl,-Bsymbolic -Wl,--gc-sections -Wl,--exclude-libs,ALL \
    -Wl,-z,max-page-size=16384 $EXTRA_LDFLAGS -s

# Fail loudly if a libc++ dependency ever comes back.
if readelf -d "$OUT/$ABI_DIR/libffmpegJNI.so" | grep -q 'NEEDED.*libc++_shared'; then
    echo "ERROR: $ABI_DIR/libffmpegJNI.so still links libc++_shared.so — it will not load on device" >&2
    exit 1
fi

echo "wrote $OUT/$ABI_DIR/libffmpegJNI.so"
ls -l "$OUT/$ABI_DIR/libffmpegJNI.so"
