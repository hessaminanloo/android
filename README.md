# MeetMate

MeetMate is a small, local-first Android meeting assistant built from scratch with Kotlin, Jetpack Compose, Material 3, Room, and MVVM.

## MVP flow

1. Create a meeting from Home and select the agents that should participate.
2. Grant microphone access. The live screen records an M4A file and continuously displays Persian partial and final recognition results from Whisper.cpp. Whisper is the only speech recognition engine.
3. Pause, resume, or finish. The transcript, duration, selected agents, and audio path are saved in Room and the meeting appears in History.
4. A local provider creates a lightweight summary and task insight immediately. `AiProvider` is the seam for an OpenAI-compatible, OpenRouter, custom, or on-device provider.

## Offline model management

Settings maintains Tiny, Base, Small, Medium, and Large v3 GGML models. Each model has a download state, progress percentage, validation state, active selection, delete action, storage warning, and device recommendation. Downloads use Android's scoped app-specific storage, so the app does not request broad external-storage permission. A completed download is moved from a `.part` file only after it passes a minimum-size validation check. Recommendations use total RAM, CPU core count, and free storage: Tiny/Base for low-end devices, Small for normal devices, and Medium/Large v3 for high-end devices.

## Speech engine note

The requested [`nimaone/persian_tts`](https://github.com/nimaone/persian_tts) repository is a Persian **text-to-speech** project (ONNX/Torch voice synthesis), not a speech-to-text engine. It cannot process microphone audio into a transcript and is not used as STT. MeetMate keeps separate boundaries:

- `WhisperEngine` is the only STT engine and reads the selected local `ggml-*.bin` model through `WhisperRuntime`.
- `TextToSpeechEngine` is an optional playback feature. `AndroidPersianTtsEngine` is included as a safe default; the referenced TTS project can be integrated behind this interface later.

The native Whisper boundary is intentionally isolated in `NativeWhisperRuntime`. It expects the `meetmate_whisper` JNI library with `init`, `transcribe`, and `release` functions, captures 16 kHz PCM microphone chunks, and feeds decoded text into the live transcript. The repository now includes the JNI shim and CMake integration in `app/src/main/cpp`. The shim links a local whisper.cpp checkout when present and builds a safe native error target otherwise.

To enable the real native implementation locally:

```bash
./scripts/setup-whisper.sh
```

Then open the project in Android Studio with NDK 25.2.9519653 and CMake 3.22.1 installed. The Gradle configuration passes `MEETMATE_WHISPER_CPP_DIR` to CMake and supports `-PWHISPER_CPP_DIR=/path/to/whisper.cpp` when the checkout is elsewhere. If the checkout is absent, the native target builds a stub and the app reports the setup error; once the checkout is present, the same target links real whisper.cpp inference. This follows the official whisper.cpp Android sample's CMake/NDK structure and local GGML model workflow ([Android sample](https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android), [JNI example](https://github.com/ggml-org/whisper.cpp/blob/master/examples/whisper.android/lib/src/main/jni/whisper/jni.c)).

## Pipeline test

`WhisperPipelineInstrumentedTest` includes a 1-second 16 kHz WAV sample and exercises local model loading, JNI inference, and cleanup through the same native path used by the microphone worker. Run it after downloading/selecting a model and building the native target:

```bash
./gradlew connectedDebugAndroidTest --tests '*WhisperPipelineInstrumentedTest*'
```

The test skips itself when no model or native whisper.cpp library is installed, so it does not hide a missing local setup behind a false pass.

## Build

Open the directory in Android Studio with an Android SDK installed (API 35), let Android Studio install the matching SDK/platform and Gradle dependencies, then run the `app` configuration. Dependencies and plugin versions are declared in `app/build.gradle.kts`; the project does not rely on the prototype APK or any generated build artifacts. The emulator/device needs microphone permission. Use Settings → Whisper Models to download and select a model before starting a meeting.

The reference `app-debug.apk` was inspected only for behavior and capability clues. Its Vosk/Whisper/Room/local-LLM artifacts informed the capability boundaries; no UI or code was copied.
