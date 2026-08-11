# CursorTalkAndroid

Android (Kotlin / Jetpack Compose) chat client that talks to an external **CursorTalk** backend
over SSE (`POST {serverUrl}/chat/stream`). The backend is **not** part of this repo; the app
defaults to `http://10.0.2.2:3000` (host loopback from the Android emulator).

## Cursor Cloud specific instructions

### Environment
- JDK 21 is preinstalled. The Android SDK is installed at `~/android-sdk` (command-line tools,
  `platform-tools`, `platforms;android-36`, `platforms;android-36.1`, `build-tools;36.0.0`).
  `android-36.1` is required because `app/build.gradle.kts` sets `compileSdk` to `release(36){ minorApiLevel = 1 }`.
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` are exported from `~/.bashrc`. `local.properties`
  (gitignored) contains `sdk.dir=$HOME/android-sdk`. Both let Gradle find the SDK; if a new pod
  is missing `local.properties`, the update script recreates it.
- `org.gradle.configuration-cache=true` is on, so the first invocation of a task is slower and
  later ones are cached.

### Lint / test / build / run (all via the Gradle wrapper `./gradlew`)
- Unit tests: `./gradlew testDebugUnitTest` (pure JVM: `SseParserTest`, `ChatViewModelTest`).
- Lint: `./gradlew lintDebug` (HTML report at `app/build/reports/lint-results-debug.html`).
- Build debug APK: `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`.
- Instrumented tests (`connectedAndroidTest`) and launching the app **cannot run here**: there is
  no `/dev/kvm`, so an Android emulator is not usable. Validate device/UI-independent logic with
  unit tests instead.

### Running the app's core flow without an emulator
The networking layer (`data/remote/CursorTalkClient.kt` + `SseParser.kt`) is plain Kotlin + OkHttp
with no Android dependencies, so the chat/SSE flow can be exercised from a JVM unit test by
pointing `CursorTalkClient.streamMessage("http://127.0.0.1:3000", ...)` at a small in-process HTTP
server (e.g. `com.sun.net.httpserver.HttpServer`) that emits `meta` / `delta` / `done` SSE events.
When run under the plain JVM test runtime you'll see a harmless
`Method isLoggable in android.util.Log not mocked` warning from OkHttp's platform detection; the
request still succeeds.
