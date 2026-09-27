# Original User Request

## 2026-09-26T12:23:20Z

Implémentation complète de l'application Android native TMM Mobile V1 (Kotlin, Compose, Room, WorkManager, SAF, Google Drive REST) en suivant scrupuleusement docs/PLAN_GEMINI.md, docs/CAHIER_DES_CHARGES.md et les maquettes HTML/CSS de maquettes/.

Working directory: c:\Users\clement martini\Documents\GitHub\app-translate-my-manga
Integrity mode: development

## Requirements

### R1. Socle technique, architecture et Design System strict (E0 - E3)
- Initialiser le projet Gradle (Kotlin 2.x, AGP stable, Compose BOM, sans Hilt ni Retrofit ni Material Icons).
- Configurer les polices Google Fonts officielles (Bricolage Grotesque, IBM Plex Sans, IBM Plex Mono) et la palette stricte extraite des maquettes.
- Implémenter la bibliothèque de composants maison (barre de navigation, switches, fiches, badges, animations CSS transcrites en Compose).
- Valider la conformité via les tests de garde NoRawColorTest, NoMaterialIconsTest, PaletteCoverageTest, IconCoverageTest.

### R2. Écrans Compose en données factices et navigation (E4.1 - E4.13)
- Développer les 13 écrans fidèles au pixel près aux gabarits maquettes/*.dc.html.
- Mettre en place la couverture textuelle complète (mockup_texts.tsv et MockupTextCoverageTest).
- Valider chaque écran par captures et comparaisons visuelles automatisées Roborazzi (docs/rapport_gemini/compare/*.png).

### R3. Persistance locale Room, DataStore et intégration Mihon (E5 - E6)
- Implémenter la base de données Room (TmmDatabase), DAO, préférences DataStore et dérivations pures d'état des chapitres.
- Intégrer l'accès SAF (MihonTree), le décodage Protobuf des sauvegardes .tachibk, le scan incrémental des téléchargements et le packaging CBZ (CbzPacker, LocalInstaller).

### R4. Protocole Google Drive, simulateurs et travaux d'arrière-plan (E7 - E9)
- Implémenter le transport Drive v3 REST via OkHttp, l'authentification Google OAuth et le protocole d'échange JSON.
- Développer un FakeDrive (MockWebServer) et un FakePc pour des tests unitaires et de bout en bout hermétiques sans dépendance réseau.
- Orchestrer les envois, téléchargements et synchronisations de réglages distants via WorkManager et RefreshCoordinator.

### R5. Raccordement données réelles, durcissement et packaging (E10 - E11)
- Raccorder les écrans aux ViewModel réels, gérer les permissions SAF, l'onboarding complet et l'export sécurisé des journaux.
- Corriger toute anomalie de lint (lintDebug), configurer les règles ProGuard et produire l'APK validé (assembleDebug).
- Maintenir le document docs/RAPPORT_GEMINI.md à chaque étape avec preuves d'exécution.

## Acceptance Criteria

### Tests & Qualité
- [ ] `./gradlew testDebugUnitTest` passe avec 0 échec sur l'ensemble de la suite de tests (tests unitaires, Robolectric, DAO, protocoles, scénarios complets JobFlowTest).
- [ ] `./gradlew verifyRoborazziDebug` valide la conformité des 13 écrans avec les captures des maquettes.
- [ ] `./gradlew lintDebug` se termine sans aucune erreur.
- [ ] `./gradlew assembleDebug` produit l'APK fonctionnel app-debug.apk.

### Traçabilité & Git
- [ ] Branche `feat/v1-android` initialisée depuis `main`.
- [ ] Commits atomiques respectant strictement la nomenclature définie dans `docs/PLAN_GEMINI.md` pour chaque étape (E0 à E11).
- [ ] Rapport `docs/RAPPORT_GEMINI.md` renseigné de manière factuelle avec versions, hashs et statuts.
