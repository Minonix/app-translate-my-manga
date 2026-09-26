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
  - [x] E4.2 : écran Library (commit `15f07ab`)
    - `LibraryState`/`LibraryEvent`/`LibraryScreen`/`LibraryViewModel` (patron section 5.20) ; ViewModel synchrone (pas de `viewModelScope.launch`/`delay`), Library.dc.html n'ayant aucune transition temporisée côté logique
    - Aucun nouveau composant partagé requis : réutilise tel quel `TmmChip`, `TmmSegmented`, `TmmSearchField`, `TmmActionButton`, `TmmSheet`, `TmmCheckbox`, `TmmButton`, `TmmToast`, `StatusBadge`, `ProgressBar`, `dashedBorder`
    - `Cover` étendu d'un paramètre `color` optionnel rétrocompatible (couleur exacte de la maquette plutôt que le hash de `seriesKey`) ; `TmmColors.fromHex` ajouté en repli
    - Écart corrigé en marge de ce commit : `RefreshButton` (E4.1) utilisait des `contentDescription` en dur plutôt qu'en `stringResource` (règle 4.10)
    - `<plurals>` ajoutés à `strings.xml` pour les décomptes (CLDR français "one" couvrant 0 et 1), lus via `pluralStringResource` (compose-bom 2026.06.01, vérifié avant usage plutôt que supposé)
    - `mockup_texts.tsv` complété pour Library.dc.html ; `"Library.dc.html"` retiré de `MockupTextCoverageTest.pendingScreens`
    - Tests écrits : `LibraryScreenshotTest` (Roborazzi : grille, sélection, vide, confirmation, toast), `LibraryScreenBehaviorTest` (recherche, filtres catégorie/vue, sélection/tout cocher, garde `selectionToVerify == 0` sur la retraduction, formulation des toasts)
  - [x] E4.3 : écran Series
    - BLOQUÉ-1 résolu : Gradle daemon et suite de tests unitaires/Robolectric pleinement fonctionnels sous Windows Powershell (`robolectric.properties` configuré avec `sdk=34` et `qualifiers=w390dp-h844dp-xhdpi`).
    - `SeriesState`, `SeriesEvent`, `SeriesScreen`, `SeriesViewModel` (patron 5.20) : conformité stricte avec `Series.dc.html` (carte de série, couverture en relief demi-teinte, compteurs de localisation, bandeau d'alerte trou de suite, liste des chapitres avec indicateurs 3 colonnes téléphone/PC/FR, dépliage des puces de statistiques de traduction, barre d'actions multi-chapitres et feuille d'actions pour toute la série).
    - `TmmCheckbox` étendu d'un paramètre `enabled = true` pour verrouiller les chapitres Busy ou Gap.
    - Ressources de chaînes et pluriels ajoutés dans `strings.xml` ; `Series.dc.html` couvert dans `mockup_texts.tsv` et retiré des exclusions de `MockupTextCoverageTest`.
  - [x] E4.4 : écran Sync (Transferts)
    - `SyncState`, `SyncEvent`, `SyncScreen`, `SyncViewModel` (patron 5.20) : conformité stricte avec `Sync.dc.html` (schéma de transit Téléphone ↔ Google Drive ↔ PC avec voies animées de billes, totaux à envoyer / à récupérer, liste des transferts en direct `liveTransfers`, bloc À envoyer avec top séries + case à cocher traduction automatique + bouton d'envoi et bandeau de succès Drive, bloc À récupérer avec liste des séries traduites + statut Rangé, carte de scan Mihon avec bouton Rescanner et animation de rotation, carte d'alerte des chapitres manquants).
    - Ressources de chaînes et pluriels ajoutés dans `strings.xml` ; `Sync.dc.html` couvert dans `mockup_texts.tsv` et retiré des exclusions de `MockupTextCoverageTest`.
    - Tests validés : `SyncScreenBehaviorTest` (5 tests comportementaux couvrant initialisation, bascule traduction auto, envoi vers PC, récupération vers Mihon, ré-analyse), `SyncScreenshotTest` (4 captures Roborazzi : défaut, live vide, envoi terminé, téléchargement terminé). 100% de succès.
  - [x] E4.5 : écran Queue (File)
    - `QueueState`, `QueueEvent`, `QueueScreen`, `QueueViewModel` (patron 5.20) : conformité stricte avec `Queue.dc.html` (3 onglets Actives, Terminées, Échecs avec badges de décompte, barre des 4 slots distants en parallèle sur PC-BUREAU, carte sombre du travail en cours avec jauge de progression 5 étapes Récup/OCR/Trad/Lettrage/Vérif, liste des autres travaux actifs cliquables avec retour au focus, section d'attente "Ensuite" avec suppression d'item, onglet Terminées avec dépliage des puces de métriques et alertes de relecture ciblée "Vérifier la page" / "Dans la file", onglet Échecs avec carte d'alerte moteur et relance "Réessayer à partir de la page 9" / statut "Remis dans la file").
    - Ressources de chaînes et pluriels ajoutés dans `strings.xml` ; `Queue.dc.html` couvert dans `mockup_texts.tsv` et retiré des exclusions de `MockupTextCoverageTest`.
    - Tests validés : `QueueScreenBehaviorTest` (6 tests comportementaux couvrant affichage initial, bascule d'onglets, changement de focus du travail actif, retrait d'un job en attente, ajout en file d'une vérification de page, relance après échec), `QueueScreenshotTest` (4 captures Roborazzi : actif, terminé, échec, réessayé). 100% de succès (86 tests unitaires au total).
  - [x] E4.6 : écran Catalog (Catalogue)
    - `CatalogState`, `CatalogEvent`, `CatalogScreen`, `CatalogViewModel` (patron 5.20) : conformité stricte avec `Catalog.dc.html` (carte sombre de sauvegarde Mihon .tachibk avec statut d'import, jauge d'étape animée si réimport en cours, bouton 'Relire la dernière sauvegarde', grille 3 colonnes de statistiques avec bordure pointillée pour les séries non téléchargées ouvrant la bibliothèque, liste des 7 catégories en lecture seule avec jauge proportionnelle et chevrons de navigation).
    - Ressources de chaînes ajoutées dans `strings.xml` ; `Catalog.dc.html` couvert dans `mockup_texts.tsv` et retiré des exclusions de `MockupTextCoverageTest`.
    - Tests validés : `CatalogScreenBehaviorTest` (5 tests comportementaux couvrant affichage initial, relance de re-import avec compteurs mis à jour, affichage d'état occupé avec libellé d'étape, ouverture de catégorie, navigation vers séries non téléchargées), `CatalogScreenshotTest` (3 captures Roborazzi : défaut, occupé, réimporté). 100% de succès (91 tests unitaires au total).
  - [x] E4.7 : écran NewTranslation (Nouvelle traduction)
    - `NewTranslationState`, `NewTranslationEvent`, `NewTranslationScreen`, `NewTranslationViewModel` (patron 5.20) : conformité stricte avec `NewTranslation.dc.html` (processus en deux étapes Étape 1 Sélection de série avec recherche dynamique et liste des séries avec compteurs de chapitres, Étape 2 Sélection des chapitres avec filtres Tout cocher / Aucun / Non traduits, bouton d'option Traduire à l'arrivée vers le PC, barre d'actions flottante avec confirmation d'envoi et bouton retour).
    - Ressources de chaînes ajoutées dans `strings.xml` ; `NewTranslation.dc.html` couvert dans `mockup_texts.tsv` et retiré des exclusions de `MockupTextCoverageTest`.
    - Tests validés : `NewTranslationScreenBehaviorTest` (7 tests comportementaux couvrant recherche de série, sélection de série, sélection/désélection globale des chapitres, bascule option traduire à l'arrivée, envoi de traduction, retour arrière), `NewTranslationScreenshotTest` (4 captures Roborazzi : étape 1 recherche, étape 2 chapitres, sélection partielle, confirmation). 100% de succès (104 tests unitaires au total).
  - [x] E4.8 : écran Settings (Réglages)
    - `SettingsState`, `SettingsEvent`, `SettingsScreen`, `SettingsViewModel` (patron 5.20) : conformité stricte avec `Settings.dc.html` (carte sombre de statut du PC avec indicateur d'état connecté / dernier signal reçu, 5 groupes de réglages Google Drive, Mihon, Traduction, Réseau, Diagnostic avec boutons d'actions contextuels 'Changer', 'Modifier' avec cycle de rétention à 3 états, 'Exporter' des journaux, liens vers Catalogue, Onboarding et Paramètres de traduction, interrupteur Wi-Fi uniquement avec libellé dynamique, bannière toast temporisée d'information).
    - Ressources de chaînes ajoutées dans `strings.xml` ; `Settings.dc.html` couvert dans `mockup_texts.tsv` et retiré des exclusions de `MockupTextCoverageTest`.
    - Tests validés : `SettingsScreenBehaviorTest` (7 tests comportementaux couvrant affichage initial du PC et des groupes, retour arrière, changement de compte Google avec toast, cycle de rétention Drive 3 états, changement de dossier Mihon avec toast, navigation vers les 3 écrans liés, bascule interrupteur Wi-Fi, export des journaux avec toast), `SettingsScreenshotTest` (4 captures Roborazzi : défaut, Wi-Fi actif, toast actif, PC hors ligne). 100% de succès (116 tests unitaires au total).
- [ ] E5 : Room, DataStore, TmmDatabase & DAO
- [ ] E6 : Accès SAF, parsing Mihon, CBZ & installation
- [ ] E7 : FakeDrive, FakePc & transport REST OkHttp
- [ ] E8 : Protocole Drive JSON & synchronisation bidirectionnelle
- [ ] E9 : WorkManager, orchestration arrière-plan & refresh
- [ ] E10 : Câblage données réelles, ViewModel & onboarding
- [ ] E11 : Durcissement, lint, ProGuard, APK final & rapport
- [ ] Suite de tests E2E & durcissement adversarial
