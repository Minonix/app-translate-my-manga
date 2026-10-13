# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

TMM Mobile — native Android companion app for `Translate My Manga` (a Windows desktop translation engine). Single Gradle module (`app`), Kotlin + Jetpack Compose, `applicationId com.tmm.mobile`. The phone does no translation itself; it scans manga chapters downloaded by the Mihon reader, packages them as CBZ, exchanges jobs with the desktop app through a Google Drive mailbox, and installs translated chapters back into Mihon's local library.

Full requirements/spec: [ORIGINAL_REQUEST.md](ORIGINAL_REQUEST.md), [docs/CAHIER_DES_CHARGES.md](docs/CAHIER_DES_CHARGES.md), [docs/PLAN_GEMINI.md](docs/PLAN_GEMINI.md). [PROJECT.md](PROJECT.md) and [TEST_INFRA.md](TEST_INFRA.md) describe the original plan/spec — treat package names and paths in them as aspirational, not authoritative; the sections below reflect what actually exists in `app/src/`.

## Commands

All commands run through the Gradle wrapper from the repo root (`gradlew.bat` on Windows, `./gradlew` in bash).

```bash
./gradlew testDebugUnitTest          # full unit/Robolectric test suite
./gradlew test --tests "*.ClassName" # single test class
./gradlew test --tests "*.ClassName.methodName"  # single test method
./gradlew testDebugUnitTest --tests "*ScreenshotTest" "-Proborazzi.test.record=true"  # write Roborazzi screenshots to app/build/outputs/roborazzi/
./gradlew lintDebug                  # must be 0 errors, 0 warnings
./gradlew assembleDebug              # app-debug.apk
./gradlew assembleRelease            # signed if keystore.properties present (see keystore.properties.example)
```

CI (`.github/workflows/android.yml`) runs `testDebugUnitTest lintDebug assembleRelease` on every push to `main`/`feat/v1-android` and publishes a GitHub release on `v*` tags.

Tests are JVM-only (Robolectric), no emulator/device needed. On Windows, if `user.home` contains a space, `app/build.gradle.kts` auto-redirects Robolectric's native-lib cache to a space-free path (`%ProgramData%/tmm-robolectric-home`) — this is already handled, don't rediagnose it if tests fail for unrelated reasons.

## Architecture

No DI framework (no Hilt), no Retrofit, no Material Icons — these are deliberate constraints from the original spec, enforced by guard tests (see below). Dependencies are manual lazy singletons via `AppContainer`/`DefaultAppContainer` ([core/AppContainer.kt](app/src/main/java/com/tmm/mobile/core/AppContainer.kt)), exposed through `(applicationContext as TmmApp).container`. WorkManager workers fetch dependencies the same way rather than via constructor injection or a custom `WorkerFactory`.

Startup flow: `Startup.kt` (root package) is the startup orchestrator, re-run on every `ON_START` and by the Home "Refresh" action, serialized by a `Mutex`. It gates on onboarding/Mihon SAF permission (`StartupResult`), imports the `.tachibk` backup if needed, then scans Mihon's `downloads/`. Results are also broadcast through `results: SharedFlow` so the root UI can redirect to Permission when Mihon access is revoked.

Package layout under `com.tmm.mobile`:
- `core` — `AppContainer`, `AppLog` (rolling log file with PII redaction), `Clock`, `Formatters`, `ReducedMotion`.
- `ui.theme` — closed color palette (`TmmColors`), typography (Bricolage Grotesque / IBM Plex Sans / IBM Plex Mono), `TmmTheme`, motion curves.
- `ui.icons` — hand-transcribed vector icons (`TmmIcons`); `androidx.compose.material.icons` is banned.
- `ui.components` — reusable maison Compose components (bottom bar, switches, sheets, badges, etc.) matching the HTML mockups.
- `ui.nav` — navigation graph / route wiring.
- `ui.screens.<screen>` — one package per screen (main, library, series, translate, queue (Activity tab), settings, translationsettings, onboarding, empty, offline, permission), each following `<Screen>Route` + `<Screen>Screen` + `<Screen>State` + `<Screen>ViewModel`.
- `ui.mock` — fake/demo data feeding screens before real repositories are wired.
- `data`, `data.db`, `data.status` — Room database (`TmmDatabase`), DAOs, `AppPrefs` (DataStore), `JobRepository`, `RefreshCoordinator`, `SettingsSync`, and pure chapter/series status derivation functions.
- `mihon` — SAF access to the Mihon folder tree, `.tachibk` protobuf backup parsing, filename sanitization, CBZ packing, atomic local installer.
- `protocol` — JSON contracts exchanged with the desktop app through the Drive mailbox (`request.json`, `status.json`, `result.json`, settings commands).
- `transport` — `DriveApi`/`DriveTransport` (raw OkHttp Google Drive v3 REST, no Google API client library), `GoogleAuth` (`play-services-auth`, `drive.file` scope).
- `workers` — WorkManager `CoroutineWorker`s (`SendJobWorker`, `InstallJobWorker`, `OutboxWorker`) plus shared `WorkerSupport`.

Test code mirrors this under `app/src/test/java/com/tmm/mobile/`, plus:
- `fake` — `FakeDrive` (MockWebServer-backed Google Drive double) and `FakePc` (desktop daemon simulator), used for hermetic end-to-end job-flow tests without any network access.
- `ui/theme/NoRawColorTest.kt`, `ui/icons/NoMaterialIconsTest.kt` — architectural guard tests enforcing the closed palette and the Material-Icons ban; keep these passing when touching theming/icons.
- `screenshots/*ScreenshotTest` — Roborazzi captures, one class per screen. There is no Roborazzi Gradle plugin, so no `verify`/`record` tasks and no automatic comparison: captures are only written with `-Proborazzi.test.record=true`, into `app/build/outputs/roborazzi/` (unversioned), then compared by eye with the mockup. Copy the v2 ones into `maquettes/v2-simplifiee/captures/`.

`maquettes/v2-simplifiee/index.html` is the authoritative mockup for the main screens (Home, Library, Series, Translate sheet, Activity, Settings, Translation settings); `maquettes/*.dc.html` at the root still apply to Onboarding, Permission, Empty and Offline; `maquettes/v1/` is archived (see `maquettes/README.md`). Every screen must match its mockup; when a screen's visuals are in question, check the corresponding mockup before guessing.

## Conventions

- Commit messages and in-code comments are in French. Commits follow Conventional Commits (`feat:`, `fix:`, `perf:`, `refactor:`, `test:`, `docs:`, `build:`, `ci:`, `chore:`; `!` or `BREAKING CHANGE:` for a break). Older history uses `E4.1:`-style milestones and `v1.0.x:` prefixes; don't copy them.
- Versioning is SemVer, documented at the top of [CHANGELOG.md](CHANGELOG.md): MAJOR = incompatibility (PC protocol, destructive Room migration), MINOR = new feature, PATCH = fix/perf. A release = bump `versionName` + `versionCode` (+1) in `app/build.gradle.kts`, add a CHANGELOG section, commit `chore(release): vX.Y.Z`, then push the `vX.Y.Z` tag (CI builds the APK and creates the GitHub release). Releases are tagged from `main`.
- Deliberate simplifications are marked with a `ponytail:` comment explaining the cut corner and when to revisit it (see examples in [app/build.gradle.kts](app/build.gradle.kts) and `AppContainer.kt`) — follow this pattern for new ones instead of leaving unexplained hacks.
- `app/schemas/` holds Room's exported schema JSON (KSP `room.schemaLocation`) — do not hand-edit; it's regenerated by the Room compiler on build.
