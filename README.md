# PanelPath

**Your Comic Reading Companion** — a native Android app for tracking a personal comic reading library. Version **0.4.4** adds Reading History, persistent Reading Queue and favorites, Library view preferences, quick actions with Undo, and bulk issue management. It does not display comic pages.

## Features

- Create one local profile and use the app without an account or internet connection.
- Add comics manually; mark them Want to Read, Reading, or Completed.
- Browse the local library offline, filter by status, and view saved and completed counts.
- Search Marvel publisher series on Metron, browse paginated issues, view issue details, and save issues locally.
- Save a whole series as one Library entry without bulk saving its issues; browse its issues and known progress.
- Create, rename, delete, and reorder local Sagas containing series and individual issues. Include all available issues, specific loaded issues, or a numeric issue range for a series, and edit that membership later.
- Track Saga progress from unique issues with one reading status shared across the Library and every Saga. Continue Reading opens the next incomplete issue in the saved order.
- Rate issues from 0 to 5 stars, distinguish 0 from Not Rated, and browse expandable series reviews with filters and sorting.
- See reading totals, publication year averages, rating distribution, favorite decades, top series, and dated reading activity on the Dashboard.
- Configure, validate, replace, test, or remove a personal Metron API token.
- Use the supplied PanelPath artwork for the launch cover and first-run screen, with a matching book-and-path launcher icon. The Library, Sagas, Search, and Settings screens follow the dark charcoal and coral reference palette.

## Stack and architecture

Kotlin, Jetpack Compose, Material 3, Navigation Compose, Room, DataStore, Android Keystore, Retrofit, OkHttp, Coroutines, StateFlow, and Coil. `PanelViewModel` coordinates UI state; `LibraryRepository` owns canonical issue records; `SagaRepository` owns saved series and ordered membership; `ProfileRepository` owns local profile preferences; `MetronRepository` handles documented read-only API calls; `TokenStore` encrypts the token using an Android Keystore AES-GCM key.

Room's `issues` table is the single authority for reading status, nullable rating, and activity dates. `inLibrary` distinguishes explicit Library entries from issues kept for a Saga, rating, or completed history. `saved_series` stores one record per Metron series, with its own Library flag. `sagas`, `saga_entries`, and `saga_tracked_issues` store ordered membership relationally. Saga progress deduplicates issue IDs across entries. Version 0.3 keeps database schema 2, so existing version 0.2 data needs no migration. `ReadingAnalytics.kt` derives statistics from the same records. The version 1 → 2 Room migration continues to preserve older issue rows; DataStore profile data and Keystore credentials remain untouched.

## Open in Android Studio

1. Open this directory as an existing project in Android Studio.
2. Install Android SDK Platform 35 and a JDK 17 or later (Android Studio's bundled JDK works).
3. Let Gradle sync. The project uses Gradle 8.14 and Android Gradle Plugin 8.6.1.
4. Run the `app` configuration on an Android 8.0 (API 26) or newer device or emulator.

From a terminal, run `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` (on Windows, use `gradlew.bat`). With an emulator or device connected, run `./gradlew connectedDebugAndroidTest` for Room migration and Saga integration tests.

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
- Adding all issues of a large series may take time and requires a network connection; the operation can be cancelled and commits membership only after all pages load. Selecting specific issues uses the pages currently loaded or previously cached local issues.
- Offline series browsing shows locally recorded issues. A Metron connection is required to discover additional issues.
- A series percentage is withheld when older issue records have not yet been associated with a Metron series ID.
- Historical activity uses recorded completion dates only. Older completed issues without dates are counted in totals but excluded from the monthly timeline.
- Publication year insights require an issue publication year. Unidentified manual series are grouped by their entered title and year.
- The app has no comic reader, downloads, or cloud backup.

## Version 0.4.4

Reading History is available from Library. It uses canonical completion dates, groups unknown dates separately, and filters by local series, saga, and date range. Marking an issue unread removes it from the timeline. Library issue cards offer quick actions and multi-selection; persisted Undo restores reading and queue fields only when a later reading action has not superseded them. Queue order and favorites work offline. Library filters and sorting survive restarts; local title search ignores case and accents.

Room schema 3 adds `issues.queued`, `issues.queuePosition`, and `saved_series.favorite` with safe defaults through migration 2→3. Existing identifiers, ratings, covers, memberships, and dates are preserved. Completion removes the issue from the queue atomically. No historical dates are invented. The Dashboard continues to derive activity from the same issue table.

Continue Reading can select only locally known issues; incomplete offline catalogs cannot identify issues that have not been loaded. History represents each issue's current completion, rather than multiple rereading events. The downloadable APK uses the existing debug signing configuration.
