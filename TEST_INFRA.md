# Master E2E Test Infrastructure Specification: TMM Mobile V1

**Document Version:** 1.0.0  
**Date:** 2026-09-26  
**Status:** Approved Architectural Specification  
**Integrity Mode:** Opaque-Box, Requirement-Driven, Implementation-Independent  
**Author:** `test_writer_e2e_0` (Dedicated E2E Testing Track Author)  
**Parent Orchestrator:** `bc66d14b-5035-46e5-90d7-4fe88a702b08`  
**Authoritative References:** `ORIGINAL_REQUEST.md`, `PROJECT.md`, `docs/CAHIER_DES_CHARGES.md` (v1.1), `docs/PLAN_GEMINI.md` (v1.0), `maquettes/*.dc.html`.

---

## 1. Executive Summary & Testing Philosophy

### 1.1 Core Mission
This document defines the complete End-to-End (E2E) test infrastructure and specification for **TMM Mobile V1** (`app-translate-my-manga`). TMM Mobile is a native Android companion application (Kotlin 2.x, Jetpack Compose, Room, WorkManager, OkHttp) that coordinates manga translation with a headless desktop daemon (`Translate My Manga Win` / TMM-Win) via Google Drive REST v3 and the Android Storage Access Framework (SAF).

### 1.2 Opaque-Box Testing Principles
1. **Requirement-Driven**: Every test case is derived strictly from observable requirements defined in `docs/CAHIER_DES_CHARGES.md` and `PROJECT.md`.
2. **Implementation-Independent**: Tests treat the application as an opaque system. Verification inspects observable interfaces:
   - File system changes (SAF `downloads/`, `local/`, `autobackup/`).
   - SQLite Room database contents (entities, counts, query results).
   - Remote Google Drive REST requests, headers, and mailbox files (`request.json`, `status.json`, `result.json`).
   - UI StateFlow states, navigation destinations, and accessibility semantics.
   - Internal classes, private functions, and refactorable implementations are never targeted.
3. **No Facade Testing**: No mock or test may pass unconditionally without validating the underlying logical transformation, data consistency, and protocol contract.
4. **Hermetic & Deterministic**: Zero dependency on active Google Cloud API keys, real Google Drive networks, physical Windows PCs, or external internet connections. All tests run locally within reproducible JVM test environments (JUnit 4, Robolectric, MockWebServer).

### 1.3 Progressive Testability Contract
Tests are organized into a strict progressive hierarchy aligned with implementation milestones (E0 through E11). A test at milestone $M$ must ONLY depend on features delivered in milestone $M$ or its completed ancestors. Tests never fail due to the absence of features planned for future milestones.

---

## 2. Test Architecture & Execution Engine

### 2.1 Runner Invocations
All tests execute via the project's standard Gradle wrapper:

```bash
# 1. Full E2E and Unit Test Suite (Hermetic Robolectric + JUnit 4)
./gradlew testDebugUnitTest

# 2. Filtered E2E Execution (Only Tier 1 to Tier 4 acceptance tests)
./gradlew testDebugUnitTest --tests "com.tmm.mobile.e2e.*"

# 3. Visual Regression Verification (Roborazzi 780x1688 px matching mockups)
./gradlew verifyRoborazziDebug

# 4. Zero-Defect Static Analysis & Lint
./gradlew lintDebug

# 5. Production & Debug Artifact Build Verification
./gradlew assembleDebug
./gradlew assembleRelease
```

### 2.2 Test Directory and Package Layout
Test files and test infrastructure reside strictly in standard Android test source directories co-located within the `app` module:

```
app-translate-my-manga/
├── TEST_INFRA.md                          # This master specification
├── app/
│   └── src/
│       └── test/
│           ├── resources/
│           │   ├── mockup_texts.tsv       # Exact literal text catalog (276 entries)
│           │   └── fixtures/
│           │       ├── backups/           # .tachibk sample protobuf files (standard + forks)
│           │       ├── chapters/          # Sample raw image directories and uncompressed CBZs
│           │       └── protocol/          # Golden JSON contracts (request, status, result, settings)
│           └── java/com/tmm/mobile/
│               ├── testutil/              # Shared hermetic test infrastructure
│               │   ├── FakeDrive.kt       # MockWebServer Google Drive v3 REST dispatcher
│               │   ├── FakePc.kt          # Desktop TMM-Win daemon simulator
│               │   ├── FakeMihonFs.kt     # Virtual SAF filesystem implementation
│               │   └── TestClock.kt       # Controllable virtual clock
│               └── e2e/                   # 4-Tier Opaque-Box Acceptance Test Suite
│                   ├── tier1_features/    # Tier 1: Happy path feature coverage (>= 5/feature)
│                   ├── tier2_boundaries/  # Tier 2: Boundary, corner & stress cases (>= 5/feature)
│                   ├── tier3_interactions/# Tier 3: Pairwise cross-feature interaction matrices
│                   └── tier4_workloads/   # Tier 4: Real-world end-to-end user journeys (>= 5)
```

### 2.3 Hermetic Test Doubles Specification

#### 2.3.1 `FakeDrive` (Google Drive v3 REST Mock Server)
- **Engine**: Square `okhttp3.mockwebserver.MockWebServer` with a custom `Dispatcher`.
- **State Store**: In-memory thread-safe map `Map<String, DriveFileRecord>` where:
  ```kotlin
  data class DriveFileRecord(
      val id: String,
      val name: String,
      val mimeType: String,
      val parents: List<String>,
      val modifiedTime: Long,
      val bytes: ByteArray,
      val trashed: Boolean = false
  )
  ```
- **Endpoints Supported**:
  - `GET /drive/v3/files`: Parses query `q` using regex matching `name = '...'`, `'...' in parents`, `trashed = false`, and `modifiedTime > '...'`. Returns JSON `FileList`.
  - `POST /upload/drive/v3/files?uploadType=multipart`: Decodes multipart body; creates metadata JSON and stores binary payload.
  - `POST /upload/drive/v3/files?uploadType=resumable`: Initializes session; returns HTTP 200 with `Location: /upload/resumable/<sessionId>`.
  - `PUT /upload/resumable/<sessionId>`:
    * Supports partial chunk transfers with `Content-Range: bytes START-END/TOTAL`.
    * Stores bytes sequentially; returns HTTP 308 Resume Incomplete with `Range: bytes=0-END` until last byte received.
    * Returns HTTP 200 with metadata on full reception.
    * Injects simulated connection drops on demand after $K$ transmitted bytes.
  - `GET /drive/v3/files/<id>?alt=media`: Streams stored bytes. Supports byte-range queries.
  - `DELETE /drive/v3/files/<id>`: Sets `trashed = true` or purges file.
  - `GET /drive/v3/about?fields=user(emailAddress)`: Returns authenticated user email (`vous@gmail.com`).
- **Fault Injection Capabilities**:
  - `injectRateLimit(retries = 2, retryAfterSeconds = 1)`: Returns HTTP 429 `Retry-After: 1`.
  - `injectAuthRevocation()`: Returns HTTP 401 Unauthorized for next $N$ requests.
  - `injectServerError(code = 503)`: Simulates transient Google Drive internal outages.
  - `injectTruncation(dropAfterBytes = 8388608)`: Closes socket prematurely during resumable chunk upload.

#### 2.3.2 `FakePc` (TMM-Win Desktop Daemon Simulator)
Simulates the behavior of the Windows desktop application interacting through the Drive mailbox:
- **Heartbeat Daemon**:
  * Emits `pc/heartbeat.json` on Drive with `updatedAt = Clock.now()`, `slots = 4`, `activeJobIds`, and `queue` statistics.
- **Job Ingestion & Processing**:
  * Scans `jobs/<jobId>/request.json`. Verifies `source.cbz` exists (in `translate` mode) and validates its SHA-256 against `request.json.file.sha256`.
  * Transitions `status.json` through the 5 pipeline stages: `fetch` (10%) $\to$ `ocr` (30%) $\to$ `translate` (60%) $\to$ `lettering` (80%) $\to$ `verify` (95%).
  * Writes simulated page progress: `"page": P, "pages": TOTAL`.
  * Terminal state: Writes `result.cbz` then `result.json` with quality report (`translatedBubbles`, `untranslatedBubbles`, `sfxKept`, `alerts`).
- **Error Simulation**: Emits `status.json` with `"state": "failed"`, `"error": {"code": "...", "message": "...", "page": N}`.
- **Settings Daemon**: Reads `pc/commands/<cmdId>.json`. Validates against whitelist. Writes updated `pc/settings.json` with incremented `version` and `lastCommandId = cmdId`. Deletes command file.
- **Index Publisher**: Publishes `pc/index.json` reflecting processed library originals and FR chapters.

#### 2.3.3 `FakeMihonFs` (Virtual SAF Storage)
- **Engine**: In-memory directory tree mimicking Android `DocumentsContract` tree URI structure.
- **Emulated Layout**:
  - `autobackup/`: Holds `.tachibk` files.
  - `downloads/<source>/<series>/<chapter>/`: Holds image files (`1.jpg`, `2.jpg`...) or single `.cbz` files.
  - `local/<folder>/<chapter>.cbz`: Destination for translated chapters installed by TMM Mobile.
- **Atomic Operation Emulation**:
  - Validates hidden staging naming convention `.<name>.tmm-<jobId>.tmp`.
  - Simulates atomic rename via `DocumentsContract.renameDocument`.
  - Enforces atomic failure if destination file lock is simulated.

#### 2.3.4 `TestClock`
- Extends application `Clock` contract.
- Provides thread-safe time freezing, discrete `advanceBy(duration)`, and automatic timestamp derivation for determinism across date formatters, cooldown countdowns, and heartbeat expiration checks.

### 2.4 Pass/Fail Semantics and Invariants
Every test in this suite must satisfy the following core invariants:
1. **Zero Uncaught Exceptions**: All error paths must resolve into typed domain errors, UI error states (`OfflineBanner`, `PermissionScreen`, `FAILED` badge), or logged diagnostic events.
2. **Payload Completeness Marker Rule**:
   - `request.json` must NEVER be written to Drive before `source.cbz` upload completes.
   - `result.json` must NEVER be written by PC before `result.cbz` upload completes.
3. **Storage Atomicity**:
   - No `.tmp` staging file may ever remain orphaned in `Mihon/local/` on installation failure.
   - Room catalog updates must execute in a single atomic database transaction (zero intermediate partial state).
4. **Deterministic Exit**: All coroutines, background tasks, and MockWebServer threads must terminate within the test scope. Tests must not leak unconfined coroutines.

---

## 3. Progressive Testability & Dependency Matrix

The test suite is structured so that as the project advances milestone-by-milestone, tests activate without modifying existing test logic:

```
[Milestone E0: Tooling & Git]
       │
       ▼
[Milestone E1: Skeleton & DI] ───────────────► Testable: SmokeTest, AppContainer, DI graph
       │
       ▼
[Milestone E2: Design Tokens & Theme] ──────► Testable: Guard tests (NoRawColor, NoMaterialIcons, Palette, Icon)
       │
       ▼
[Milestone E3: UI Maison Components] ───────► Testable: ComponentsBehaviorTest, ComponentsScreenshotTest
       │
       ▼
[Milestone E4: 13 Screens in Fake Data] ───► Testable: MockupTextCoverageTest, 13 Screen Visual Regressions
       │
       ▼
[Milestone E5: Room DB & Derivations] ──────► Testable: DaoTest, ChapterStatusDerivation, SeriesSummary, Gaps
       │
       ▼
[Milestone E6: SAF Mihon & CBZ Engine] ─────► Testable: BackupReader, DownloadScanner, CbzPacker, LocalInstaller
       │
       ▼
[Milestone E7: Drive REST & Simulators] ────► Testable: FakeDrive, FakePc, DriveTransport, ProtocolModels
       │
       ▼
[Milestone E8: WorkManager & Job Engine] ───► Testable: SendJobWorker, InstallJobWorker, RefreshCoordinator, JobFlowTest
       │
       ▼
[Milestone E9: Remote Settings Sync] ───────► Testable: SettingsSync, Hot command dispatch, 60s timeout loop
       │
       ▼
[Milestone E10: Real ViewModel Wiring] ─────► Testable: 13 Real Screen ViewModels, Full Root Navigation, Onboarding
       │
       ▼
[Milestone E11: Lint, ProGuard & APK] ──────► Testable: Zero-defect lintDebug, ProGuard shrinking, assembleDebug
```

---

## 4. Tier 1: Feature Coverage (Representative Happy Paths)

Tier 1 verifies the primary contract for each application feature under normal operating conditions. **Requirement: Minimum 5 distinct test cases per feature area.**

### Feature 1: Mihon Backup Ingestion & Catalog Management
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §6.2, §8.3; `PLAN_GEMINI.md` §5.6, §9.5.3.*

- **T1-BK-01: Standard Backup Decoding & Room Population**
  - *Input*: Valid `.tachibk` archive (gzip + protobuf) containing 3 manga series, 4 categories, and 12 chapters.
  - *Action*: Ingest via `BackupReader.readBackup()` and execute `BackupImporter.importToDatabase()`.
  - *Expected Output*: Exactly 3 records in `series`, 4 in `category`, 12 in `chapter`, and 1 in `backup_import`. `backup_import.source` is `"autobackup"`.
  - *Assertion*: `database.seriesDao().getAll().size == 3` and all foreign keys in `series_category` are consistent.

- **T1-BK-02: Idempotent Re-Import Suppression**
  - *Input*: Same `.tachibk` file imported twice with identical filename, size, and timestamp.
  - *Action*: Trigger automated startup backup import check twice.
  - *Expected Output*: Second pass detects unchanged metadata and skips decompression and database writes.
  - *Assertion*: Database transaction count remains 1; `backup_import.importedAt` timestamp does not change.

- **T1-BK-03: Tolerant Parsing of Unknown Fork Tags (SY/J2K)**
  - *Input*: `.tachibk` file synthesized with standard fields plus unknown protobuf tags (`tag = 999`, `tag = 1000` with complex varint and length-delimited payloads).
  - *Action*: Decode via `BackupReader`.
  - *Expected Output*: Parsing completes without throwing `SerializationException`. Standard fields are decoded cleanly.
  - *Assertion*: Series title, author, and categories match the input payload exactly.

- **T1-BK-04: Atomic Transaction Replacement on Catalog Refresh**
  - *Input*: Initial database holds 5 old series. A new `.tachibk` contains 2 updated series.
  - *Action*: Execute `BackupImporter.importToDatabase()`.
  - *Expected Output*: Old catalog entries are cleared and replaced atomically. No orphaned records in `series_category` or `chapter`.
  - *Assertion*: `database.seriesDao().getAll().size == 2`; old series IDs return null.

- **T1-BK-05: Category Ordering and Multiple Category Linkage**
  - *Input*: Manga series belonging to two categories: "En cours" (`order = 0`) and "Favoris" (`order = 1`).
  - *Action*: Decode and store.
  - *Expected Output*: `series_category` contains 2 link records for the series with matching `categoryOrder`.
  - *Assertion*: `database.categoryDao().getCategoriesForSeries(seriesKey)` returns 2 categories sorted by `order`.

---

### Feature 2: SAF Download Scanning & Chapter Catalog Matching
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §8.2, §8.5; `PLAN_GEMINI.md` §5.6, §6.4.*

- **T1-SC-01: Directory Hierarchy Traversal & Chapter Ingestion**
  - *Input*: Emulated `downloads/MangaDex (EN)/Blue Lock/Chapter 214/` containing 22 valid JPEG images.
  - *Action*: Execute `DownloadScanner.scan()`.
  - *Expected Output*: 1 record created in `downloaded_chapter` with `mihonPath = "MangaDex (EN)/Blue Lock/Chapter 214"`, `pageCount = 22`, `isCbz = false`.
  - *Assertion*: Record is queryable via DAO; `lastModified` matches directory timestamp.

- **T1-SC-02: Pre-Packaged CBZ Download Ingestion**
  - *Input*: Emulated `downloads/MangaDex (EN)/Chainsaw Man/Ch. 150.cbz` (15 MB single archive).
  - *Action*: Execute `DownloadScanner.scan()`.
  - *Expected Output*: 1 record created in `downloaded_chapter` with `isCbz = true`, `sizeBytes = 15728640`.
  - *Assertion*: `isCbz` flag is true; file size matches filesystem byte count.

- **T1-SC-03: Incremental Scan Optimization via Folder Mtime**
  - *Input*: 5 series folders scanned on pass 1. On pass 2, only Series B has an updated `lastModified` timestamp.
  - *Action*: Execute `DownloadScanner.scan()`.
  - *Expected Output*: Scanner queries children of Series B only. Unchanged series folders are bypassed.
  - *Assertion*: Total `MihonFs.children()` calls equals $1 (\text{root}) + 1 (\text{Series B})$.

- **T1-SC-04: Mihon Sanitization Name Matching (Scanlator + Chapter Name)**
  - *Input*: Catalog chapter: `name = "Chapter 214"`, `scanlator = "Po2Scans"`. Disk directory: `"Po2Scans_Chapter 214"`.
  - *Action*: Run `Matcher.match(downloadedFolder, catalogChapters)`.
  - *Expected Output*: Successfully maps to catalog chapter; populates `seriesKey` and `chapterKey`.
  - *Assertion*: `downloaded_chapter.chapterKey` is non-null and equal to catalog entity `chapterKey`.

- **T1-SC-05: Unmatched Download Handling ("Hors Catalogue")**
  - *Input*: Downloaded chapter folder with non-matching title not present in Mihon backup.
  - *Action*: Run `Matcher.match()`.
  - *Expected Output*: Ingestion succeeds with `seriesKey = null`, `chapterKey = null`.
  - *Assertion*: Chapter status displays as "Hors catalogue"; `mihonPath` is intact and eligible for manual transmission.

---

### Feature 3: Stored CBZ Packaging Engine (`CbzPacker`)
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §4.2, §7.1; `PLAN_GEMINI.md` §5.14.*

- **T1-PK-01: Natural Numerical Sort Order Packaging**
  - *Input*: Directory containing images named: `1.jpg`, `2.jpg`, `10.jpg`, `11.jpg`, `20.jpg`.
  - *Action*: Pack via `CbzPacker.pack()`.
  - *Expected Output*: Output ZIP file contains entries in order: `1.jpg`, `2.jpg`, `10.jpg`, `11.jpg`, `20.jpg` (NOT lexicographical `1`, `10`, `11`, `2`).
  - *Assertion*: Reading `ZipInputStream` entries produces the natural numerical sequence.

- **T1-PK-02: Zero-Compression (`STORED`) Mode & CRC-32 Verification**
  - *Input*: Directory containing 10 JPEG images (total size 12,450,000 bytes).
  - *Action*: Pack via `CbzPacker.pack()`.
  - *Expected Output*: All `ZipEntry` instances have `method = ZipEntry.STORED`. Uncompressed size equals compressed size. Valid CRC-32 checksums set on each entry.
  - *Assertion*: Standard zip readers verify all entry CRCs without CRC32 mismatch errors.

- **T1-PK-03: Supported Image Formats Page Counting**
  - *Input*: Directory containing 2 `.jpg`, 2 `.png`, 2 `.webp`, 1 `.gif`, 1 `.avif`, 1 `.jxl` (total 9 images) plus 1 `notes.txt`.
  - *Action*: Pack via `CbzPacker.pack()`.
  - *Expected Output*: Result `CbzPackResult.pageCount == 9`. `notes.txt` is excluded or omitted from image page count.
  - *Assertion*: `pageCount` equals 9; SHA-256 is deterministic.

- **T1-PK-04: Pass-Through of Pre-Existing CBZ**
  - *Input*: Already existing `.cbz` archive in `downloads/`.
  - *Action*: Ingest via `CbzPacker.pack()`.
  - *Expected Output*: Re-packaging is skipped; source file is verified and referenced directly with calculated SHA-256.
  - *Assertion*: File bytes match source file byte-for-byte.

- **T1-PK-05: Output Cache Path & Cleanup Readiness**
  - *Input*: Valid unpacked chapter directory.
  - *Action*: Pack via `CbzPacker.pack(destFile)`.
  - *Expected Output*: Destination file is created in application cache directory with permissions restricted to application UID.
  - *Assertion*: `destFile.exists() && destFile.length() > 0`.

---

### Feature 4: Pure Chapter State & Series Gap Derivations
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §6.3; `PROJECT.md` §3; `PLAN_GEMINI.md` §5.7.*

- **T1-ST-01: Derivation Hierarchy - Active Translation Priority**
  - *Input*: `ChapterFacts(hasDownloaded=true, hasLocalFr=false, indexOriginal=false, indexFr=false, latestJobState="SENT", latestJobPcState="translating", latestJobFailed=false)`.
  - *Action*: Evaluate `ChapterStatusDerivation.deriveStatus(facts)`.
  - *Expected Output*: `ChapterStatus.TRANSLATING`.
  - *Assertion*: Returns `TRANSLATING` regardless of `hasDownloaded` value.

- **T1-ST-02: Derivation Hierarchy - Local Translated Presence Priority**
  - *Input*: `ChapterFacts(hasDownloaded=true, hasLocalFr=true, indexOriginal=true, indexFr=true, latestJobState=null, latestJobPcState=null, latestJobFailed=false)`.
  - *Action*: Evaluate `ChapterStatusDerivation.deriveStatus(facts)`.
  - *Expected Output*: `ChapterStatus.UP_TO_DATE`.
  - *Assertion*: Returns `UP_TO_DATE` even if `indexOriginal` or `hasDownloaded` are true.

- **T1-ST-03: Derivation Hierarchy - Queued Translation State**
  - *Input*: `ChapterFacts(hasDownloaded=true, hasLocalFr=false, latestJobState="SENT", latestJobPcState="queued", latestJobFailed=false)`.
  - *Action*: Evaluate `ChapterStatusDerivation.deriveStatus(facts)`.
  - *Expected Output*: `ChapterStatus.QUEUED`.
  - *Assertion*: Correctly distinguishes `QUEUED` from `TRANSLATING` based on `pcState`.

- **T1-ST-04: Missing Chapters Integer Range Detection**
  - *Input*: Downloaded chapter numbers: `[1.0, 2.0, 4.0, 7.5, 8.0]`. Ignored gaps: `[]`.
  - *Action*: Calculate missing chapters via `MissingChapters.compute()`.
  - *Expected Output*: Missing integer gaps identified: `[3, 5, 6, 7]`. (Non-integer 7.5 does not satisfy integer 7).
  - *Assertion*: Result list contains exactly `[3, 5, 6, 7]`.

- **T1-ST-05: Filtered Missing Chapters via Ignored Gaps**
  - *Input*: Downloaded chapter numbers: `[1.0, 2.0, 4.0, 8.0]`. Ignored gaps in Room: `[number = 3]`.
  - *Action*: Calculate missing chapters.
  - *Expected Output*: Gap 3 is suppressed; returns `[5, 6, 7]`.
  - *Assertion*: 3 is absent from gap list; series summary badge updates accordingly.

---

### Feature 5: Google Drive REST Protocol & Resumable Transfer
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §7.1, §7.2; `PLAN_GEMINI.md` §5.10, §7.2.*

- **T1-DR-01: Root Mailbox Folder Layout Initialization**
  - *Input*: Authenticated `DriveTransport` on empty Drive storage.
  - *Action*: Invoke `DriveTransport.ensureLayout()`.
  - *Expected Output*: Creates root folder `TranslateMyManga/`, `jobs/`, `pc/`, `pc/commands/`, and writes `protocol.json` (`{"protocol": 1}`).
  - *Assertion*: Second invocation performs 0 folder creations (idempotent caching).

- **T1-DR-02: Multipart Metadata & JSON Upload**
  - *Input*: Valid `request.json` model serialized to bytes.
  - *Action*: Upload via `DriveApi.createFileMultipart()`.
  - *Expected Output*: HTTP request contains `Content-Type: multipart/related; boundary=...`. Remote file created with MIME type `application/json`.
  - *Assertion*: FakeDrive records file under designated parent directory ID with identical bytes.

- **T1-DR-03: Resumable Upload 8 MiB Chunk Sequence**
  - *Input*: 20 MB CBZ file (20,971,520 bytes).
  - *Action*: Upload via `DriveApi.uploadResumable(file)`.
  - *Expected Output*: Split into three chunks: Chunk 1 (8,388,608 bytes, HTTP 308), Chunk 2 (8,388,608 bytes, HTTP 308), Chunk 3 (4,194,304 bytes, HTTP 200).
  - *Assertion*: FakeDrive verifies total accumulated bytes equal 20,971,520 and SHA-256 matches local file.

- **T1-DR-04: Incremental Drive Changes Query (`modifiedTime`)**
  - *Input*: Cursor timestamp $T_0$. Remote files modified at $T_0 - 10\text{s}$, $T_0 + 5\text{s}$, $T_0 + 30\text{s}$.
  - *Action*: Query `DriveApi.pollChanges(cursor = T_0)`.
  - *Expected Output*: Request query string contains `modifiedTime > '...'`. Returns only the two files modified after $T_0$.
  - *Assertion*: Response list size is 2; cursor updates to latest `modifiedTime`.

- **T1-DR-05: Binary File Download Streaming**
  - *Input*: Remote `result.cbz` file ID on Drive (size 18 MB).
  - *Action*: Download via `DriveApi.downloadFile(fileId, destinationFile)`.
  - *Expected Output*: Streams byte chunks directly into destination file sink without buffering entire file in JVM memory.
  - *Assertion*: `destinationFile.length() == 18874368` and local SHA-256 matches remote metadata.

---

### Feature 6: Mailbox Protocol & Dual-Client Handshake
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §7.1 - §7.3; `PLAN_GEMINI.md` §5.5.*

- **T1-MB-01: End-of-Write Marker Sequence (Client Side)**
  - *Input*: Client uploading translation job for Chapter 214.
  - *Action*: Monitor `FakeDrive` file creation events during job upload.
  - *Expected Output*: `source.cbz` creation timestamp strictly precedes `request.json` creation timestamp.
  - *Assertion*: `fakeDrive.getFileCreationTime("request.json") > fakeDrive.getFileCreationTime("source.cbz")`.

- **T1-MB-02: End-of-Write Marker Sequence (Desktop Side)**
  - *Input*: `FakePc` finishing translation of job.
  - *Action*: Monitor `FakeDrive` file creation events during result publication.
  - *Expected Output*: `result.cbz` creation strictly precedes `result.json` creation.
  - *Assertion*: `fakeDrive.getFileCreationTime("result.json") > fakeDrive.getFileCreationTime("result.cbz")`.

- **T1-MB-03: PC Heartbeat & Online Status Derivation**
  - *Input*: `pc/heartbeat.json` written with `updatedAt` 2 minutes prior to `Clock.now()`.
  - *Action*: Execute `RefreshCoordinator.refresh()`.
  - *Expected Output*: App state marks PC as "En ligne" with `pcName = "PC-BUREAU"`, `slots = 4`.
  - *Assertion*: UI exposes green success status indicator with pulsing dot.

- **T1-MB-04: PC Index Parsing & Chapter Synchronization**
  - *Input*: `pc/index.json` containing 10 chapters with `{"original": true, "fr": true}`.
  - *Action*: Ingest through `RefreshCoordinator`.
  - *Expected Output*: 10 entries inserted into `pc_index_chapter` table.
  - *Assertion*: Querying `pc_index_chapter` returns 10 matching records.

- **T1-MB-05: Quality Report Deserialization & Storage**
  - *Input*: `result.json` containing `quality` block with 146 translated bubbles, 2 untranslated, 31 SFX, and 1 overflow alert on page 8.
  - *Action*: Process `result.json` during refresh.
  - *Expected Output*: Ingested into `chapter_quality` Room table with JSON-serialized alert list.
  - *Assertion*: `chapter_quality.translatedBubbles == 146` and `alerts[0].page == 8`.

---

### Feature 7: Background Job Workers & Local Installation
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §8.4; `PLAN_GEMINI.md` §5.12, §5.13, §6.6.*

- **T1-WK-01: `SendJobWorker` Complete Transmission Cycle**
  - *Input*: Job in Room with `phoneState = PREPARING`.
  - *Action*: Execute `SendJobWorker.doWork()`.
  - *Expected Output*: Packs CBZ, uploads to Drive, writes `request.json`, updates Room state to `SENT`, clears local cache file.
  - *Assertion*: `job.phoneState == PhoneState.SENT` and `job.cachePath == null`.

- **T1-WK-02: `InstallJobWorker` Atomic Local Installation**
  - *Input*: Job with `phoneState = RESULT_READY`, `resultFileId = "res123"`.
  - *Action*: Execute `InstallJobWorker.doWork()`.
  - *Expected Output*: Downloads `result.cbz` to temp, verifies SHA-256, atomically installs to `Mihon/local/Blue Lock/Chapter 214.cbz`, updates Room state to `INSTALLED`, schedules `OutboxWorker`.
  - *Assertion*: `job.phoneState == PhoneState.INSTALLED` and file exists at `installedPath`.

- **T1-WK-03: `OutboxWorker` Dispatches `received.json`**
  - *Input*: Job with `phoneState = INSTALLED` and `receivedWrittenAt = null`.
  - *Action*: Execute `OutboxWorker.doWork()`.
  - *Expected Output*: Writes `jobs/<jobId>/received.json` on Drive; updates `receivedWrittenAt` in Room.
  - *Assertion*: FakeDrive contains `received.json` with correct `installedPath`.

- **T1-WK-04: Single Job Isolation for Multi-Chapter Batch Creation**
  - *Input*: User triggers translation for 5 selected chapters from Library action bar.
  - *Action*: Call `JobRepository.createJobs(mode = translate, targets = 5 chapters)`.
  - *Expected Output*: Creates 5 separate `JobEntity` records in Room with 5 distinct `jobId` UUIDs. Zero batched job containers.
  - *Assertion*: `database.jobDao().getActiveJobs().size == 5`.

- **T1-WK-05: Job Cancellation Prior to PC Ingestion**
  - *Input*: Job in `phoneState = SENT`.
  - *Action*: Call `JobRepository.cancel(jobId)`.
  - *Expected Output*: Marks state `CANCEL_REQUESTED`, schedules `OutboxWorker` to write `cancel.json`.
  - *Assertion*: `cancel.json` uploaded to Drive; subsequent status update from PC transitions job to `CANCELLED`.

---

### Feature 8: Anti-Spam Refresh Coordination
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §11.3; `PLAN_GEMINI.md` §5.15.*

- **T1-RC-01: Single Concurrent Execution Lock (`Mutex.tryLock`)**
  - *Input*: Idle `RefreshCoordinator`.
  - *Action*: Launch two concurrent `refresh()` calls simultaneously via `async`.
  - *Expected Output*: First call acquires lock and executes; second call immediately returns `false` without executing network queries.
  - *Assertion*: Network request log records exactly 1 set of polling requests.

- **T1-RC-02: 30-Second Cooldown Enforcement**
  - *Input*: `refresh()` completes successfully at $T = 0$. Cooldown timer active.
  - *Action*: Invoke `refresh()` at $T = 10\text{s}$.
  - *Expected Output*: Call returns immediately without acquiring network resources.
  - *Assertion*: `RefreshState.cooldownSeconds == 20`; network request count remains 1.

- **T1-RC-03: Cooldown Expiration & Re-Arming**
  - *Input*: Cooldown active.
  - *Action*: Advance `TestClock` by 31 seconds. Invoke `refresh()`.
  - *Expected Output*: Cooldown state is 0; refresh executes successfully.
  - *Assertion*: Refresh completes; `lastCheckedAt` updates to current timestamp.

- **T1-RC-04: Foreign Job ID Isolation**
  - *Input*: Drive mailbox contains `jobs/foreign-uuid-999/status.json` created by a different mobile device.
  - *Action*: Execute `RefreshCoordinator.refresh()`.
  - *Expected Output*: Foreign job status is safely ignored. No entity created in local Room `job` table.
  - *Assertion*: `database.jobDao().getJob("foreign-uuid-999") == null`.

- **T1-RC-05: Unified Pull-to-Refresh & Button Entry Point**
  - *Input*: Compose UI with RefreshButton and PullToRefreshBox.
  - *Action*: Trigger swipe gesture and button click in sequence.
  - *Expected Output*: Both route through the identical `RefreshCoordinator.refresh()` suspend function.
  - *Assertion*: Lock prevents redundant execution.

---

### Feature 9: Remote PC Settings Synchronization (`SettingsSync`)
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §7.5; `PLAN_GEMINI.md` §5.18.*

- **T1-SS-01: Setting Modification Command Dispatch**
  - *Input*: Active `settings.json` has `version = 12`, `chapterConcurrency = 4`.
  - *Action*: User changes concurrency to 2 in UI.
  - *Expected Output*: Outbox creates `pc/commands/<cmdId>.json` containing `{"protocol": 1, "cmdId": "...", "baseVersion": 12, "changes": {"chapterConcurrency": 2}}`. Banner shows `SENDING`.
  - *Assertion*: File created on FakeDrive; banner state is `SENDING`.

- **T1-SS-02: Desktop Confirmation & Version Increment**
  - *Input*: Command written. `FakePc` processes command, increments version to 13, writes `settings.json` with `lastCommandId = cmdId`.
  - *Action*: `SettingsSync` polling loop reads updated `settings.json`.
  - *Expected Output*: Command removed from pending queue. Banner transitions to `APPLIED` ("Appliqué sur PC-BUREAU").
  - *Assertion*: `SettingsSync.state == SyncBanner.APPLIED`.

- **T1-SS-03: Hot Application Without Interruption Guarantee**
  - *Input*: Concurrency reduced from 4 to 2 while 3 jobs are actively translating.
  - *Action*: Verify running jobs.
  - *Expected Output*: Desktop daemon finishes the 3 active jobs; does not pull a 4th job until count drops below 2.
  - *Assertion*: All 3 initial jobs proceed to completion without abortion.

- **T1-SS-04: Closed Whitelist Key & Value Validation**
  - *Input*: Valid changes: `detector = "ctd"`, `inpainter = "lama_large"`, `useGpu = true`.
  - *Action*: Validate via `SettingsSchema.validate()`.
  - *Expected Output*: Validation returns true; changes permitted.
  - *Assertion*: No schema violation exception thrown.

- **T1-SS-05: Read-Only UI State When Settings File Absent**
  - *Input*: Fresh app state where `pc/settings.json` has never been downloaded.
  - *Action*: Open TranslationSettings screen.
  - *Expected Output*: All controls rendered disabled with alpha 0.45. Banner displays "Les réglages s'afficheront au premier signal du PC".
  - *Assertion*: UI components have `enabled = false`.

---

### Feature 10: Lifecycle, Onboarding & Error Banner States
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §9.2, §9.11; `PLAN_GEMINI.md` §5.19, §7.10.*

- **T1-LC-01: First-Run Onboarding Routing**
  - *Input*: Clean app install (`onboarding_done = false` in DataStore).
  - *Action*: Launch `MainActivity`.
  - *Expected Output*: App automatically directs user to `OnboardingRoute` (Step 1). Bottom navigation bar is hidden.
  - *Assertion*: Current navigation destination is `OnboardingRoute`.

- **T1-LC-02: 3-Step Onboarding Progression**
  - *Input*: User connects Google account (Step 1) $\to$ selects valid Mihon directory (Step 2) $\to$ imports initial catalog (Step 3).
  - *Action*: Complete onboarding flow and tap "Ouvrir TMM Mobile".
  - *Expected Output*: DataStore sets `onboarding_done = true`. Navigates to `HomeRoute`. Bottom navigation bar becomes visible.
  - *Assertion*: Destination is `HomeRoute`; back stack is cleared.

- **T1-LC-03: Transient Network Disconnect Detection**
  - *Input*: App running. Device loses network connectivity (`NetworkCapabilities.hasCapability` lost).
  - *Action*: Observer emits offline state.
  - *Expected Output*: Top `OfflineBanner` displays "Pas de connexion internet" (`NoNetwork` variant). Bottom tabs remain responsive.
  - *Assertion*: `OfflineBanner` visible with role `status`.

- **T1-LC-04: Empty State Rendering on Blank Catalog**
  - *Input*: Onboarding completed, but 0 downloaded chapters exist in Mihon and 0 backups imported.
  - *Action*: Navigate to Home screen.
  - *Expected Output*: Renders `Empty` screen layout with halftone illustration, "Scanner Mihon" button, and backup instructions.
  - *Assertion*: Empty view composables are rendered; transfer cards are hidden.

- **T1-LC-05: Application Startup Sequence Execution**
  - *Input*: App process started from cold state with valid onboarding and permissions.
  - *Action*: Execute `Startup.run()`.
  - *Expected Output*: Checks SAF permissions $\to$ checks backup freshness $\to$ scans downloads $\to$ runs auto-translate $\to$ executes `refresh()`.
  - *Assertion*: All 5 stages complete in order; home dashboard updates with fresh timestamps.

---

### Feature 11: Multi-Selection & Batch Translation
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §9.4, §9.12; `PLAN_GEMINI.md` §9.4.*

- **T1-MS-01: Long-Press Gesture Activation (Library Screen)**
  - *Input*: User performs long click on series tile in Library grid.
  - *Action*: Dispatch long-click gesture event.
  - *Expected Output*: Enters multi-selection mode. Tile is checked with accent ring and 96% scale. Action bar 2x2 appears with aggregate counters. Haptic feedback triggered.
  - *Assertion*: `LibraryState.isSelectionMode == true` and `selectedSeriesKeys.size == 1`.

- **T1-MS-02: Aggregate Action Bar Counter Math**
  - *Input*: Series 1 has 3 chapters `TO_SEND` and 2 `TO_FETCH`. Series 2 has 4 chapters `TO_SEND` and 1 `TO_FETCH`. Both selected.
  - *Action*: Inspect action bar buttons.
  - *Expected Output*: "Envoyer au PC" button shows count 7. "Récupérer" button shows count 3.
  - *Assertion*: Action button labels display exact sum of chapters across all selected series.

- **T1-MS-03: Selection Persistence Across Search & Category Filtering**
  - *Input*: 2 series selected in Category "Tous". User changes filter chip to "En cours", causing 1 selected series to be hidden from the grid.
  - *Action*: Change filter.
  - *Expected Output*: Hidden series remains selected in `SavedStateHandle`. Action bar continues to count chapters from both series.
  - *Assertion*: `selectedSeriesKeys.size == 2`; action bar sums reflect both series.

- **T1-MS-04: Automatic Exit on Deselecting Last Item (Gesture Mode)**
  - *Input*: Selection mode entered via long-press. Exactly 1 series currently checked.
  - *Action*: User taps the checked series to uncheck it.
  - *Expected Output*: Selection becomes empty; multi-selection mode automatically terminates; action bar disappears.
  - *Assertion*: `isSelectionMode == false` and `selectedSeriesKeys.isEmpty()`.

- **T1-MS-05: Accessible Button Multi-Selection Mode Behavior**
  - *Input*: User enters selection mode via explicit "Sélectionner" button.
  - *Action*: User checks and unchecks a series.
  - *Expected Output*: Unchecking the last series leaves the app IN selection mode (0 selected). User must explicitly tap "Terminer" or Back to exit.
  - *Assertion*: `isSelectionMode == true` while `selectedSeriesKeys.isEmpty()`.

---

## 5. Tier 2: Boundary, Corner & Stress Cases

Tier 2 exercises extreme limits, error conditions, malicious/corrupted data, network instability, and resource stress. **Requirement: Minimum 5 distinct test cases per feature area.**

### Boundary 1: Protobuf & Malformed Backup Extremes
- **T2-BK-01: Zero-Byte / Truncated Gzip File Ingestion**
  - *Input*: `.tachibk` file truncated to 0 bytes or cut off mid-gzip block.
  - *Expected Output*: Ingestion catches `EOFException` / `ZipException`. Displays non-crashing error notification; existing Room database remains intact.
  - *Assertion*: Database rollback verified; 0 records corrupted.

- **T2-BK-02: Massive Catalog Stress (1,000 Series, 50,000 Chapters)**
  - *Input*: Synthesized valid `.tachibk` containing 1,000 manga titles and 50,000 chapters.
  - *Expected Output*: Ingestion completes within 5 seconds in a single batch transaction without SQLite statement limit (999 variables) overflow or OutOfMemoryError.
  - *Assertion*: `series` row count is 1,000; `chapter` row count is 50,000.

- **T2-BK-03: Corrupted Varint Field in Protobuf Stream**
  - *Input*: Protobuf stream with broken varint terminating bit (10 consecutive bytes with MSB=1).
  - *Expected Output*: Parser catches protobuf deserialization error without crashing JVM; logs diagnostic with PII redaction.
  - *Assertion*: `backup_import` record is not updated.

- **T2-BK-04: Unicode & Emoji Stress in Series / Author Titles**
  - *Input*: Manga titles with right-to-left text, zero-width joiners, complex Japanese Kanji, and emojis (`🔥 Manga <script>alert(1)</script> 訳`).
  - *Expected Output*: Ingested cleanly into SQLite; displayed verbatim in UI; no SQL injection or unescaped crashes.
  - *Assertion*: Database text equals input string byte-for-byte.

- **T2-BK-05: Negative / Extreme Chapter Float Numbers**
  - *Input*: Chapter numbers: `-1.0`, `0.0`, `9999.99`, `NaN`, `Infinity`.
  - *Expected Output*: Sanitizer coerces or rejects non-finite numbers; negative and large numbers sort deterministically without indexing errors.
  - *Assertion*: Database contains sanitized finite floating point values.

---

### Boundary 2: Filesystem & SAF Edge Cases
- **T2-SC-01: Storage Permission Revocation Mid-Execution**
  - *Input*: Background scan running while user revokes SAF permission in Android OS Settings.
  - *Expected Output*: Catch `SecurityException`. Aborts scan cleanly without crashing. Next foreground launch redirects to `PermissionScreen`.
  - *Assertion*: App transitions to `PermissionRoute`.

- **T2-SC-02: Deeply Nested & Symlinked Directory Cycles**
  - *Input*: Malicious or corrupted filesystem structure with 20 recursive directory levels.
  - *Expected Output*: Traversal adheres to maximum depth limit `depth <= 4` (`downloads/<source>/<series>/<chapter>`); terminates safely.
  - *Assertion*: StackOverflowError is avoided; scan completes.

- **T2-SC-03: Directory Containing Active Download `.tmp` Marker**
  - *Input*: Folder `downloads/.../Chapter 50/` contains `01.jpg` and `download.tmp`.
  - *Expected Output*: Scanner identifies folder as incomplete; skips folder entirely from `downloaded_chapter`.
  - *Assertion*: Chapter 50 does not appear in "À envoyer au PC".

- **T2-SC-04: Zero-Image Empty Chapter Directory**
  - *Input*: Validly named chapter folder containing 0 files.
  - *Expected Output*: Scanner skips directory or registers `pageCount = 0`; job creation rejects chapter with zero pages.
  - *Assertion*: No upload triggered for 0-page chapter.

- **T2-SC-05: Forbidden Path Characters in Mihon Folder Names**
  - *Input*: Folder names with Windows/Unix reserved characters (`AUX`, `COM1`, `CON`, `NUL`, `:`, `*`, `?`, `"`, `<`, `>`, `|`).
  - *Expected Output*: Sanitizer normalizes names matching Mihon's exact replacement logic.
  - *Assertion*: Sanitized name matches catalog title cleanly.

---

### Boundary 3: CBZ Packaging Boundaries
- **T2-PK-01: Massive Chapter Size Stress (200 MB, 150 Pages)**
  - *Input*: Chapter folder with 150 high-res scans totaling 200 MB.
  - *Expected Output*: Packages into STORED CBZ streaming memory; heap usage remains $< 32\text{ MB}$.
  - *Assertion*: Resulting CBZ is readable; CRC matches.

- **T2-PK-02: Hidden OS Files Filtering (`.DS_Store`, `Thumbs.db`)**
  - *Input*: Chapter directory containing 20 JPEGs plus `.DS_Store`, `Thumbs.db`, `.nomedia`.
  - *Expected Output*: Packer strictly ignores files beginning with `.` and system metadata files.
  - *Assertion*: ZIP contains exactly 20 image entries; `pageCount == 20`.

- **T2-PK-03: Disk Full Mid-Packaging (`IOException: ENOSPC`)**
  - *Input*: Simulated filesystem running out of disk space during CBZ generation.
  - *Expected Output*: Catch `IOException`. Deletes partially written CBZ in cache. Job marks `FAILED` at step `prepare`.
  - *Assertion*: Partial file does not linger in cache; UI displays clear disk space error.

- **T2-PK-04: Non-Ascii & Japanese Image Filenames Inside ZIP**
  - *Input*: Page images named: `01_表紙.png`, `02_本文.jpg`, `03_奥付.webp`.
  - *Expected Output*: ZIP encodes entry names with UTF-8 flags set (`EFS` bit). Names preserved on extraction.
  - *Assertion*: CRC-32 and filenames verify cleanly.

- **T2-PK-05: Single Page Chapter Edge Case**
  - *Input*: Chapter containing exactly 1 image (`cover.jpg`).
  - *Expected Output*: Packages valid CBZ with `pageCount = 1`.
  - *Assertion*: Correctly processed through packaging without boundary underflow.

---

### Boundary 4: State Derivation Conflicting Flags
- **T2-ST-01: Conflicting Job States (Local FAILED vs PC Done)**
  - *Input*: Local job failed on download (`phoneState = FAILED`), but PC `status.json` reported `done`.
  - *Expected Output*: Local failure takes precedence; status derives as `FAILED`.
  - *Assertion*: `deriveStatus()` returns `FAILED`.

- **T2-ST-02: Chapter Present in Local FR and Catalog Only (No Download)**
  - *Input*: `hasLocalFr = true`, `hasDownloaded = false`, `latestJobState = null`.
  - *Expected Output*: Derives as `UP_TO_DATE`.
  - *Assertion*: Returns `UP_TO_DATE`.

- **T2-ST-03: Gap Detection with Single Chapter Series**
  - *Input*: Series with only Chapter 1 downloaded.
  - *Expected Output*: Zero gaps detected (requires at least 2 distinct chapters to establish a range).
  - *Assertion*: `MissingChapters.compute()` returns empty list.

- **T2-ST-04: Gap Detection with Floating Point Sub-Chapters Only**
  - *Input*: Downloaded chapters: `[10.1, 10.2, 10.3]`.
  - *Expected Output*: No integer missing gaps reported.
  - *Assertion*: Returns empty list.

- **T2-ST-05: Cancelled Job State Invalidation**
  - *Input*: Job with `phoneState = CANCELLED`.
  - *Expected Output*: Cancelled job is ignored in active state evaluation; status falls back to `TO_SEND` if downloaded.
  - *Assertion*: Returns `TO_SEND`.

---

### Boundary 5: Network Disconnects & Resumable Recovery
- **T2-DR-01: Mid-Upload Disconnect at 40% with Range Header Recovery**
  - *Input*: 20 MB upload cut off after 8 MB transmitted.
  - *Expected Output*: Reconnection queries upload session via `PUT` with empty body and `Content-Range: bytes */20971520`. Receives HTTP 308 with `Range: bytes=0-8388607`. Resumes upload at byte 8,388,608.
  - *Assertion*: Full upload succeeds; remote file byte count is exactly 20,971,520.

- **T2-DR-02: Expired Resumable Upload Session URL (HTTP 404/410)**
  - *Input*: Interrupted upload resumes after upload session expired on Google Drive servers.
  - *Expected Output*: `DriveApi` catches HTTP 404; discards stale session URL; initiates brand new upload session from byte 0.
  - *Assertion*: Upload restarts cleanly and completes.

- **T2-DR-03: Download Stream Interruption & Corrupted Checksum Rejection**
  - *Input*: `result.cbz` download terminates prematurely. Downloaded file has 10 MB instead of 15 MB.
  - *Expected Output*: SHA-256 verification fails. File discarded. Worker retries download once. If failure persists, marks job `FAILED` at step `download`.
  - *Assertion*: Corrupted file never renamed into `Mihon/local/`.

- **T2-DR-04: Drive Server 503 Outage with Exponential Backoff**
  - *Input*: FakeDrive returns HTTP 503 for 3 consecutive calls, then returns HTTP 200.
  - *Expected Output*: Client applies exponential backoff (1s, 2s, 4s); retries succeed without failing the background worker.
  - *Assertion*: Request succeeds on 4th attempt; total retry count is 3.

- **T2-DR-05: Rate Limiting HTTP 429 with `Retry-After` Header**
  - *Input*: FakeDrive returns HTTP 429 with `Retry-After: 3`.
  - *Expected Output*: Client delays execution by at least 3 seconds before issuing subsequent request.
  - *Assertion*: Backoff duration strictly respects server-provided header.

---

### Boundary 6: Rate Limiting & Auth Token Revocation
- **T2-AU-01: 401 Unauthorized During Active Upload Session**
  - *Input*: Upload request receives HTTP 401 Unauthorized mid-transfer.
  - *Expected Output*: Access token invalidated; refreshed via GoogleAuth; request retried with new Bearer token.
  - *Assertion*: Transfer completes successfully with renewed token.

- **T2-AU-02: Permanent Token Invalidation (User Changed Google Password)**
  - *Input*: Two consecutive requests return HTTP 401 after token renewal attempt.
  - *Expected Output*: DataStore sets `auth_expired = true`. Background workers pause gracefully. UI displays persistent alert banner with "Reconnecter" button.
  - *Assertion*: `OfflineBanner` displays `TokenExpired` variant.

- **T2-AU-03: Protocol Version Mismatch (`protocol: 2`)**
  - *Input*: Desktop PC writes `protocol.json` containing `{"protocol": 2}`.
  - *Expected Output*: Phone detects unsupported protocol version ($> 1$). Sets `protocol_too_new = true`. Halts processing of Drive files. Displays top banner "Le PC utilise une version plus récente du protocole. Mettez à jour l'app."
  - *Assertion*: No unknown protocol files parsed; UI banner active.

- **T2-AU-04: Malformed JSON in Protocol File**
  - *Input*: Drive contains `status.json` with unparseable syntax (`{ "protocol": 1, "state": ... [unclosed`).
  - *Expected Output*: Deserializer catches syntax error; logs warning; skips corrupted file without crashing refresh loop.
  - *Assertion*: Refresh loop continues processing remaining files.

- **T2-AU-05: Wi-Fi Only Constraint Violation**
  - *Input*: Preference `wifi_only = true`. Network changes to Mobile Cellular Data.
  - *Expected Output*: WorkManager constraints halt `SendJobWorker` and `InstallJobWorker`. Active transfers paused.
  - *Assertion*: Zero cellular bytes transmitted.

---

### Boundary 7: Refresh Spam & Cooldown Race Conditions
- **T2-RC-01: 10 Rapid Clicks on Refresh Button in 500 ms**
  - *Input*: User mashes the "Rafraîchir" button 10 times in half a second.
  - *Expected Output*: Exactly 1 refresh cycle executes. UI shows spin animation, then immediately enters 30s countdown. Zero thread deadlock.
  - *Assertion*: Network request count is exactly 1.

- **T2-RC-02: Refresh Triggered Exactly at $T = 30.001\text{s}$ (Boundary Millisecond)**
  - *Input*: Refresh triggered at the exact boundary of cooldown expiration.
  - *Expected Output*: Lock acquired cleanly; second refresh proceeds without race condition or rejection.
  - *Assertion*: Second refresh succeeds.

- **T2-RC-03: Process Death During Cooldown Countdown**
  - *Input*: Cooldown active with 15s remaining. Android kills and restarts application process.
  - *Expected Output*: `Startup` reads `last_refresh_at` from DataStore. Recomputes remaining cooldown based on wall clock time elapsed.
  - *Assertion*: Remaining cooldown is $\max(0, 30 - \text{elapsedSeconds})$.

- **T2-RC-04: Remote Clock Drift Resilience**
  - *Input*: PC heartbeat contains timestamp 10 minutes in the future (PC clock desynchronized).
  - *Expected Output*: Phone handles future timestamp gracefully; does not calculate negative relative time strings; marks PC online.
  - *Assertion*: UI formats timestamp safely without throwing `IllegalArgumentException`.

- **T2-RC-05: Database Lock Contention during Refresh**
  - *Input*: Background scan writing large catalog batch while `RefreshCoordinator` attempts status write.
  - *Expected Output*: SQLite transaction waits for lock with timeout; completes without `SQLiteDatabaseLockedException`.
  - *Assertion*: Both transactions commit cleanly.

---

### Boundary 8: Installation Collisions & Filesystem Faults
- **T2-IN-01: Pre-Existing Translated Chapter Overwrite**
  - *Input*: `Mihon/local/Blue Lock/Chapter 214.cbz` already exists from earlier translation. Mode is `retranslate`.
  - *Expected Output*: New file staged to hidden `.tmp` file $\to$ verified $\to$ existing file deleted $\to$ `.tmp` renamed.
  - *Assertion*: New version replaces old version; zero orphaned `.tmp` files.

- **T2-IN-02: Mihon Local Directory Creation Failure**
  - *Input*: `Mihon/local/` root folder has write permission revoked.
  - *Expected Output*: `LocalInstaller` catches error; marks job `FAILED` at step `install`; deletes downloaded cache file.
  - *Assertion*: Room records failure code `install_error`.

- **T2-IN-03: Verification Job Producing Zero File Changes (`file: null`)**
  - *Input*: PC finishes `verify` job without modifying any pages; writes `result.json` with `"file": null`.
  - *Expected Output*: Phone updates `chapter_quality` in Room; skips CBZ download; transitions job directly to `INSTALLED`.
  - *Assertion*: `job.phoneState == PhoneState.INSTALLED` without issuing download request.

- **T2-IN-04: File Provider Appending Numeric Suffix on Rename**
  - *Input*: `DocumentsContract.renameDocument` appends ` (1)` because destination name is locked.
  - *Expected Output*: Installer detects renamed document does not match requested filename; deletes file; fails explicitly.
  - *Assertion*: Rejects corruptly renamed file.

- **T2-IN-05: Concurrent Read Access by Mihon Reader During Install**
  - *Input*: User actively reading old chapter in Mihon while TMM installs replacement.
  - *Expected Output*: Staging to hidden temp file prevents Mihon from reading partially written stream.
  - *Assertion*: Mihon reader experiences no file corruption.

---

### Boundary 9: Remote Settings Boundaries & Illegal Inputs
- **T2-SS-01: Disallowed Settings Key Injection**
  - *Input*: Command attempts to set `"apiKey": "secret"` or `"outputDir": "C:/"`.
  - *Expected Output*: Client schema validator rejects modification locally. Desktop daemon also drops unknown keys if received.
  - *Assertion*: Command is blocked; never written to Drive.

- **T2-SS-02: Out-of-Bounds Concurrency Values**
  - *Input*: Command attempts to set `chapterConcurrency = 0` or `concurrency = 5`.
  - *Expected Output*: Rejected by `SettingsSchema`. Allowed values are strictly integers 1 to 4.
  - *Assertion*: Local validation fails; error returned to UI.

- **T2-SS-03: 60-Second Confirmation Timeout Transition to `WAITING_PC`**
  - *Input*: Setting command dispatched to offline PC. Polling loop runs for 60 seconds with no confirmation.
  - *Expected Output*: After 60s, banner transitions from `SENDING` to `WAITING_PC` ("En attente du PC" / "Sera appliqué au prochain démarrage du PC").
  - *Assertion*: `SettingsSync.state == SyncBanner.WAITING_PC`.

- **T2-SS-04: Conflicting Rapid Setting Adjustments (Command Overwrite)**
  - *Input*: User changes concurrency to 2, then immediately changes to 3 before PC confirms.
  - *Expected Output*: Second command supersedes first; outbox sends latest command with updated `baseVersion`.
  - *Assertion*: PC applies final command; settings converge on 3.

- **T2-SS-05: Non-Ascii Font Selection**
  - *Input*: Font selection changed to allowed values: `"Anime Ace"` or `"Manga Temple"`.
  - *Expected Output*: Properly serialized into JSON without encoding corruption.
  - *Assertion*: Verified on FakeDrive.

---

### Boundary 10: Multi-Selection Gesture & Filter Traversal
- **T2-MS-01: Rapid Double-Tap Long Press Conflict**
  - *Input*: User rapidly taps twice then holds on a series card.
  - *Expected Output*: System resolves to long-press selection; does not open Series screen.
  - *Assertion*: Screen remains Library; selection mode active.

- **T2-MS-02: "Tout cocher" with Active Filter Yielding Zero Results**
  - *Input*: Search query filters library down to 0 visible series. User taps "Tout cocher".
  - *Expected Output*: Selects 0 series; action bar disabled.
  - *Assertion*: `selectedSeriesKeys.isEmpty()`.

- **T2-MS-03: Rotation During Active Selection with Action Dialog Open**
  - *Input*: 3 series selected; "Retraduire" confirmation dialog displayed. Device rotates orientation.
  - *Expected Output*: Selection state and open dialog survive rotation via `SavedStateHandle`.
  - *Assertion*: Dialog remains open; selected items preserved.

- **T2-MS-04: System Back Button Handling with Selection Active**
  - *Input*: 2 series selected in Library. User presses Android system back button.
  - *Expected Output*: First press clears selection and exits selection mode. Second press navigates back to Home.
  - *Assertion*: Back press does not exit application while selection is active.

- **T2-MS-05: TalkBack Accessibility Semantics for Multi-Selection**
  - *Input*: Accessibility service enabled (TalkBack).
  - *Action*: Inspect semantics node of series card.
  - *Expected Output*: Node exposes role `Checkbox`, checked state `true/false`, and custom action "Sélectionner" on long press.
  - *Assertion*: Accessibility node contains expected actions and descriptions.

---

## 6. Tier 3: Cross-Feature Interactions

Tier 3 tests verify that concurrent, asynchronous, or sequential combinations of features do not produce race conditions, data corruption, or deadlock.

### 6.1 Pairwise Cross-Feature Interaction Matrix

| Feature Pair | Interaction Point | Risk / Failure Mode | Verification Contract |
|---|---|---|---|
| **C1: Scan + Upload** | `DownloadScanner` runs while `SendJobWorker` is actively packaging/uploading. | Scanner reads locked or incomplete CBZ; worker reads file being re-scanned. | Independent filesystem locks; scanner skips cache path; upload unaffected. |
| **C2: Settings Sync + Active Jobs** | PC settings updated while 4 translation jobs are actively processing. | Jobs in flight aborted or corrupted by parameter changes. | PC finishes in-flight pages; applies changes to next pages/jobs hot. |
| **C3: Auto-Translate + Gaps** | Startup detects newly downloaded chapters for auto-translate series with gaps. | Auto-translate sends skipped/gap chapters or crashes on missing range. | Auto-translate strictly filters `TO_SEND`; gap warnings remain isolated. |
| **C4: Retention + Cancel** | Drive retention "After Receive" configured while a job is cancelled. | Outbox worker attempts to delete non-existent or wrong folder. | State machine guards deletion; folder deleted cleanly without 404 crash. |
| **C5: SAF Revocation + Install** | SAF permission revoked while `InstallJobWorker` is streaming `result.cbz`. | Partial file left in local storage; app enters unhandled crash loop. | Worker catches SecurityException; temp file deleted; redirects to Permission. |
| **C6: Protocol Upgrade + Refresh** | PC upgrades protocol to v2 while phone is actively polling Drive. | Phone parses incompatible JSON; corrupts Room database. | Phone halts on `protocol > 1`; suppresses database updates; shows banner. |
| **C7: Retry Failed + Existing Original** | User taps "Réessayer à partir de la page N" when original exists on PC. | Phone re-uploads heavy CBZ over cellular data unnecessarily. | Mode automatically switches from `translate` to `retranslate` without upload. |
| **C8: Navigation + Multi-Select** | User switches bottom tabs (Library $\to$ Queue $\to$ Library) during selection. | Selection state leaked or discarded unexpectedly. | Leaving screen clears selection cleanly per spec; re-entering starts fresh. |

---

### 6.2 Detailed Interaction Test Cases

#### Test C1: Incremental Scan During Active Resumable Upload
- **Preconditions**: Manga chapter `Blue Lock 214` is currently being uploaded by `SendJobWorker` (chunk 2 of 3 in progress).
- **Execution Steps**:
  1. Trigger manual "Scanner Mihon" from UI.
  2. `DownloadScanner` traverses `downloads/` directory.
- **Authoritative Expected Output**:
  - Scanner reads source image folder without interfering with reading stream.
  - Scanner ignores the temporary CBZ in the app's internal cache folder.
  - Upload continues to completion; database records both operations without deadlock.
- **Assertion**: `job.phoneState == PhoneState.SENT` and `downloaded_chapter` row is valid.

#### Test C2: Hot Settings Synchronization During Heavy 4-Job Concurrency
- **Preconditions**: Desktop `FakePc` has 4 active translation slots running (`concurrency = 4`).
- **Execution Steps**:
  1. User changes `chapterConcurrency` from 4 to 2 in TranslationSettings screen.
  2. Command dispatched via Drive `pc/commands/<cmdId>.json`.
  3. `FakePc` processes command, writes updated `settings.json`, and completes active jobs.
- **Authoritative Expected Output**:
  - Phone banner transitions from `SENDING` to `APPLIED`.
  - PC does not abort the 4 active jobs; lets them finish, but throttles subsequent queue pickup to 2.
- **Assertion**: All 4 initial jobs complete with valid `result.cbz`; `settings.version` incremented.

#### Test C3: Auto-Translate Trigger with Chapter Gap & Ignored List
- **Preconditions**: Series "Chainsaw Man" has `autoTranslate = true`. Downloaded: Chapters 1, 2, 4, 5. Chapter 3 is missing (gap). Chapter 4 is ignored in `ignored_gap`.
- **Execution Steps**:
  1. Trigger app `Startup` sequence.
- **Authoritative Expected Output**:
  - Auto-translate creates translation jobs ONLY for chapters that are `TO_SEND` (1, 2, 4, 5).
  - Missing chapter 3 alert is displayed on Series screen.
  - Ignored chapter 4 does not trigger gap warning.
- **Assertion**: Exactly 4 jobs created in Room with `mode = translate`.

#### Test C4: Drive Space Retention ("After Receive") with Job Cancellation
- **Preconditions**: Drive retention preference set to `after_receive`. Job 1 installed; Job 2 cancelled in `SENT` state.
- **Execution Steps**:
  1. `OutboxWorker` executes maintenance pass.
- **Authoritative Expected Output**:
  - Job 1 folder on Drive (`jobs/<job1Id>/`) is deleted via `DELETE` call after `received.json` confirmed.
  - Job 2 folder is deleted immediately upon processing `cancel.json`.
  - Zero 404 errors logged.
- **Assertion**: FakeDrive verifies both job directories are purged from storage.

#### Test C5: SAF Revocation During Multi-Chapter Download Installation
- **Preconditions**: 3 jobs in `RESULT_READY`. `InstallJobWorker` is installing Chapter 1.
- **Execution Steps**:
  1. Revoke SAF URI persistable permission in mock environment before Chapter 2 installs.
- **Authoritative Expected Output**:
  - Chapter 1 finishes installation cleanly.
  - Chapter 2 installation fails with `SecurityException`; temporary staging file is deleted immediately.
  - App catches exception; marks Job 2 for retry; displays `PermissionScreen` on next screen focus.
- **Assertion**: Zero corrupted `.tmp` files in `Mihon/local/`; navigation redirects to `PermissionRoute`.

#### Test C6: Desktop Version Upgrade (`protocol: 2`) During Refresh Polling
- **Preconditions**: Phone operating normally on protocol v1.
- **Execution Steps**:
  1. `FakePc` writes `protocol.json` with `{"protocol": 2}`.
  2. User pulls to refresh on Home screen.
- **Authoritative Expected Output**:
  - `RefreshCoordinator` reads protocol 2.
  - Halts all further mailbox parsing for jobs, heartbeat, and settings.
  - Sets `protocol_too_new = true` in DataStore.
  - UI displays persistent top banner urging app update.
- **Assertion**: Room database is untouched; banner displays exact text from `PLAN_GEMINI.md` §4.9.

#### Test C7: Rapid Retry from Failed Page with Existing Original on PC
- **Preconditions**: Translation for Chapter 10 failed on page 8. Desktop `pc/index.json` confirms `original: true`.
- **Execution Steps**:
  1. User navigates to Queue $\to$ Échecs tab.
  2. Taps "Réessayer à partir de la page 8".
- **Authoritative Expected Output**:
  - New job created.
  - Because `index.original == true`, `JobRepository` automatically sets `mode = retranslate` and `file = null`.
  - Zero bytes uploaded for `source.cbz`.
  - `request.json` contains `"mode": "retranslate"`.
- **Assertion**: Job dispatches without upload step; enters queue directly on desktop.

#### Test C8: Category Filter Navigation with Retained Multi-Selection State
- **Preconditions**: User selects Series A (Category "Action") and Series B (Category "Comédie") in Library.
- **Execution Steps**:
  1. Tap filter chip "Action" (Series B is hidden from view).
  2. Tap "Envoyer au PC" action bar button.
- **Authoritative Expected Output**:
  - Action bar correctly includes chapters from BOTH Series A and Series B.
  - After action completes, selection mode terminates cleanly.
  - Toast displays total chapters queued across both series.
- **Assertion**: Both series have translation jobs created in Room.

---

## 7. Tier 4: Real-World Workload Scenarios

Tier 4 tests execute complete, unmocked, end-to-end user workflows from start to finish, validating complex asynchronous state machines across the entire application stack. **Requirement: Minimum 5 complete end-to-end application workflows.**

---

### Scenario 1: Fresh User Onboarding to First Translation Read
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §15.1 - §15.7; `ORIGINAL_REQUEST.md` R1-R5.*

```
[Clean Install] ──► [Step 1: Google OAuth] ──► [Step 2: Mihon SAF Check] ──► [Step 3: Catalog Import]
                                                                                        │
[Mihon/local/ Read] ◄── [Atomic Install] ◄── [FakePc Pipeline] ◄── [Drive Upload] ◄─────┘
```

- **Objective**: Verify that a first-time user can install the app, authenticate, grant SAF storage permission, import their Mihon library, dispatch their first chapter to an active PC, and receive a translated CBZ ready to read.
- **Step-by-Step Flow**:
  1. Launch app with empty DataStore. Verify redirect to `OnboardingRoute`.
  2. Trigger Google Sign-In with `GoogleAuth`. Verify `ensureLayout()` creates Drive root structure.
  3. Select Mihon storage tree containing `downloads/`, `local/`, and `autobackup/`. Verify 3 green animated checkmarks.
  4. Ingest sample `.tachibk` backup containing "Blue Lock". Verify series and categories imported.
  5. Tap "Ouvrir TMM Mobile". Verify navigation to `HomeRoute`.
  6. Place 20 JPEG images in `downloads/MangaDex (EN)/Blue Lock/Chapter 214/`.
  7. Tap "Scanner Mihon". Verify "Blue Lock · Ch. 214" appears with badge "À envoyer au PC".
  8. Tap "Envoyer". Verify `SendJobWorker` packs STORED CBZ and uploads to Drive with `request.json` written LAST.
  9. `FakePc` detects request, simulates 5 stages (`fetch`, `ocr`, `translate`, `lettering`, `verify`), writes `result.cbz` and `result.json`.
  10. Tap "Rafraîchir" (or wait for coordinator). Verify job transitions to `RESULT_READY`.
  11. `InstallJobWorker` downloads result, validates SHA-256, and atomically installs into `Mihon/local/Blue Lock/Chapter 214.cbz`.
  12. `OutboxWorker` writes `received.json` on Drive.
  13. Open Fiche série. Verify Chapter 214 displays badge "À jour partout" and quality statistics (bubbles, SFX).
- **Verification Assertions**:
  - File exists at `Mihon/local/Blue Lock/Chapter 214.cbz` with exact size matching `result.json`.
  - `database.jobDao().getJob(jobId).phoneState == PhoneState.INSTALLED`.
  - Drive folder contains valid `received.json`.

---

### Scenario 2: Batch Translation of Multi-Volume Manga with Offline Desktop PC
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §15.3, §15.4; `PLAN_GEMINI.md` §7.1.*

- **Objective**: Verify that a user can queue multiple chapters while the desktop PC is completely powered off, and that all jobs are preserved, uploaded, and automatically processed once the PC boots up.
- **Step-by-Step Flow**:
  1. Emulate PC offline (no heartbeat written for $> 24$ hours). Home screen displays red indicator "PC hors ligne depuis 2 jours".
  2. User opens Library screen. Long-presses "Dandadan" to enter multi-selection mode.
  3. Checks Chapters 101, 102, 103, 104, 105 (5 chapters).
  4. Taps "Envoyer au PC (5)".
  5. Verify 5 distinct `JobEntity` records created in Room with `phoneState = PREPARING`.
  6. Background workers pack and upload all 5 chapters to `TranslateMyManga/jobs/<id>/` on Drive.
  7. All 5 jobs transition to `phoneState = SENT`. Phone enters idle state.
  8. Simulate PC bootup: `FakePc` comes online, writes `pc/heartbeat.json`, scans Drive, and detects all 5 requests.
  9. `FakePc` processes up to 4 chapters in parallel (`slots = 4`). Updates `status.json` with progress.
  10. Phone executes `RefreshCoordinator.refresh()`. Home dashboard updates: PC shows "En ligne", active slots show 4 jobs translating and 1 queued.
  11. `FakePc` finishes all 5 jobs. Writes results.
  12. Phone triggers batch fetch. All 5 chapters installed into `Mihon/local/Dandadan/`.
- **Verification Assertions**:
  - All 5 CBZ files present in `Mihon/local/Dandadan/`.
  - Zero jobs lost, stalled, or duplicated during the offline-to-online transition.

---

### Scenario 3: Real-Time Engine Reconfiguration During Heavy Concurrency
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §7.5, §15.16, §15.17.*

- **Objective**: Verify remote tuning of translation provider and concurrency limits from the phone while the PC is actively translating chapters without dropping or restarting jobs.
- **Step-by-Step Flow**:
  1. `FakePc` is translating 4 chapters in parallel using provider `"gemini"`.
  2. User navigates to Settings $\to$ "Traduction sur le PC".
  3. User modifies engine from `"gemini"` to `"claude"`, and adjusts `chapterConcurrency` from 4 to 2.
  4. Changes dispatched via `pc/commands/<cmdId>.json`. UI displays `SENDING` banner.
  5. `FakePc` ingests command, updates internal settings, increments version, and writes `pc/settings.json`.
  6. Phone polling loop detects confirmation. Banner flashes `APPLIED` ("Appliqué sur PC-BUREAU").
  7. Verify in-flight chapters continue on Gemini without crashing; next queued chapters start with Claude.
  8. Verify PC limits subsequent parallel translation to 2 slots.
- **Verification Assertions**:
  - `settings.values.translationProvider == "claude"`.
  - `settings.values.chapterConcurrency == 2`.
  - Zero jobs failed or restarted from page 1.

---

### Scenario 4: Storage Permission Revocation & Self-Healing Recovery
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §9.11, §14, §15.8.*

- **Objective**: Verify that external revocation of Android SAF permissions cleanly halts background workers, blocks invalid UI operations with the dedicated Permission screen, and self-heals immediately upon permission re-grant.
- **Step-by-Step Flow**:
  1. Active app session with 2 chapters queued for download.
  2. Simulate OS revoking persistable URI permission (`takePersistableUriPermission` cleared).
  3. `InstallJobWorker` attempts to create file; catches `SecurityException`.
  4. Worker marks job status paused; exits gracefully without crash.
  5. User opens app. `Startup` sequence detects missing permission; navigates immediately to `PermissionScreen`.
  6. Screen shows: "L'app ne voit plus le dossier Mihon", expected path, and "Choisir à nouveau le dossier".
  7. User clicks button and re-selects the Mihon directory.
  8. App validates subfolders (`downloads/`, `local/`, `autobackup/`), re-grants persistable permission, and displays "Accès rétabli".
  9. Tap "Revenir à l'accueil". App re-runs `Startup`.
  10. Background transfer workers resume; both pending chapters download and install cleanly.
- **Verification Assertions**:
  - App never crashes with unhandled `SecurityException`.
  - Both pending chapters successfully install into `Mihon/local/` after re-authorization.

---

### Scenario 5: Corrupt Download Detection, Automatic Rollback & Re-fetch
*Authoritative Reference: `CAHIER_DES_CHARGES.md` §7.3.2, §8.4; `PLAN_GEMINI.md` §5.13.*

- **Objective**: Verify that a corrupted network transfer (bit flip or truncated download) is detected by SHA-256 verification, safely rolled back without leaving partial files, retried automatically, and cleanly installed upon successful retry.
- **Step-by-Step Flow**:
  1. `FakePc` publishes `result.json` with expected SHA-256 `hash_original` for Chapter 15.
  2. `FakeDrive` is configured to inject corruption into the initial download stream (flips bytes in transit).
  3. `InstallJobWorker` downloads file to temporary cache `temp_corrupt.cbz`.
  4. Worker calculates SHA-256 of downloaded file; detects mismatch against `result.json.file.sha256`.
  5. Worker deletes `temp_corrupt.cbz`. Verifies NO file was written to `Mihon/local/`.
  6. Worker attempts second download pass without corruption.
  7. Second pass completes; SHA-256 verifies cleanly.
  8. `LocalInstaller` writes to hidden temp file, verifies size, and atomically renames to `Chapter 15.cbz`.
  9. Status transitions to `INSTALLED`; `received.json` written on Drive.
- **Verification Assertions**:
  - No corrupted file ever exposed to Mihon reader.
  - Final installed file matches `hash_original` perfectly.
  - Job completes in `INSTALLED` state.

---

## 8. Implementation & Verification Roadmap

### 8.1 Execution Command Matrix

| Target Verification Area | Execution Command | Acceptance Criterion |
|---|---|---|
| **Tier 1: Feature Tests** | `./gradlew testDebugUnitTest --tests "com.tmm.mobile.e2e.tier1_features.*"` | 100% Pass (0 failures, $\ge 55$ test cases) |
| **Tier 2: Boundary Tests** | `./gradlew testDebugUnitTest --tests "com.tmm.mobile.e2e.tier2_boundaries.*"` | 100% Pass (0 failures, $\ge 50$ test cases) |
| **Tier 3: Interactions** | `./gradlew testDebugUnitTest --tests "com.tmm.mobile.e2e.tier3_interactions.*"` | 100% Pass (0 failures, 8 matrix cases) |
| **Tier 4: Workloads** | `./gradlew testDebugUnitTest --tests "com.tmm.mobile.e2e.tier4_workloads.*"` | 100% Pass (0 failures, 5 workflows) |
| **Visual Regressions** | `./gradlew verifyRoborazziDebug` | 0 pixel drift against `maquettes/captures/` |
| **Lint & Code Quality** | `./gradlew lintDebug` | 0 errors, 0 warnings |
| **Package Compilation** | `./gradlew assembleDebug` | Produces valid signed debug APK |

### 8.2 Deliverable Artifact Checklist
- [x] Master E2E specification document `TEST_INFRA.md` published to repository root.
- [ ] Hermetic test doubles (`FakeDrive.kt`, `FakePc.kt`, `FakeMihonFs.kt`, `TestClock.kt`) implemented in `app/src/test/java/com/tmm/mobile/testutil/`.
- [ ] Protocol and backup test fixtures added to `app/src/test/resources/fixtures/`.
- [ ] Tier 1 to Tier 4 test classes implemented in `app/src/test/java/com/tmm/mobile/e2e/`.
- [ ] Test execution verification report `TEST_READY.md` aggregated and published by orchestrator upon test suite completion.
