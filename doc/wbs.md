# Découpage Structural (PBS) et Arborescence des Tâches (WBS)
## Audit Sécurité Pré-Publication & Confidentialité

Ce document cadre l'audit exhaustif de sécurité avant l'ouverture publique du dépôt GitHub `app-translate-my-manga`.

---

### Product Breakdown Structure (PBS)

- **PBS 1 : Secrets & Credentials**
  - Historique complet Git (161 commits analysés) : scan des diffs, blobs, tags.
  - Workspace actuel : fichiers ignorés, fichiers de build, fichiers de config.
  - Formats critiques : `.env`, `keystore.properties`, `*.jks`, `google-services.json`, clés API, tokens OAuth.

- **PBS 2 : Architecture d'Authentification & Confidentialité des Comptes**
  - Flux Google OAuth Android (`GoogleAuth`, scopes, token cache).
  - Boîte aux lettres Google Drive (`drive.file`, dossier racine `TranslateMyManga`).
  - Flux Direct PC-Mobile (HTTP/WebSocket, découverte, absence d'adresses IP ou endpoints privés en dur).

- **PBS 3 : Protection des Données Personnelles (PII) & Sanitization**
  - Chemins absolus ou noms de machines / d'utilisateurs locaux (ex: `clement martini`).
  - Système de logging applicatif (`AppLog`) et mécanisme de caviardage PII.
  - Base de données locale Room et fichiers temporaires / CBZ.

- **PBS 4 : Surface d'Attaque & Dépendances Externes**
  - Permissions déclarées dans `AndroidManifest.xml`.
  - Exported components (Activities, Services, BroadcastReceivers, ContentProviders).
  - Sécurité des dépendances et scripts de build Gradle.

- **PBS 5 : Documentation, Traçabilité & Mention IA**
  - Rapport complet d'audit dans `/doc/security/audit_securite_pre_public.md`.
  - Mentions claires et transparentes sur le développement assisté par IA.

---

### Work Breakdown Structure (WBS)

- [x] **WBS 1. Audit Exhaustif de l'Historique Git (Secrets & Données Sensibles)**
  - [x] 1.1 Exécution d'un scan regex multi-patterns sur l'ensemble des 161 commits (API keys Google, AI, AWS, GitHub, JWT, clés privées).
  - [x] 1.2 Inspection de l'historique des fichiers sensibles spécifiques (`keystore.properties`, `local.properties`, fichiers `.p12`, `.jks`).
  - [x] 1.3 Recherche de chaînes nominatives / chemins locaux (`clement`, `martini`) dans l'historique Git et les blobs.

- [x] **WBS 2. Audit du Code Source & Configuration Actuelle**
  - [x] 2.1 Inspection de `app/build.gradle.kts`, `gradle.properties`, `AndroidManifest.xml`.
  - [x] 2.2 Analyse des composants Android exportés et de leurs permissions.
  - [x] 2.3 Vérification des règles `.gitignore` et protection des traces d'agents et logs.

- [x] **WBS 3. Audit Approfondi des Flux d'Authentification & Réseau**
  - [x] 3.1 Examen de `GoogleAuth.kt`, `DriveApi.kt`, `DriveTransport.kt` : isolation stricte des comptes utilisateur (aucun compte partagé hardcodé).
  - [x] 3.2 Examen de la liaison PC et des connexions directes (`DirectTransport.kt`, tokens chiffrés AES-256 GCM dans `AndroidKeyStore`).
  - [x] 3.3 Vérification du module de logs `AppLog.kt` (tampon RAM, caviardage PII systématique à l'export).

- [x] **WBS 4. Tests de Régression & Validation Globale**
  - [x] 4.1 Assainissement de l'email personnel dans les tests et des chemins locaux dans la doc.
  - [x] 4.2 Exécution complète des tests unitaires (`./gradlew testDebugUnitTest` : 100% de succès).

- [x] **WBS 5. Livrables & Mention Développement Assisté par IA**
  - [x] 5.1 Génération du rapport formel d'audit dans `/doc/security/audit_securite_pre_public.md`.
  - [x] 5.2 Création de `README.md` et `doc/README.md` avec la mention explicite de conception/développement assisté par IA.
