# VOIDLINK Android — source project

Android Studio project for Samsung Galaxy S23+ and Android 8.0+.

## Features
- Offline text-to-VOIDLINK acoustic frame generator.
- WAV PCM output (lossless; Android can play it directly).
- In-app playback of the last generated transmission.
- File picker and decoder for WAV, plus Android MediaCodec decoding for common compressed audio such as MP3.
- FSK 1500/2200 Hz, Manchester, 12 ms per encoded symbol.
- Frame: `AA55AA55` + `VLNK` + uint16 big-endian payload length + UTF-8 payload + CRC32.

## Build
Open this folder in Android Studio, let Gradle sync, then choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
The generated APK is normally under `app/build/outputs/apk/debug/app-debug.apk`.

## Important format note
This is the experimental VOIDLINK format from this project, not GGWave. The first version exports WAV rather than MP3 because WAV is lossless and does not require an MP3 encoder library. The decoder can read WAV and attempts to decode MP3/other supported audio using the Android device's MediaCodec implementation. For reliable transmission, use the generated WAV.
