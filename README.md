# TMM Mobile (Translate My Manga)

> **Application compagnon Android native pour [Translate My Manga](https://github.com/Minonix).**

[![Android CI](https://github.com/Minonix/app-translate-my-manga/actions/workflows/android.yml/badge.svg)](https://github.com/Minonix/app-translate-my-manga/actions/workflows/android.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Android-green.svg)](https://developer.android.com/jetpack/compose)
[![AI Assisted](https://img.shields.io/badge/Développement-Assisté%20par%20IA-blue.svg)](#-projet-conçu-avec-assistance-ia)

---

## 🤖 Projet Conçu avec Assistance IA

Ce projet a été développé en binôme (Pair Programming) avec des agents d'intelligence artificielle avancés (**Claude Opus/Sonnet**, **Google Gemini** et l'environnement **Antigravity**). L'architecture logicielle, l'implémentation Kotlin / Jetpack Compose, la suite de tests et la documentation ont été réalisés sous supervision humaine rigoureuse.

---

## 📖 Présentation

**TMM Mobile** est l'application mobile compagnon du moteur de traduction PC pour mangas. Le smartphone n'effectue pas les traductions lui-même :
1. **Détection** : L'application scanne les chapitres téléchargés par le lecteur Mihon via le Storage Access Framework (SAF) et les sauvegardes `.tachibk`.
2. **Transfert** : Elle prépare des archives `.cbz` et transmet les demandes de traduction au logiciel PC :
   - Soit en **liaison directe** locale (HTTPS / Tailscale) chiffrée.
   - Soit via une **boîte aux lettres asynchrone Google Drive**.
3. **Récupération** : Les chapitres traduits sont automatiquement récupérés et réinstallés dans la bibliothèque locale Mihon.

---

## 🔒 Confidentialité & Sécurité

- **Aucun serveur central** : L'application ne dépend d'aucun serveur centralisé, d'aucune base de données hébergée et d'aucun service tiers de collecte.
- **Isolation des comptes** : Chaque utilisateur utilise son propre compte Google Drive personnel (`App/TranslateMyManga`). Aucune donnée n'est partagée entre utilisateurs.
- **Sécurité matérielle** : Les jetons d'appairage direct PC-Mobile sont chiffrés en **AES-256 GCM** adossé au **Hardware Android KeyStore**.
- **Zéro télémétrie** : Aucun traceur publicitaire, aucun SDK analytique, aucun rapporteur de crash distant.
- **Protection des sauvegardes** : L'attribut `android:allowBackup="false"` protège les données locales et la base Room contre les extractions ADB ou cloud non autorisées.
- **Audit de sécurité** : Un audit complet du code et de l'historique Git a été réalisé avant publication ([Consulter le rapport d'audit](doc/security/audit_securite_pre_public.md)).

---

## 🛠️ Stack Technique

- **Langage & UI** : Kotlin, Jetpack Compose, Material Design 3 personnalisé, Polices Bricolage Grotesque & IBM Plex.
- **Architecture** : Découpage unitaire, conteneur de dépendances maison (`DefaultAppContainer`), pas de framework lourd d'injection.
- **Stockage local** : Room Database SQLite (`TmmDatabase`), DataStore Preferences (`AppPrefs`).
- **Tâches d'arrière-plan** : Android WorkManager (`CoroutineWorker`).
- **Réseau & Protocoles** : OkHttp 5, Google Play Services Auth (`AuthorizationClient`), protocoles JSON / Protobuf.
- **Tests** : Robolectric, Roborazzi (tests de régression visuelle), FakeDrive et FakePc intégrés.

---

## 🚀 Compilation & Tests

Le projet se compile avec le Gradle Wrapper fourni :

```bash
# Lancer l'ensemble des tests unitaires et Robolectric
./gradlew testDebugUnitTest

# Vérifier la qualité du code (0 avertissement toléré)
./gradlew lintDebug

# Compiler l'APK de débogage
./gradlew assembleDebug

# Compiler l'APK Release (signé si keystore.properties est configuré)
./gradlew assembleRelease
```

---

## 📚 Documentation

L'ensemble de la documentation technique et des spécifications est regroupé dans le dossier `/doc` :
- [Architecture & Plan](doc/wbs.md)
- [Rapport d'Audit de Sécurité](doc/security/audit_securite_pre_public.md)
- [Historique des versions](CHANGELOG.md)
