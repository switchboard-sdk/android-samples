# SwitchboardAndroidDemo

A simple speech-to-text, text-to-speech and voice assistant demo application for Android.

The app targets **SwitchboardSDK 3.3.0**, uses the **v3 JSON audio-graph API**, and is
built with **Jetpack Compose** for the UI. It includes four examples:

- **Sherpa TTS** — synthesize typed text to speech.
- **Whisper STT** — transcribe microphone input to text.
- **Whisper STT to Sherpa TTS** — transcribe speech and synthesize it back.
- **OpenAI Live** — a voice conversation with OpenAI GPT-Live, with tool calls.

## Setup

1. Create a `model` directory under `app/src/main/assets`.
2. Copy all the model and asset files from SwitchboardSherpa assets into `model`.
3. Copy `ggml-tiny.en.bin` from the SwitchboardWhisper assets into `model`.

OpenAI Live needs no models; skip these steps if it's the only example you want to run.

The SwitchboardSDK and its extensions are declared as Gradle dependencies (see
`app/build.gradle`) and resolved automatically from Maven when you build.

## Build & Run

Open the project folder in Android Studio and run the application, or build from the
command line:

```
./gradlew assembleDebug
```

## OpenAI Live

The [`OpenAI.Live` node](https://docs.switchboard.audio/extensions/openai/openai-live-node) holds a voice conversation with OpenAI GPT-Live.

It needs your own Switchboard app: [register one](https://console.switchboard.audio/register), then
replace `"demo"` with its `appID` and `appSecret` in `DemoApplication.kt`.

Allow the microphone and notifications when the app asks, open **OpenAI Live** and press Start.
Then talk; the transcript shows as you go. Say "play some music" or "pause the music" to try the
tools. Press Stop to end.

## Documentation

- [Register a Switchboard app](https://console.switchboard.audio/register)
- [Switchboard SDK](https://docs.switchboard.audio/)
- [Android integration](https://docs.switchboard.audio/integration/android)
- [OpenAI Live credentials and billing](https://docs.switchboard.audio/extensions/openai/openai-live-node#credentials-and-billing)
- [OpenAI Live Toolkit for React Native](https://github.com/switchboard-sdk/openai-live-toolkit)
