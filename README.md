# PanelPath

**Your Comic Reading Companion** — a native Android app for tracking a personal comic reading library. Version **0.1.0** focuses on Marvel series metadata from Metron and offline reading progress. It does not display comic pages.

## Features

- Create one local profile and use the app without an account or internet connection.
- Add comics manually; mark them Want to Read, Reading, or Completed.
- Browse the local library offline, filter by status, and view saved and completed counts.
- Search Marvel publisher series on Metron, browse paginated issues, view issue details, and save issues locally.
- Configure, validate, replace, test, or remove a personal Metron API token.

## Stack and architecture

Kotlin, Jetpack Compose, Material 3, Navigation Compose, Room, DataStore, Android Keystore, Retrofit, OkHttp, Coroutines, StateFlow, and Coil. `PanelViewModel` coordinates UI state; `LibraryRepository` owns Room operations; `ProfileRepository` owns local profile preferences; `MetronRepository` handles documented read-only API calls; `TokenStore` encrypts the token using an Android Keystore AES-GCM key.

## Open in Android Studio

1. Open this directory as an existing project in Android Studio.
2. Install Android SDK Platform 35 and a JDK 17 or later (Android Studio's bundled JDK works).
3. Let Gradle sync. The project uses Gradle 8.14 and Android Gradle Plugin 8.6.1.
4. Run the `app` configuration on an Android 8.0 (API 26) or newer device or emulator.

From a terminal, run `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` (on Windows, use `gradlew.bat`).

## Metron token

Create a free account at [Metron](https://metron.cloud/) and generate a personal token in your account's API Tokens section. Enter it during setup or later in Settings. PanelPath sends it only in the `Authorization: Bearer` header to `https://metron.cloud/api/`. No shared developer token, backend, or cloud account is used. The app validates a candidate with a read-only publisher request before saving it. Online search requires a valid token; manual tracking does not.

The encrypted token is kept in app-private storage with a Keystore key. App backup is disabled. This reduces accidental exposure but cannot guarantee protection on a compromised device. Do not include tokens in issues, screenshots, logs, or commits.

## Metron API and attribution

Comic metadata and remote covers are provided by the [Metron Comic Book Database](https://metron.cloud/). PanelPath uses Metron's documented series and issue endpoints, publisher-name filter, bearer authentication, and pagination. See the [API documentation](https://github.com/Metron-Project/metron/blob/master/api/README.md). The MIT license in this repository covers only original PanelPath code, not Metron data, cover images, or trademarks.

## Limitations

- Online search requires the user's own Metron token and a network connection.
- Only one local profile is supported.
- Saved metadata stays in the local Room database; it is not synchronized with Metron collections.
- Search is focused on series whose publisher name matches Marvel in Metron's metadata.
- The app has no comic reader, downloads, or cloud backup.
