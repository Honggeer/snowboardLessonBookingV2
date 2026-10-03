#!/bin/sh
set -eu
# FFmpeg source releases are published by ffmpeg.org. Build only local MP4 inspection.
# No global Homebrew upgrades or changes to the user's shell profile are needed.
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TASK_BUILD=$(mktemp -d "${TMPDIR:-/tmp}/geer-ffprobe.XXXXXX")
trap 'rm -rf "$TASK_BUILD"' EXIT INT TERM
curl --fail --location --connect-timeout 15 --max-time 180 \
  https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz -o "$TASK_BUILD/source.tar.xz"
TASK_HASH=8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e
printf '%s  %s\n' "$TASK_HASH" "$TASK_BUILD/source.tar.xz" | shasum -a 256 -c -
tar -xf "$TASK_BUILD/source.tar.xz" -C "$TASK_BUILD"
cd "$TASK_BUILD/ffmpeg-9.0.2"
./configure --disable-everything --disable-autodetect --disable-doc --disable-debug \
  --disable-network --disable-ffplay --enable-ffprobe --enable-protocol=file \
  --enable-demuxer=mov --enable-decoder=h264,aac --enable-parser=h264,aac \
  --enable-small --disable-asm
make -j2 ffprobe
mkdir -p "$TASK_ROOT/backend/.local/bin"
cp ffprobe "$TASK_ROOT/backend/.local/bin/ffprobe"
cp COPYING.LGPLv2.1 "$TASK_ROOT/backend/.local/bin/ffprobe-LICENSE.txt"
"$TASK_ROOT/backend/.local/bin/ffprobe" -version | head -1
