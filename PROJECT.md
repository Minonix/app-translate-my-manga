# Project: TMM Mobile V1

## Architecture

### System Overview
TMM Mobile is a companion Android native client for `Translate My Manga Win` (TMM-Win). The mobile client does not execute heavy ML translation locally. Instead, it coordinates:
1. Scanning downloaded manga chapters from Mihon reader (`Mihon/downloads/`) via Android Storage Access Framework (SAF).
2. Packaging chapters into uncompressed CBZ archives (`CbzPacker`).
3. Uploading jobs to a Google Drive mailbox (`TranslateMyManga/jobs/<jobId>/`) using raw OkHttp Google Drive v3 REST.
4. Monitoring translation stages (`fetch`, `ocr`, `translate`, `lettering`, `verify`) updated by TMM-Win desktop daemon via Drive status files.
5. Downloading translated CBZ archives from Drive directly into Mihon local manga storage (`Mihon/local/`) using an atomic staging and verification installer (`LocalInstaller`).
6. Reading Mihon library catalog directly from automatic backup files (`.tachibk`) using Gunzip and Protobuf deserialization.
7. Enabling remote management of desktop translation engine settings via hot JSON command sync without restarting the desktop daemon.

### Package & Module Boundaries
Single-module architecture (`app`) organized cleanly by domain packages:
- `com.tmm.mobile.core`: Base application, `TmmApp`, `AppContainer` (manual DI), logging (`AppLog` with PII redaction), `Clock`.
- `com.tmm.mobile.ui.theme`: Strict Design System: `TmmColors` (closed 35 hex / 13 RGBA palette), `TmmType` (Bricolage Grotesque, IBM Plex Sans, IBM Plex Mono), `TmmTheme`, `Motion` (CSS animation curves & transitions).
- `com.tmm.mobile.ui.icons`: `TmmIcons` (SVG path transcription to Compose `ImageVector`, strictly prohibiting `androidx.compose.material.icons`).
- `com.tmm.mobile.ui.components`: 18 maison components (`TmmBottomBar`, `TmmSwitch`, `TmmCheckbox`, `TmmSegmented`, `TmmChip`, `TmmButton`, `TmmSearchField`, `TmmSheet`, `TmmDialog`, `TmmToast`, `OfflineBanner`, `Cover`, `StatusBadge`, `PresenceIcons`, `ProgressBar`, `RefreshButton`, `SectionLabel`, `DashedBorder`).
- `com.tmm.mobile.ui.navigation`: `TmmRoot`, Navigation Compose graph, bottom tab routing, back stack management.
- `com.tmm.mobile.ui.screens.<screen>`: 13 screens implementing unidirectional data flow: `<Screen>State`, `<Screen>Event`, `<Screen>Screen`, `<Screen>ViewModel`.
- `com.tmm.mobile.database`: Room SQLite database `TmmDatabase` (13 entities, DAOs, type converters), `AppPrefs` (DataStore).
- `com.tmm.mobile.domain`: Pure functional domain logic: `ChapterStatus` (8 derived states), `SeriesSummary`, `MissingChapters`, `Formatters`.
- `com.tmm.mobile.mihon`: SAF direct queries (`MihonTree`, `MihonFs`), Protobuf backup parser (`BackupReader`, `BackupImporter`), filename sanitization (`MihonNaming`), CBZ packer (`CbzPacker`), atomic installer (`LocalInstaller`).
- `com.tmm.mobile.transport`: Network transport abstractions: `DriveApi`, `DriveTransport`, `JobTransport`, `GoogleAuth`, protocol models and serialization (`ProtocolModels`, `ProtocolJson`, `SettingsSchema`).
- `com.tmm.mobile.jobs`: Background orchestration: `JobRepository`, WorkManager workers (`SendJobWorker`, `InstallJobWorker`, `OutboxWorker`), `RefreshCoordinator` (anti-spam 30s cooldown), `Startup` lifecycle coordinator, `SettingsSync`.
- `com.tmm.mobile.guard`: Architectural guard tests (`NoRawColorTest`, `NoMaterialIconsTest`, `PaletteCoverageTest`, `IconCoverageTest`).
- `com.tmm.mobile.mockup`: TSV string verification (`MockupTextCoverageTest`), `MockupData`.
- `com.tmm.mobile.screenshots`: Roborazzi native visual regression test suite (26 captures).
- `com.tmm.mobile.testutil`: Hermetic test doubles: `FakeDrive` (MockWebServer), `FakePc`, `FakeMihonFs`, `TestClock`.

### Key Shared Interfaces & Contracts
1. **Dependency Injection**:
   ```kotlin
   interface AppContainer {
       val context: Context
       val clock: Clock
       val database: TmmDatabase
       val appPrefs: AppPrefs
       val mihonFs: MihonFs
       val googleAuth: GoogleAuth
       val driveApi: DriveApi
       val jobTransport: JobTransport
       val jobRepository: JobRepository
       val refreshCoordinator: RefreshCoordinator
       val settingsSync: SettingsSync
   }
   ```
2. **Filesystem Abstraction**:
   ```kotlin
   interface MihonFs {
       suspend fun hasPersistedTreeUri(): Boolean
       suspend fun validateSubfolders(treeUri: Uri): MihonValidationResult
       suspend fun readLatestBackup(treeUri: Uri): ByteArray?
       suspend fun scanDownloads(treeUri: Uri): List<DownloadedChapterInfo>
       suspend fun packCbz(treeUri: Uri, chapterInfo: DownloadedChapterInfo, destFile: File): CbzPackResult
       suspend fun installCbz(treeUri: Uri, localFolder: String, chapterFile: String, sourceCbz: File, expectedSize: Long): Boolean
   }
   ```
3. **Transport Abstraction**:
   ```kotlin
   interface JobTransport {
       suspend fun checkConnection(): ConnectionStatus
       suspend fun getPcHeartbeat(): PcHeartbeat?
       suspend fun getPcIndex(): PcIndex?
       suspend fun uploadJob(job: JobEntity, cbzFile: File, onProgress: (transferred: Long, total: Long) -> Unit): String
       suspend fun downloadResult(fileId: String, destFile: File, onProgress: (transferred: Long, total: Long) -> Unit)
       suspend fun sendCommand(command: PcCommand): String
       suspend fun cancelJob(jobId: String)
       suspend fun confirmReceived(jobId: String)
   }
   ```

---

## Code Layout

```
app-translate-my-manga/
├── .gitignore
├── local.properties (ignored)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
├── docs/
│   ├── CAHIER_DES_CHARGES.md
│   ├── PLAN_GEMINI.md
│   ├── RAPPORT_GEMINI.md
│   └── rapport_gemini/
│       └── compare/ (1560x1688 side-by-side comparison PNGs)
├── maquettes/
│   ├── *.dc.html (13 authoritative mockups)
│   ├── captures/ (13 reference 780x1688 PNGs)
│   └── ...
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    ├── schemas/ (Room exported schemas)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/
        │   │   └── licenses/ (OFL.txt for Google Fonts)
        │   ├── res/
        │   │   ├── font/ (Bricolage Grotesque, IBM Plex Sans, IBM Plex Mono)
        │   │   ├── values/ (strings.xml, plurals.xml, themes.xml)
        │   │   └── mipmap-*/ (adaptive launcher icons)
        │   └── java/com/tmm/mobile/
        │       ├── TmmApp.kt
        │       ├── MainActivity.kt
        │       ├── core/
        │       ├── ui/
        │       │   ├── theme/
        │       │   ├── icons/
        │       │   ├── components/
        │       │   ├── navigation/
        │       │   └── screens/
        │       ├── database/
        │       ├── domain/
        │       ├── mihon/
        │       ├── transport/
        │       └── jobs/
        └── test/
            ├── resources/
            │   ├── mockup_texts.tsv
            │   └── fixtures/ (tachibk, protobuf, json fixtures)
            ├── screenshots/ (Roborazzi single screen captures)
            └── java/com/tmm/mobile/
                ├── guard/ (NoRawColorTest, NoMaterialIconsTest, PaletteCoverageTest, IconCoverageTest)
                ├── mockup/ (MockupTextCoverageTest)
                ├── screenshots/ (13 Roborazzi visual test classes)
                ├── domain/ (DaoTest, ChapterStatusTest, SeriesSummaryTest, FormattersTest)
                ├── mihon/ (BackupReaderTest, DownloadScannerTest, CbzPackerTest, LocalInstallerTest)
                ├── transport/ (DriveTransportTest, ProtocolModelsTest)
                ├── jobs/ (JobFlowTest, SettingsSyncTest, RefreshCoordinatorTest)
                └── testutil/ (FakeDrive, FakePc, FakeMihonFs, TestClock)
```

---

## Feature Inventory

| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Repository & Branch Setup | Branch `feat/v1-android`, `.gitignore` with 11 strict entries, `local.properties` Android SDK config, baseline `RAPPORT_GEMINI.md`. | E0 | survey / PLAN_GEMINI 9.1 |
| 2 | Tooling & Build Diagnostics | JDK 26+ (target 17), Android SDK platforms 36/37 & build-tools 36.0.0, cached Gradle 9.7.0, Python 3.13.1 verification. | E0 | survey / PLAN_GEMINI 2.1 |
| 3 | Version Catalog & Gradle Setup | `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` (Kotlin 2.x, AGP stable, Compose BOM, Room, WorkManager, Roborazzi). | E1 | survey / PLAN_GEMINI 9.2 |
| 4 | Empty Application Shell | `AndroidManifest.xml`, `strings.xml`, `themes.xml`, `TmmApp`, `MainActivity`, `AppContainer` DI skeleton, `SmokeTest` verifying startup. | E1 | survey / PLAN_GEMINI 9.2 |
| 5 | Official Google Fonts Bundling | Bundling Bricolage Grotesque, IBM Plex Sans, IBM Plex Mono TTF files in `res/font/` with OFL licenses in `assets/licenses/`. | E2 | survey / PLAN_GEMINI 4.4 |
| 6 | Strict Typography System | `TmmType` mapping Bricolage Grotesque (display, opsz), IBM Plex Sans (body), IBM Plex Mono (badges), `includeFontPadding=false`. | E2 | survey / PLAN_GEMINI 4.4 |
| 7 | Strict Color Palette & Tokens | `TmmColors` containing closed 35 hex colors, 13 RGBA values, and semantic color tokens (`ink`, `paper`, `accent`, etc.). | E2 | survey / PLAN_GEMINI 4.5 |
| 8 | Custom Vector Iconography | `TmmIcons` containing custom SVG path transcriptions into Compose `ImageVector` (strictly zero Material Icons). | E2 | survey / PLAN_GEMINI 4.6 |
| 9 | CSS Motion Physics Engine | `Motion.kt` containing Compose equivalents of 15 mockup CSS keyframe animations, `TmmEase` cubic-bezier curves, and `LocalReducedMotion`. | E2 | survey / PLAN_GEMINI 4.7 |
| 10 | Design System Guard Tests | `NoRawColorTest`, `NoMaterialIconsTest`, `PaletteCoverageTest`, and `IconCoverageTest` preventing color/icon leaks. | E3 | survey / PLAN_GEMINI 9.3 |
| 11 | Reusable UI Component Library | 18 maison Compose components matching HTML/CSS templates (`TmmBottomBar`, `TmmSwitch`, `TmmCheckbox`, `TmmSheet`, `Cover`, `RefreshButton`, etc.). | E3 | survey / PLAN_GEMINI 4.9 |
| 12 | Component Behavior & Screenshot Tests | Unit tests and visual screenshot verification for all 18 components across checked, pressed, and disabled states. | E3 | survey / PLAN_GEMINI 9.3 |
| 13 | Screen E4.1 - Accueil (Main) | Home dashboard: PC status, refresh cooldown ring (30s), transfer summary card, active jobs, shortcuts, recent chapters, toast. | E4.1 | survey / Main.dc.html |
| 14 | Screen E4.2 - Bibliothèque (Library) | 3-column manga grid: filters, search, long-press multi-selection mode, 2x2 action bar, retranslate confirmation dialog. | E4.2 | survey / Library.dc.html |
| 15 | Screen E4.3 - Fiche série (Series) | Series details: chapter rows with `PresenceIcons`, stats accordion, missing chapters gap alert, bulk selection sheet, action bar. | E4.3 | survey / Series.dc.html |
| 16 | Screen E4.4 - Transferts (Sync) | Animated transfer pipeline diagram, active uploads/downloads, auto-translate checkbox, scan card with progress. | E4.4 | survey / Sync.dc.html |
| 17 | Screen E4.5 - File (Queue) | Translation queue: 3 tabs (Actives, Terminées, Échecs), parallel slots (N/4), 5-stage pipeline tracker, cancel, retry from page N. | E4.5 | survey / Queue.dc.html |
| 18 | Screen E4.6 - Catalogue (Catalog) | Mihon backup info, 5-step animated backup reimport, series & missing stats, category progress bars. | E4.6 | survey / Catalog.dc.html |
| 19 | Screen E4.7 - Nouvelle traduction | 2-step modal sheet: series selection, chapter checkboxes, select all, auto-translate toggle, batch send action. | E4.7 | survey / NewTranslation.dc.html |
| 20 | Screen E4.8 - Réglages (Settings) | App & PC connection settings: Google Drive group, Mihon group, retention policy, Wi-Fi toggle, log export. | E4.8 | survey / Settings.dc.html |
| 21 | Screen E4.9 - Traduction sur le PC | Remote PC translation settings: sync banner (`SYNCED`/`SENDING`/`WAITING_PC`), engine options, sliders, collapsible vision. | E4.9 | survey / TranslationSettings.dc.html |
| 22 | Screen E4.10 - Onboarding | 3-step setup: Google Drive OAuth login, Mihon SAF storage permission + animated folder validation, initial backup sync. | E4.10 | survey / Onboarding.dc.html |
| 23 | Screen E4.11 - État vide (Empty) | Empty home state: floating book halftone illustration, "Scanner Mihon" button with spinner, backup guidance card. | E4.11 | survey / Empty.dc.html |
| 24 | Screen E4.12 - Hors ligne (Offline) | Transverse error state: offline banners (no network vs expired token with "Reconnecter"), read-only guidance. | E4.12 | survey / Offline.dc.html |
| 25 | Screen E4.13 - Permission perdue | Blocking error screen when SAF access to Mihon folder is revoked; directory re-selection launcher. | E4.13 | survey / Permission.dc.html |
| 26 | Mockup Text TSV Catalog | Complete 276 static literal string mapping in `app/src/test/resources/mockup_texts.tsv` with STR, FMT, PLU, DEMO types. | E4 | survey / PLAN_GEMINI 4.10 |
| 27 | Mockup Text Coverage Verification | `MockupTextCoverageTest` ensuring 100% character-exact string reproduction from HTML templates into Android resources. | E4 | survey / PLAN_GEMINI 4.10 |
| 28 | Roborazzi Visual Regression Suite | Automated 780x1688 px screenshot captures (13 initial + 13 interaction states) and 1560x1688 px side-by-side comparison PNGs. | E4 | survey / PLAN_GEMINI 4.13 |
| 29 | Room Relational Database Schema | 13 SQLite tables (`TmmDatabase`, schema v1): series, category, chapter, downloaded_chapter, job, quality, settings, gaps, etc. | E5 | survey / PLAN_GEMINI 5.1-5.13 |
| 30 | Room DAOs & Converters | Type-safe DAOs with Flow queries, batch transactions, and JSON converters for `List<Int>` and `List<QualityAlert>`. | E5 | survey / PLAN_GEMINI 5.14 |
| 31 | DataStore Preferences | `AppPrefs` managing onboarding, auth state, Drive folder IDs, sync cursors, retention, Wi-Fi only, protocol flags. | E5 | survey / PLAN_GEMINI 5.15 |
| 32 | Pure Chapter Status Derivation | Pure function `deriveStatus(ChapterFacts)` evaluating 8 mutually exclusive states (`TO_SEND`, `QUEUED`, `TRANSLATING`, etc.). | E5 | survey / PLAN_GEMINI 5.16 |
| 33 | Series Summary & Gap Calculation | Pure function computing aggregated chapter counts, FR ratios, badge states, and calculating missing integer chapter gaps. | E5 | survey / PLAN_GEMINI 5.17 |
| 34 | High-Performance SAF Scanner | Direct `DocumentsContract` queries over Mihon tree URI (`downloads/`, `local/`, `autobackup/`) bypassing slow `DocumentFile`. | E6 | survey / PLAN_GEMINI 6.1 |
| 35 | Mihon Protobuf Backup Parser | Gunzip stream + `kotlinx.serialization.protobuf` deserializer decoding `.tachibk` files while tolerating unknown fork tags. | E6 | survey / PLAN_GEMINI 6.2 |
| 36 | Atomic Catalog Importer | Single Room database transaction clearing and repopulating manga catalog tables from parsed Mihon backup. | E6 | survey / PLAN_GEMINI 6.3 |
| 37 | Chapter Filename Sanitization & Matching | Implementation of Mihon's exact filename/directory sanitization algorithm matching downloaded folders to catalog chapters. | E6 | survey / PLAN_GEMINI 6.4 |
| 38 | Stored CBZ Packaging Engine | `CbzPacker` zipping page images with `ZipEntry.STORED` (no recompression), natural numerical sort, CRC-32, and image filtering. | E6 | survey / PLAN_GEMINI 6.5 |
| 39 | Atomic Local CBZ Installer | `LocalInstaller` writing to hidden temp file, verifying size against `result.json`, deleting old file, and atomically renaming. | E6 | survey / PLAN_GEMINI 6.6 |
| 40 | Google Drive v3 REST Client | Lightweight REST client over OkHttp for file query, multipart create, download, delete, and metadata without Google API client. | E7 | survey / PLAN_GEMINI 7.1 |
| 41 | Resumable Chunked Upload & Recovery | 8 MiB chunked upload with Range header queries to resume interrupted CBZ transfers without restarting from byte 0. | E7 | survey / PLAN_GEMINI 7.2 |
| 42 | Dual-Client Mailbox JSON Protocols | JSON serialization for `protocol.json`, `request.json`, `status.json`, `result.json`, `cancel.json`, `received.json`, `commands`. | E7 | survey / PLAN_GEMINI 7.3 |
| 43 | Google Identity OAuth Authorization | Integration with `play-services-auth` requesting `drive.file` scope, token caching, and 401 expiration handling. | E7 | survey / PLAN_GEMINI 7.4 |
| 44 | Hermetic Drive & PC Simulators | `FakeDrive` (MockWebServer Dispatcher) and `FakePc` simulating full desktop daemon and network without internet access. | E7 | survey / PLAN_GEMINI 7.5 |
| 45 | WorkManager Job Workers | Foreground sync workers (`SendJobWorker`, `InstallJobWorker`, `OutboxWorker`) with `FOREGROUND_SERVICE_DATA_SYNC`. | E8 | survey / PLAN_GEMINI 8.1 |
| 46 | Anti-Spam Refresh Coordinator | Incremental Drive change polling protected by `Mutex.tryLock()` and 30-second cooldown countdown ring. | E8 | survey / PLAN_GEMINI 8.2 |
| 47 | End-to-End Simulation Test | `JobFlowTest` simulating complete lifecycle: create job -> send -> PC process -> download -> install -> verify Mihon/local. | E8 | survey / PLAN_GEMINI 8.3 |
| 48 | Hot Translation Settings Sync | Remote modification of PC translation engine settings via JSON commands, polling with 60s timeout to `WAITING_PC`. | E9 | survey / PLAN_GEMINI 9.1 |
| 49 | Real ViewModel Integration | Connecting all 13 screens to real Room repositories, DataStore preferences, and WorkManager job dispatchers. | E10 | survey / PLAN_GEMINI 10.1 |
| 50 | App Lifecycle & Startup Orchestration | `Startup` sequence: check onboarding -> check SAF -> check backup freshness -> scan downloads -> auto-translate -> refresh. | E10 | survey / PLAN_GEMINI 10.2 |
| 51 | Rolling Log with PII Redaction | `AppLog` file logger writing to `tmm-journaux-<date>.txt` with email and auth token redaction. | E10 | survey / PLAN_GEMINI 10.3 |
| 52 | Lint & Static Analysis Zero-Defect | Android Lint (`lintDebug`) configured and passing with exactly 0 errors and 0 warnings. | E11 | survey / PLAN_GEMINI 11.1 |
| 53 | ProGuard / R8 Optimization & Packaging | `proguard-rules.pro` keeping serialization models, producing validated `app-debug.apk` and signed release APK. | E11 | survey / PLAN_GEMINI 11.2 |
| 54 | Execution Report Finalization | Complete `docs/RAPPORT_GEMINI.md` recording all commit hashes, tool versions, test outputs, and Roborazzi comparisons. | E11 | survey / PLAN_GEMINI 11.3 |
| 55 | Opaque-Box E2E Test Suite (Tiers 1-4) | Independent opaque-box acceptance test suite covering all features and boundary edge cases derived from user specs. | E2E | survey / Dual Track |
| 56 | Adversarial Hardening (Tier 5) | White-box stress testing, chaos fuzzing, chunk dropping, and race condition verification. | E2E | survey / Dual Track |

---

## Milestones

| # | Name | Scope | Dependencies | Status | Git Commit |
|---|------|-------|-------------|--------|------------|
| **E0** | Préparation du dépôt | Branch `feat/v1-android`, `.gitignore`, `local.properties`, verify JDK 26/Android SDK/Gradle, init `RAPPORT_GEMINI.md`. | none | DONE (f326868) | `E0: preparation du depot` |
| **E1** | Squelette Gradle & App vide | Gradle wrapper, `libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, Manifest, `TmmApp`, `MainActivity`, `AppContainer` DI skeleton, `SmokeTest`. | E0 | PLANNED | `E1: squelette gradle et application vide` |
| **E2** | Thème, polices, icônes & animations | Bundled Google Fonts, OFL licenses, `TmmType`, `TmmColors`, `TmmTheme`, `TmmIcons`, `Motion.kt`, adaptive icons. | E1 | PLANNED | `E2: theme polices icones animations` |
| **E3** | Composants maison & tests de garde | 18 maison components, `MockupData`, `NoRawColorTest`, `NoMaterialIconsTest`, `PaletteCoverageTest`, `IconCoverageTest`, component tests. | E2 | PLANNED | `E3: composants maison` |
| **E4** | Écrans données factices & Roborazzi | 13 screens (E4.1 - E4.13), `mockup_texts.tsv`, `MockupTextCoverageTest`, Roborazzi visual tests (26 captures), side-by-side PNGs. | E3 | PLANNED | `E4.<n>: ecran <Nom>` (13 commits) |
| **E5** | Room, DataStore & dérivations pures | `TmmDatabase` (13 tables, DAOs, converters), `AppPrefs` DataStore, pure functions `ChapterStatus`, `SeriesSummary`, `MissingChapters`, unit tests. | E1 (tested with E3/E4) | PLANNED | `E5: room datastore derivations` |
| **E6** | Accès SAF, parsing Mihon, CBZ & install | `MihonTree` SAF queries, `BackupReader` (gunzip+protobuf), `BackupImporter`, `MihonNaming`, `CbzPacker`, `LocalInstaller`, unit tests. | E5 | PLANNED | `E6: lecture mihon sauvegarde scan installation` |
| **E7** | FakeDrive, FakePc & transport REST | `ProtocolModels`, `ProtocolJson`, `SettingsSchema`, `DriveApi`, `DriveTransport`, `GoogleAuth`, `FakeDrive` (MockWebServer), `FakePc`. | E5 | PLANNED | `E7: protocole drive faux drive faux pc` |
| **E8** | Jobs, workers & rafraîchissement | `JobRepository`, `SendJobWorker`, `InstallJobWorker`, `OutboxWorker`, `RefreshCoordinator`, `Startup`, `JobFlowTest` hermetic simulation. | E6, E7 | PLANNED | `E8: jobs workers rafraichissement` |
| **E9** | Réglages de traduction à distance | `SettingsSync`, remote settings command dispatch, polling loop with 60s timeout, banner states, `SettingsSyncTest`. | E7 | PLANNED | `E9: reglages de traduction a distance` |
| **E10** | Données réelles dans les écrans | Real ViewModel integration connecting E4 screens to E5-E9 repositories, `TmmRoot` navigation, onboarding/SAF permissions, log export. | E4, E8, E9 | PLANNED | `E10: donnees reelles dans les ecrans` |
| **E11** | Durcissement, lint, ProGuard & APK | `lintDebug` (0 errors), `proguard-rules.pro`, `assembleDebug` (`app-debug.apk`), `assembleRelease`, complete `RAPPORT_GEMINI.md`. | E10 | PLANNED | `E11: verification finale et apk` |
| **E2E** | Opaque-Box Test Suite & Hardening | Independent test suite (Tiers 1-4) published to `TEST_READY.md`, followed by Tier 5 adversarial hardening. | E0 - E11 | PLANNED | `test: suite e2e et durcissement adversarial` |

---

## Interface Contracts

### 1. `MihonFs` ↔ `AppContainer` & `DownloadScanner`
```kotlin
package com.tmm.mobile.mihon

import android.net.Uri
import java.io.File

interface MihonFs {
    suspend fun hasPersistedTreeUri(): Boolean
    suspend fun validateSubfolders(treeUri: Uri): MihonValidationResult
    suspend fun readLatestBackup(treeUri: Uri): ByteArray?
    suspend fun scanDownloads(treeUri: Uri): List<DownloadedChapterInfo>
    suspend fun packCbz(treeUri: Uri, chapterInfo: DownloadedChapterInfo, destFile: File): CbzPackResult
    suspend fun installCbz(treeUri: Uri, localFolder: String, chapterFile: String, sourceCbz: File, expectedSize: Long): Boolean
}

data class MihonValidationResult(
    val hasDownloads: Boolean,
    val hasLocal: Boolean,
    val hasAutoBackup: Boolean
) {
    val isValid: Boolean get() = hasDownloads && hasLocal && hasAutoBackup
}

data class DownloadedChapterInfo(
    val sourceDir: String,
    val seriesDir: String,
    val chapterDir: String,
    val documentUri: Uri,
    val isCbz: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val pageCount: Int
)

data class CbzPackResult(
    val file: File,
    val sizeBytes: Long,
    val sha256: String,
    val pageCount: Int
)
```

### 2. `JobTransport` ↔ `DriveTransport` & `FakeDrive`
```kotlin
package com.tmm.mobile.transport

import com.tmm.mobile.database.entities.JobEntity
import java.io.File

interface JobTransport {
    suspend fun checkConnection(): ConnectionStatus
    suspend fun getPcHeartbeat(): PcHeartbeat?
    suspend fun getPcIndex(): PcIndex?
    suspend fun uploadJob(job: JobEntity, cbzFile: File, onProgress: (transferred: Long, total: Long) -> Unit): String
    suspend fun downloadResult(fileId: String, destFile: File, onProgress: (transferred: Long, total: Long) -> Unit)
    suspend fun sendCommand(command: PcCommand): String
    suspend fun cancelJob(jobId: String)
    suspend fun confirmReceived(jobId: String)
}

sealed class ConnectionStatus {
    object Connected : ConnectionStatus()
    data class AuthExpired(val message: String) : ConnectionStatus()
    data class ProtocolTooNew(val serverVersion: Int) : ConnectionStatus()
    data class Offline(val cause: Throwable) : ConnectionStatus()
}
```

### 3. `ChapterStatus` Pure Derivation
```kotlin
package com.tmm.mobile.domain

enum class ChapterStatus {
    TO_SEND,
    QUEUED,
    TRANSLATING,
    FAILED,
    TO_FETCH,
    UP_TO_DATE,
    NOT_TRANSLATED,
    CATALOG_ONLY
}

data class ChapterFacts(
    val hasDownloaded: Boolean,
    val hasLocalFr: Boolean,
    val indexOriginal: Boolean,
    val indexFr: Boolean,
    val latestJobState: String?,     // PREPARING, UPLOADING, SENT, etc.
    val latestJobPcState: String?,   // queued, translating, failed, done
    val latestJobFailed: Boolean
)

object ChapterStatusDerivation {
    fun deriveStatus(facts: ChapterFacts): ChapterStatus {
        if (facts.latestJobFailed) return ChapterStatus.FAILED
        if (facts.latestJobState in listOf("PREPARING", "UPLOADING", "SENT", "CANCEL_REQUESTED")) {
            return if (facts.latestJobPcState == "translating") ChapterStatus.TRANSLATING else ChapterStatus.QUEUED
        }
        if (facts.latestJobState in listOf("RESULT_READY", "DOWNLOADING")) return ChapterStatus.TO_FETCH
        if (facts.hasLocalFr) return ChapterStatus.UP_TO_DATE
        if (facts.indexFr) return ChapterStatus.TO_FETCH
        if (facts.indexOriginal) return ChapterStatus.NOT_TRANSLATED
        if (facts.hasDownloaded) return ChapterStatus.TO_SEND
        return ChapterStatus.CATALOG_ONLY
    }
}
```

### 4. `SettingsSync` Command & State Sync
```kotlin
package com.tmm.mobile.jobs

import kotlinx.coroutines.flow.StateFlow

interface SettingsSync {
    val state: StateFlow<SettingsSyncState>
    suspend fun updateSetting(key: String, value: Any): Boolean
    suspend fun pollConfirmation(timeoutSeconds: Int = 60)
}

sealed class SettingsSyncState {
    object Synced : SettingsSyncState()
    data class Sending(val key: String) : SettingsSyncState()
    data class Applied(val key: String) : SettingsSyncState()
    data class WaitingPc(val message: String) : SettingsSyncState()
}
```
