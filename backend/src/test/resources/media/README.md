# Media test fixture

`valid-h264.mp4` is a generated, one-second blue test clip: 320 × 180, 24 fps,
H.264 / yuv420p, no audio. It contains no personal footage or credentials.

Generated locally with FFmpeg 9.0.2, using its VideoToolbox encoder:

```sh
ffmpeg -y -f lavfi -i color=c=0x006dd0:s=320x180:r=24:d=1 \
  -c:v h264_videotoolbox -allow_sw 1 -pix_fmt yuv420p -an \
  -movflags +faststart valid-h264.mp4
```

The application only inspects uploaded media; it does not encode or transcode.
The fixture is checked by the pinned ffprobe built by
[the installation script](../../../../../scripts/install-media-probe.sh).
