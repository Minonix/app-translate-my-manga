# Orchestration Plan - TMM Mobile V1 Implementation

## Objective
Implement TMM Mobile V1 native Android application per docs/PLAN_GEMINI.md, docs/CAHIER_DES_CHARGES.md, and maquettes/, passing 100% tests, Roborazzi comparisons, and lint checks.

## Phase 0: Survey & Scope Mapping (Current)
- Spawn 3 Explorers / Spec Miners:
  1. `spec_miner_specs`: Deep analysis of docs/PLAN_GEMINI.md and docs/CAHIER_DES_CHARGES.md (requirements, architecture, modules, milestones, dependencies, conventions).
  2. `explorer_codebase`: Analysis of existing repository state, files, build setup, git status, tools, and constraints.
  3. `spec_miner_maquettes`: Analysis of maquettes/ (*.dc.html, mockup_texts.tsv, CSS styles, colors, typography, components, Roborazzi test expectations).
- Synthesize findings into `PROJECT.md` at project root with complete Feature Inventory, Milestones E0-E11, and Interface Contracts.
- Initialize `TEST_INFRA.md` for the parallel E2E Testing Track.

## Implementation Track: Milestones E0 through E11
- **E0**: Initialisation & conventions Gradle (build.gradle.kts, libs.versions.toml, Roborazzi, Room, compose compiler, zero warning).
- **E1**: Polices, thèmes & tokens stricts (Bricolage Grotesque, IBM Plex Sans/Mono, palette Color, TmmTheme).
- **E2**: Bibliothèque de composants Compose (barre de navigation, switches, fiches, badges, progress bars, animations).
- **E3**: Tests de garde stricts Design System (NoRawColorTest, NoMaterialIconsTest, PaletteCoverageTest, IconCoverageTest).
- **E4**: Écrans en données factices, navigation & Roborazzi (13 écrans E4.1 - E4.13, mock data, navigation graph, Roborazzi screenshot tests).
- **E5**: Room, DataStore, TmmDatabase & DAO (Room entities, converters, DAO, pure status derivations, DataStore preferences).
- **E6**: Accès SAF, parsing Mihon, CBZ & installation (MihonTree, .tachibk protobuf decoder, CBZ unpacker/packer, LocalInstaller).
- **E7**: FakeDrive, FakePc & transport REST OkHttp (MockWebServer FakeDrive, fake Google OAuth, FakePc, OkHttp REST client).
- **E8**: Protocole Drive JSON & synchronisation bidirectionnelle (manifests, job flows, queue, sync engine).
- **E9**: WorkManager, orchestration arrière-plan & refresh (SyncWorker, RefreshCoordinator, notifications, scheduling).
- **E10**: Câblage données réelles, ViewModel & onboarding (ViewModels, StateFlow, SAF permissions, end-to-end integration).
- **E11**: Durcissement, lint, ProGuard, APK final & rapport (lintDebug, ProGuard/R8, assembleDebug, docs/RAPPORT_GEMINI.md).

## E2E Testing Track (Parallel)
- Derived from requirements in ORIGINAL_REQUEST.md and docs/CAHIER_DES_CHARGES.md.
- Tiers 1-4 (opaque-box, progressive testability) -> publishes TEST_READY.md.
- Tier 5 (adversarial hardening with Challenger).

## Quality Gates per Milestone
- Explorer (3) -> Worker (1) -> Reviewer (2) -> Challenger (2) -> Auditor (1).
- Gate criteria:
  1. Build and tests pass.
  2. All Reviewers APPROVE.
  3. All Challengers confirm correctness.
  4. Auditor verdict is CLEAN (binary veto on cheating/hardcoding/dummy code).
