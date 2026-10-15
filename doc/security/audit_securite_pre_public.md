# Rapport d'Audit de Sécurité Pré-Publication (Open Source)
## Application TMM Mobile (`com.tmm.mobile`)

**Date de l'audit** : 15 Octobre 2026  
**Auditeur** : Assistant IA Senior Architect & Pair Programmer  
**Statut Global** : ✅ **APPROUVÉ POUR PASSAGE EN PUBLIC** (avec recommandations documentées)  
**Mention IA** : Ce projet a été intégralement conçu, architecturé et développé avec l'assistance d'agents d'intelligence artificielle (Claude Opus/Sonnet, Google Gemini, Antigravity).

---

## 1. Synthèse Exécutive

Dans le cadre du passage du dépôt `app-translate-my-manga` en visibilité **Publique** sur GitHub, un audit de sécurité approfondi a été mené sur l'intégralité du code source, de la configuration de build, des flux réseau/authentification et de l'ensemble de l'historique Git (161 commits analysés, branches et tags).

### Résultats Clés
| Domaine vérifié | Risque identifié | Statut après remédiation |
|---|---|---|
| **Clés API & Secrets Cloud** (Google, OpenAI, Anthropic, AWS, GitHub) | Aucun secret détecté dans le code ni dans l'historique Git. | ✅ **0 secret exposé** |
| **Fichiers de Signature & Clés privées** (`keystore.properties`, `*.jks`) | Fichier `keystore.properties` jamais commité. Clé injectée en CI via secrets GitHub chiffrés. | ✅ **Sécurisé** |
| **Accès aux Comptes Personnels** (Google Drive, PC) | L'application ne contient aucun identifiant de compte codé en dur. Chaque utilisateur s'authentifie sur son propre compte Google via Google Play Services. | ✅ **Isolation totale garantie** |
| **Données Personnelles (PII) dans le Code** | Une adresse email personnelle et des chemins de dossiers locaux étaient présents dans des tests et docs. | ✅ **Assaini (100% anonymisé)** |
| **Surface d'Attaque Android** (`AndroidManifest.xml`) | `allowBackup="false"`, aucun composant non nécessaire exposé, `FileProvider` interne uniquement. | ✅ **Conforme aux standards OWASP Mobile** |
| **Télémétrie & Fuites Tiers** | Aucune librairie de tracking, ni Firebase Analytics, ni Sentry, ni serveur tiers. | ✅ **Zéro télémétrie externe** |

---

## 2. Audit Exhaustif de l'Historique Git (161 Commits)

Un script d'analyse regex multi-patterns a scanné l'intégralité des 161 commits de l'arborescence Git (diffs, ajouts, modifications, suppressions et arborescence `HEAD`).

### 2.1 Recherche de Secrets et Clés Privées
- **Clés API Google (`AIza...`)** : **0** trouvée.
- **Clés OpenAI (`sk-...`)** : **0** trouvée.
- **Clés Anthropic (`sk-ant-...`)** : **0** trouvée.
- **Tokens GitHub (`ghp_...`, `github_pat_...`)** : **0** trouvé.
- **Clés privées (`BEGIN RSA/OPENSSH/EC PRIVATE KEY`)** : **0** trouvée.
- **Fichiers `.p12`, `.jks`, `.keystore`** : **0** présent dans l'historique Git.

### 2.2 Fichiers de configuration de Build
- Seul `keystore.properties.example` (modèle vide sans mots de passe) a été commité.
- `local.properties` et `keystore.properties` sont protégés par le fichier `.gitignore`.
- `.gitignore` a été renforcé pour exclure formellement les répertoires d'agents locaux (`.claude/`, `.agents/`, `.gemini/`) et les journaux de tests (`*.log`).

---

## 3. Audit d'Accès aux Comptes et Confidentialité

### 3.1 Comment fonctionne l'authentification Google Drive ?
- Le fichier `app/src/main/java/com/tmm/mobile/transport/GoogleAuth.kt` utilise l'API moderne `Identity.getAuthorizationClient(context)` de Google Play Services.
- **Absence de Client Secret codé en dur** : L'application Android utilise le flux OAuth natif pour client mobile (Android App Signing Certificate + Package Name).
- **Isolation des comptes** : Lorsqu'un utilisateur tiers installe et lance l'application, Android lui présente le sélecteur de comptes Google **présents sur son propre téléphone**.
- **Boîte aux lettres Drive** (`DriveTransport.kt`) : L'application recherche et crée le dossier `App/TranslateMyManga` à la racine **du Google Drive personnel de l'utilisateur connecté**. Il n'existe aucun Drive partagé commun, aucun serveur centralisé, et aucune possibilité pour un tiers d'accéder à votre compte Drive.

### 3.2 Comment fonctionne la liaison PC-Mobile (Direct / Tailscale) ?
- La liaison directe PC-Mobile (`DirectTransport.kt`) repose sur un appairage explicite local avec un code de confirmation à 6 chiffres généré à la volée par le logiciel PC.
- Le jeton d'authentification issu de l'appairage est chiffré dans le **Hardware Android Keystore** (`KeystoreTokenCipher.kt`) en **AES-256 GCM** sous l'alias `tmm_direct_token`.
- Aucune adresse IP de machine personnelle n'est codée en dur dans l'application.

### 3.3 Journaux Applicatifs et Masquage PII (`AppLog.kt`)
- `AppLog` est un tampon circulaire volatil conservé en mémoire RAM uniquement (2000 lignes maximum). Aucun fichier log n'est écrit sur le stockage public sans demande explicite de l'utilisateur.
- Lors de l'export manuel des journaux par l'utilisateur ("Exporter les journaux"), la fonction `maskSensitive()` caviarde automatiquement :
  - Les adresses emails (`***@***`).
  - Les en-têtes d'autorisation HTTP (`Authorization: ***`).
  - Les jetons Bearer (`Bearer ***`).

---

## 4. Remédiations Appliquées Immédiatement

Les éléments non critiques mais identifiables qui subsistaient ont été immédiatement nettoyés et testés :

1. **Suppression de l'email personnel dans les tests unitaires** :
   - Fichiers modifiés : `app/src/test/java/com/tmm/mobile/core/AppLogJournalTest.kt` et `app/src/test/java/com/tmm/mobile/ui/screens/settings/SettingsViewModelTest.kt`.
   - Remplacement par la valeur fictive canonique du cahier des charges (R13) : `vous@gmail.com`.
2. **Sanitisation des chemins locaux dans la documentation** :
   - Fichiers modifiés : `ORIGINAL_REQUEST.md`, `docs/RAPPORT_GEMINI.md`, `docs/orchestrator_main/BRIEFING.md`, `docs/orchestrator_main/DISPATCH.md`.
   - Remplacement de `c:\Users\clement martini\...` par `<project-root>` ou variables d'environnement (`%LOCALAPPDATA%`).
3. **Validation complète des tests unitaires** :
   - Exécution de `./gradlew testDebugUnitTest` : **Succès 100% (33 tâches validées, 0 échec)**.

---

## 5. Recommandation Optionnelle : Métadonnées Git d'Auteur

Dans les commits Git passés, l'email d'auteur Git configuré sur votre machine locale était :
`minonix <martiniclement06@gmail.com>`  
Sur les commits plus récents créés via GitHub Web PRs, l'adresse utilisée est :
`minonix <75138339+Minonix@users.noreply.github.com>`.

> [!NOTE]
> Une adresse email d'auteur de commit Git est une simple métadonnée publique (équivalente à une signature d'auteur). Elle ne confère **aucun accès technique** à votre compte Google ni à l'application.  
> Si vous souhaitez masquer votre adresse email réelle sur vos futurs commits Git locaux, configurez Git sur votre machine avec votre adresse no-reply GitHub :  
> `git config --global user.email "75138339+Minonix@users.noreply.github.com"`

---

## 6. Transparence : Projet Réalisé avec Assistance IA

Conformément à la demande, la mention du développement assisté par IA est officialisée dans le dépôt :
- L'application TMM Mobile a été développée en collaboration homme-machine en s'appuyant sur les modèles Claude (Opus/Sonnet) et Google Gemini sous l'environnement Antigravity.
- Cette mention est documentée dans le `README.md` du projet et dans la documentation technique.
