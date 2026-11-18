# PanelPath 0.4.4

- Added Reading History with date grouping, unknown date entries, local series/saga/date filters, and recent/oldest sorting.
- Added contextual issue actions and Library issue multi-selection for reading status and queue operations.
- Added persisted Undo for local reading and queue removal actions, with protection against superseding changes.
- Completed offline Reading Queue ordering, favorites, Library filters/sorting, and saved view preferences.
- Improved local search to ignore case and accents.
- Retained canonical issue records for Library, Sagas, Queue, and Dashboard statistics.

Persistence: Room schema 3 adds queue fields and favorites through migration 2→3; version 1 upgrades continue through 1→2→3. Existing dates, ratings, identifiers, covers and saga membership are retained.

Validation: 14 unit tests passed; assembleDebug, lintDebug and assembleDebugAndroidTest passed. Device instrumentation and visual interaction tests could not run because no working ADB device connection was available.

Limitations: history shows the current completion of each issue, not separate rereading events. Continue Reading uses locally known issues and cannot infer missing offline catalog entries. Unknown completion dates are excluded from dated charts. The attached installable APK is signed with the project's debug key.
