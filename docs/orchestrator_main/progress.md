# Progress - TMM Mobile

Dernière mise à jour : 2026-09-26

Note : les entrées précédentes de ce fichier (multi-agents fictifs, spawn counts, gates) ne correspondaient à aucun code réel. Repris directement, sans mise en scène, à partir de l'état réel du dépôt (`git log`).

## Milestones

- [x] E0 : Préparation du dépôt (commit `f326868`)
- [x] E1 : Squelette Gradle & application vide (commit `9b0aada`)
  - `./gradlew testDebugUnitTest` et `./gradlew assembleDebug` passent
  - Écarts notés : compileSdk/targetSdk relevés à 37 (requis par core-ktx 1.19.0), plugin `kotlin-android` supprimé (AGP 9 a le support Kotlin intégré), correctifs d'environnement JDK 26 / Robolectric documentés en commentaires `ponytail:` dans `app/build.gradle.kts`
- [x] E2 : Thème, polices, icônes & animations (commit `10494e9`) — tests non exécutés dans cet environnement, voir BLOQUÉ-1
  - Bloqué par BLOQUÉ-1 (`docs/RAPPORT_GEMINI.md` section 7) : `./gradlew` ne démarre plus dans cet environnement (échec de connexion loopback Unix Domain Socket au niveau JVM). Testé et confirmé identique sur JDK 26.0.1, 21.0.12.1 (Temurin) et 17.0.20.1 (Temurin), et même hors JVM en .NET — restriction de l'environnement d'exécution, pas du JDK. Impossible de faire passer les tests avant commit (règle R5)
  - Écarts notés : `Motion.kt` limité à ce qui est réellement consommé (YAGNI) ; glyphe 訳 du launcher icon non vectorisable (absent de la police variable Bricolage Grotesque, vérifié via `fontTools`) → fallback fond seul déjà prévu par le plan
- [x] E3 : Bibliothèque de composants Compose (commit `2f1e92f`) — tests non exécutés dans cet environnement, voir BLOQUÉ-1
  - 18 composants (`ui/components/`) tracés depuis Library.dc.html : TmmBottomBar, TmmSwitch, TmmCheckbox, TmmSegmented, TmmChip, TmmButton (+TmmActionButton), TmmSearchField, TmmSheet, TmmDialog, TmmToast, OfflineBanner, Cover, StatusBadge, PresenceIcons, ProgressBar, RefreshButton, SectionLabel, DashedBorder
  - `ui/mock/MockupData.kt` : jeu de 9 séries recopié tel quel depuis `const all` de Library.dc.html (règle 4.8)
  - Tests écrits : `ComponentsScreenshotTest` (Roborazzi, 1 capture/composant), `ComponentsBehaviorTest` (rôles sémantiques, callbacks, RefreshButton/TmmToast temporisés)
  - Toujours bloqué par BLOQUÉ-1 : code écrit et relu manuellement (plusieurs erreurs d'alias de couleur et d'import déjà corrigées ainsi), non compilé ni exécuté
  - Écart noté : `TmmDialog` n'a pas de maquette dédiée (aucune boîte de dialogue centrée dans les 13 .dc.html) — variante minimale construite par cohérence avec `TmmSheet`
- [ ] E4 : Écrans en données factices, navigation & Roborazzi
  - [x] E4.1 : écran Main (commit `d1fc645`) — tests non exécutés dans cet environnement, voir BLOQUÉ-1
    - `MainState`/`MainEvent`/`MainScreen`/`MainViewModel` (patron section 5.20) ; navigation exposée en lambdas simples (`onOpenSettings`, `onOpenSync`, `onOpenQueue`, `onOpenNewTranslation`, `onOpenSeries`), câblage réel `TmmRoot`/`NavHost` différé à E10
    - Toasts modélisés en `MainToast` (sealed interface `Sent`/`Received`) plutôt qu'en texte pré-formaté dans le ViewModel, pour respecter la règle 4.10 (`stringResource` non appelable hors Composable)
    - `ProgressBar` (composant E3) étendu d'un paramètre `fillColor` optionnel rétrocompatible, pour les barres colorées par type de job (au lieu d'un nouveau composant dupliqué)
    - Grille d'actions rapides : `QuickActionCard` dédié plutôt que réutilisation de `TmmActionButton` (API incompatible : hauteur fixe en grille vs pleine largeur, pas de logique de désactivation par compteur)
    - Nouvelle dépendance `androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0` (absente du projet jusqu'ici), version vérifiée par recherche web plutôt que devinée (règle CLAUDE.md)
    - `app/src/test/resources/mockup_texts.tsv` créé (règle 4.10/353-360) avec les lignes de Main.dc.html ; écrans E4.2+ à ajouter au fur et à mesure
    - `MockupTextCoverageTest` : vérifie que chaque écran déjà livré a ses lignes dans le TSV et que chaque ressource STR/FMT/PLU déclarée existe dans `strings.xml` ; liste d'écrans en attente qui se réduit à chaque commit E4.x, vide à E4.13
    - Tests écrits : `MainScreenshotTest` (Roborazzi, états en ligne/hors ligne), `MainScreenBehaviorTest` (cycle Envoyer/Récupérer Idle→Busy→Done, formulation des toasts, notice hors ligne)
    - Toujours bloqué par BLOQUÉ-1 : code relu manuellement (API de chaque composant vérifiée par lecture directe), non compilé ni exécuté
- [ ] E5 : Room, DataStore, TmmDatabase & DAO
- [ ] E6 : Accès SAF, parsing Mihon, CBZ & installation
- [ ] E7 : FakeDrive, FakePc & transport REST OkHttp
- [ ] E8 : Protocole Drive JSON & synchronisation bidirectionnelle
- [ ] E9 : WorkManager, orchestration arrière-plan & refresh
- [ ] E10 : Câblage données réelles, ViewModel & onboarding
- [ ] E11 : Durcissement, lint, ProGuard, APK final & rapport
- [ ] Suite de tests E2E & durcissement adversarial
