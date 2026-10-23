sample.mp4 is a synthetic 8-second blue H.264 video with a 440 Hz AAC tone.
It contains no third-party content and is used only by instrumentation tests.
Generate with FFmpeg:
ffmpeg -f lavfi -i color=c=blue:s=320x180:d=8 -f lavfi -i sine=frequency=440:duration=8 -c:v libx264 -c:a aac -pix_fmt yuv420p -movflags +faststart sample.mp4
