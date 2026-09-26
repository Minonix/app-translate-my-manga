# Cahier des charges : Translate My Manga Mobile (TMM-Mobile)

Version 1.1 du 26 septembre 2026. Statut : validé.

Changements de la v1.1 : maquettes définitives (13 maquettes, dossier `maquettes/`), onglet « Synchro » renommé « Transferts », sélection multiple par appui long (9.12), vérification et retraduction (chapitre, sélection, série, page), statistiques par chapitre, jusqu'à 4 chapitres traduits en parallèle sur le PC, réglages de traduction du PC modifiables depuis le téléphone sans redémarrage (7.5).

Ce document décrit le but final de l'application Android compagnon de Translate My Manga Win (TMM-Win), découpé en V1 (à construire maintenant), V2 (prochaine étape) et V3 (pistes). Tout ce qui n'est pas marqué V1 est hors périmètre de la première version.

## Sommaire

1. Contexte et objectifs
2. Périmètre par version
3. Contraintes techniques qui conditionnent tout
4. Architecture générale
5. Stack technique Android
6. Modèle de données
7. Contrat d'échange Google Drive (protocole v1)
8. Intégration Mihon
9. Écrans
10. Charte graphique
11. Exigences non fonctionnelles
12. V2 détaillée
13. V3 : pistes
14. Risques et pièges
15. Critères d'acceptation V1
16. Jalons de réalisation V1
17. Décisions complémentaires

---

## 1. Contexte et objectifs

### 1.1 Les deux applications

| | TMM-Win | TMM-Mobile |
|---|---|---|
| Plateforme | Windows (Tauri v2, React, FastAPI Python) | Android (Kotlin, Jetpack Compose) |
| Rôle | Traduire : OCR, traduction, inpainting, lettrage, optimisation sans perte du CBZ | Envoyer, suivre, récupérer ; consulter la bibliothèque Mihon |
| Dépôt | `translate-my-manga-win` | `app-translate-my-manga` |

Les deux applications ont des bases de code **totalement disjointes**. Leur seul point de contact est le **contrat d'échange** (section 7). Aucune ne suppose quoi que ce soit du code de l'autre ; tout passe par des fichiers au format versionné.

### 1.2 Utilisateurs

- V1 : un seul utilisateur (le propriétaire), un téléphone, un PC.
- Plus tard, éventuellement un cercle d'amis. Aucune publication sur un store : un APK signé est distribué à la main.

### 1.3 Objectifs V1

1. Depuis le téléphone, envoyer au PC un ou plusieurs chapitres téléchargés dans Mihon, **même si le PC est éteint**.
2. Suivre l'état des traductions (en attente, en cours, terminées, échecs).
3. Récupérer les chapitres traduits directement dans `Mihon/local/`, lisibles aussitôt dans Mihon.
4. Consulter la bibliothèque Mihon complète (y compris les séries non téléchargées), rangée par catégories, sans export manuel.
5. Marquer certaines séries en « traduction automatique » : tout nouveau chapitre téléchargé dans Mihon part seul en traduction.

---

## 2. Périmètre par version

| Fonction | V1 | V2 | V3 |
|---|---|---|---|
| Transport Google Drive (boîte aux lettres) | Oui | Conservé en secours | |
| Connexion directe au PC (Tailscale ou réseau local) | | Oui, prioritaire quand le PC est en ligne | |
| Lecture de la bibliothèque Mihon via la sauvegarde automatique | Oui (lecture seule) | | |
| Édition des catégories puis export `.tachibk` | | Oui | |
| Chapitres traduits dans `Mihon/local/<série>` | Oui | Conservé en repli | |
| Chapitres traduits fusionnés dans la série d'origine (scanlator « Translate My Manga FR ») | | Oui, après validation technique | |
| Traduction auto des nouveaux chapitres **téléchargés** (par série) | Oui, au lancement et au scan | En arrière-plan | |
| Traduction auto des nouveaux chapitres **disponibles** (non téléchargés) | | Oui (voir 12.5) | |
| Rafraîchissement de l'état | Bouton manuel anti-spam | Toutes les 15 min + notifications | |
| Annuler / relancer une traduction | Oui | | |
| Vérifier ou retraduire un chapitre, une sélection ou une série entière | Oui | | |
| Vérification ciblée d'une page signalée | Oui | | |
| Statistiques et alertes par chapitre (bulles, SFX, pages signalées) | Oui | | |
| Suivi de plusieurs traductions simultanées (jusqu'à 4) | Oui | | |
| Réglages de traduction du PC depuis le téléphone, appliqués sans redémarrage | Oui | | |
| Sélection multiple par appui long (séries et chapitres) | Oui | | |
| Pause globale de la file, édition du glossaire depuis le téléphone | | Oui | |
| Option « Wi-Fi uniquement » | Oui (désactivée par défaut) | | |
| Nettoyage automatique du Drive | Option (désactivée par défaut) | | |
| Mode sombre | | Oui | |
| Regroupement de séries découpées en tomes | | | Oui |
| Multi-utilisateurs (amis) | | | Oui |

---

## 3. Contraintes techniques qui conditionnent tout

1. **Pas de web app.** Un navigateur Android ne peut ni lire `Mihon/downloads`, ni écrire dans `Mihon/local`, ni travailler en arrière-plan de façon fiable. D'où une app native.
2. **Pas d'accès direct à Mihon.** Sa base de données est privée et il n'expose aucune API. Les seules données accessibles sont les fichiers du dossier de stockage Mihon : `downloads/`, `local/` et `autobackup/`. La bibliothèque « quasi en direct » repose donc sur la **sauvegarde automatique de Mihon**, réglée à la fréquence la plus courte proposée.
3. **Format de sauvegarde `.tachibk`** : protobuf compressé en gzip. Le schéma est celui de Mihon (licence Apache 2.0) ; ses modèles peuvent être repris tels quels. Les forks (SY, J2K...) ajoutent des champs : il faut les tolérer.
4. **Une restauration Mihon fusionne, elle ne remplace pas.** Toute modification (V2) ne s'applique qu'après une restauration manuelle dans Mihon.
5. **Accès au stockage :** le Storage Access Framework (SAF), avec une permission persistante sur le dossier racine de Mihon. Ça marche sans permission sensible, y compris chez des amis.
6. **Côté PC, le backend TMM-Win n'écoute que sur `127.0.0.1`** (règle testée). La V1 n'y touche pas : Drive ne demande aucune ouverture réseau. La V2 (connexion directe) devra ajouter une écoute dédiée et authentifiée (section 12.1).
7. **OAuth Google :** une application en statut « Testing » voit ses jetons expirer après 7 jours. Le projet Google Cloud doit être passé en « In production », éventuellement non vérifié, ce qui reste acceptable pour un usage personnel.

---

## 4. Architecture générale

### 4.1 Vue d'ensemble V1

```
 Téléphone                                              PC
+----------------------+                         +---------------------+
| Mihon                |                         | TMM-Win             |
|  downloads/  --------+--+                      |  pont Drive         |
|  local/     <--------+--|--+                   |  (module TMM-Win,   |
|  autobackup/ --------+--|--|--+                |   hors de ce dépôt) |
+----------------------+  |  |  |                +----------^----------+
                          v  |  v                           |
                   +---------+-----------+   Google Drive   |
                   | TMM-Mobile          |<---------------->+
                   |  Room (cache local) |   TranslateMyManga/
                   |  WorkManager        |     jobs/<jobId>/...
                   +---------------------+     pc/heartbeat.json
                                               pc/index.json
```

### 4.2 Flux V1

1. **Lecture de la bibliothèque :** Mihon écrit périodiquement un `.tachibk` dans `autobackup/`. À l'ouverture et au scan, TMM-Mobile lit le plus récent, s'il a changé, et remplit son cache Room.
2. **Scan des téléchargements :** TMM-Mobile parcourt `downloads/<source>/<série>/<chapitre>` et rapproche chaque chapitre du catalogue.
3. **Envoi :** pour chaque chapitre choisi : création d'un `jobId`, CBZ préparé (un dossier d'images est zippé sans recompression), envoi dans `jobs/<jobId>/source.cbz`, puis `request.json` écrit **en dernier**.
4. **Traduction :** TMM-Win repère le nouveau `request.json`, télécharge, met en file, traduit et optimise, en mettant à jour `status.json`. À la fin, il dépose `result.cbz` puis `result.json`, **en dernier**.
5. **Récupération :** au rafraîchissement, TMM-Mobile voit `result.json`, télécharge `result.cbz`, vérifie le SHA-256, l'installe dans `Mihon/local/<dossier>/`, puis écrit `received.json`.

### 4.3 Prévision V2 (à respecter dès la V1)

La couche transport est une interface `JobTransport` avec deux implémentations : `DriveTransport` (V1) et `DirectTransport` (V2). Le reste de l'app ne manipule que des jobs (`jobId`, requête, statut, résultat). Un job garde le même `jobId` quel que soit le canal, ce qui permet au PC de dédoublonner s'il le reçoit par les deux.

---

## 5. Stack technique Android

| Besoin | Choix | Raison |
|---|---|---|
| Langage | Kotlin | Même langage que Mihon, reprise directe de ses modèles de sauvegarde |
| UI | Jetpack Compose + Material 3 (thème personnalisé) | Standard actuel |
| Navigation | Navigation Compose | |
| Base locale | Room | Cache du catalogue, jobs, réglages par série |
| Tâches longues | WorkManager (tâches expédiées ou au premier plan pour les transferts) | Les transferts survivent à la fermeture de l'app |
| Sérialisation | kotlinx.serialization (JSON + protobuf) | JSON pour le protocole, protobuf pour `.tachibk` |
| HTTP | OkHttp, appels REST Drive v3 | Pas de SDK lourd ; envois reprenables |
| Authentification Google | Google Identity Services (autorisation avec scope Drive) | API exacte à confirmer dans la doc au jalon 0 |
| Images | Coil | Couvertures |
| Réglages | DataStore | |
| Injection | Manuelle (un `AppContainer`) | Pas de Hilt pour une app de cette taille |
| Min SDK | 26 (Android 8) | |

Organisation du code : un seul module `app`, découpé en paquets par fonctionnalité (`catalog`, `mihon`, `jobs`, `transport`, `ui/<écran>`). Chaque écran a un ViewModel qui expose un `StateFlow`.

---

## 6. Modèle de données

### 6.1 Identités

| Clé | Composition | Usage |
|---|---|---|
| **Clé série** | `sourceId` + `url` (issus de la sauvegarde) | Identité unique d'une série. **Jamais le titre** (doublons et titres changeants). |
| **Clé chapitre** | clé série + `url` du chapitre | Identité d'un chapitre du catalogue |
| **Chemin Mihon** | `(dossier source, dossier série, dossier chapitre)` sous `downloads/` | Seule identité connue des deux applications ; sert au rapprochement avec le PC |
| **jobId** | UUID v4 créé par le téléphone | Identité d'une demande de traduction, stable sur tous les canaux |

Le regroupement de séries (V3) sera une couche **au-dessus** des clés séries : un groupe liste plusieurs clés séries et ne modifie jamais Mihon. La V1 n'a rien à prévoir de plus.

### 6.2 Tables Room (V1)

- `series` : clé série, titre, auteur, statut, `thumbnailUrl`, nom de la source, `dateAdded`.
- `category` : id, nom, ordre (issus de la sauvegarde).
- `series_category` : lien plusieurs-à-plusieurs.
- `chapter` : clé chapitre, clé série, nom, numéro (flottant), scanlator, lu ou non, date d'envoi par la source.
- `downloaded_chapter` : chemin Mihon, URI SAF, taille, date de modification, clé chapitre rapprochée (nullable).
- `local_fr_chapter` : dossier de `Mihon/local`, nom du fichier, jobId d'origine (nullable).
- `job` : jobId, mode (`translate`, `verify`, `retranslate`), pages ciblées (nullable), chemin Mihon, clé chapitre (nullable), état côté téléphone, dernier `status.json` reçu, dates, erreur.
- `chapter_quality` : chemin Mihon, pages, bulles traduites, bulles non traduites, SFX gardés, date de vérification (nullable), alertes (JSON), jobId d'origine. Remplacé à chaque `result.json` reçu pour ce chapitre.
- `series_settings` : clé série, traduction auto (booléen), langue cible.
- `ignored_gap` : clé série, numéro de chapitre ignoré.
- `backup_import` : nom, taille, date de modification du dernier `.tachibk` lu (pour éviter de le relire s'il n'a pas changé).

Hors Room (DataStore) : dernier `pc/settings.json` reçu, commande de réglages en attente, préférences de l'app.

### 6.3 État d'un chapitre (dérivé, jamais stocké)

| Statut affiché | Condition |
|---|---|
| **À envoyer au PC** | Téléchargé dans Mihon, absent de `pc/index.json`, aucun job |
| **En file / En traduction (x %)** | Job actif |
| **Échec** | Job en échec |
| **À récupérer** | Résultat disponible (`result.json`, ou `fr: true` dans l'index) et absent de `Mihon/local` |
| **À jour** | Présent dans `Mihon/local` |
| **Non traduit** | Original présent sur le PC, ni FR ni job |
| **Dans Mihon, non téléchargé** | Présent dans le catalogue uniquement |
| **Manquant partout** | Numéro absent entre le plus petit et le plus grand chapitre présent localement, et non ignoré |

### 6.4 États d'un job

Côté téléphone : `preparing` -> `uploading` -> `sent` -> (états du PC) -> `result_ready` -> `downloading` -> `installed`. Plus `failed` (avec l'étape en cause) et `cancel_requested`.

Côté PC (lus dans `status.json`), alignés sur les `jobState` de TMM-Win : `queued`, `fetching`, `ready`, `translating`, `done`, `failed`, `cancelled`.

Modes d'un job (affichés en badge dans la File et l'Accueil) :

| Mode | Badge | Effet sur le PC | Source |
|---|---|---|---|
| `translate` | Traduction | Traduction complète | `source.cbz` envoyé par le téléphone |
| `verify` | Vérification | Revérifie le chapitre traduit, corrige ce qui est réparable, recalcule les statistiques. `pages` non nul = vérification ciblée de ces pages | Copie déjà présente sur le PC |
| `retranslate` | Retraduction | Refait la traduction depuis l'original conservé sur le PC | Copie déjà présente sur le PC |

`verify` et `retranslate` ne sont proposés que pour des chapitres dont l'original est sur le PC (`original: true` dans l'index). Le résultat remplace le chapitre FR déjà installé dans `Mihon/local`.

---

## 7. Contrat d'échange Google Drive (protocole v1)

Ce contrat est **la seule interface** entre les deux applications. Toute évolution incrémente `protocol`.

### 7.1 Arborescence

```
TranslateMyManga/                  (créé par le premier des deux clients)
  protocol.json                    {"protocol": 1}
  jobs/
    <jobId>/
      source.cbz                   téléphone (mode translate uniquement)
      request.json                 téléphone, écrit EN DERNIER
      status.json                  PC, réécrit pendant le traitement
      cancel.json                  téléphone, optionnel
      result.cbz                   PC (absent si rien n'a changé, voir result.json)
      result.json                  PC, écrit EN DERNIER
      received.json                téléphone, après installation
  pc/
    heartbeat.json                 PC, toutes les 60 s tant qu'il tourne
    index.json                     PC, après chaque changement
    settings.json                  PC, réglages de traduction en vigueur
    commands/
      <cmdId>.json                 téléphone, demande de changement de réglages
```

### 7.2 Schémas

`request.json`
```json
{
  "protocol": 1,
  "jobId": "7f1c...",
  "createdAt": "2026-09-26T10:12:00Z",
  "device": "Pixel 8",
  "series": { "title": "Blue Lock", "sourceName": "MangaDex (EN)", "sourceId": "2499283573021220255", "url": "/manga/..." },
  "chapter": { "name": "Chapter 214", "number": 214.0, "scanlator": "...", "url": "/chapter/..." },
  "mihonPath": { "source": "MangaDex (EN)", "series": "Blue Lock", "chapter": "Team_Chapter 214" },
  "file": { "name": "source.cbz", "size": 18234567, "sha256": "..." },
  "options": { "mode": "translate", "pages": null, "translate": true, "sourceLang": "auto", "targetLang": "FR" }
}
```
`mode` ∈ `translate`, `verify`, `retranslate` (section 6.4). `pages` : `null` (tout le chapitre) ou liste de numéros de pages **en base 0**, uniquement avec `verify`. En `verify` et `retranslate`, `file` vaut `null` et aucun `source.cbz` n'est envoyé : le PC retrouve sa copie par `mihonPath` et échoue avec le code `source_missing` s'il ne l'a pas. `translate: false` (envoi sans traduction) n'a de sens qu'en mode `translate`.

`sourceId` est une chaîne (identifiant 64 bits). `series` et `chapter` peuvent avoir `sourceId`/`url` à `null` si le rapprochement avec le catalogue a échoué ; `mihonPath` est toujours présent.

`status.json`
```json
{
  "protocol": 1, "jobId": "7f1c...", "updatedAt": "...",
  "mode": "translate",
  "state": "translating",
  "stage": "lettering",
  "page": 14, "pages": 22,
  "engine": "Gemini",
  "queuePosition": null,
  "error": null
}
```
`stage` ∈ `fetch`, `ocr`, `translate`, `lettering`, `verify` (les cinq étapes de la maquette File). Un job `verify` passe directement à `verify`. `error` = `{ "code": "...", "message": "...", "page": 9 }` ; `page` est en base 1 (numéro affiché), ou `null`.

`result.json`
```json
{
  "protocol": 1, "jobId": "7f1c...", "finishedAt": "...", "mode": "translate",
  "file": { "name": "result.cbz", "size": 15234567, "sha256": "..." },
  "localFolderName": "Blue Lock",
  "chapterFileName": "Chapter 214.cbz",
  "pages": 22, "engine": "Gemini",
  "quality": {
    "totalPages": 22,
    "translatedBubbles": 146,
    "untranslatedBubbles": 2,
    "sfxKept": 31,
    "verifiedAt": "2026-09-26T10:40:00Z",
    "alerts": [ { "page": 8, "kind": "overflow", "label": "Texte débordant de sa bulle (1 bulle(s))", "repairable": true } ]
  }
}
```
`localFolderName` et `chapterFileName` sont décidés par le PC, avec la même convention que son export ADB, pour qu'un chapitre arrivé par câble ou par Drive tombe au même endroit.

`file` vaut `null` si le job n'a produit aucun nouveau fichier (vérification sans correction) : le téléphone met seulement à jour les statistiques. `quality` reprend la sortie de `chapter_quality_report` de TMM-Win (`core-engine/comics/tmm_metadata.py`), complétée de `untranslatedBubbles` ; `quality` vaut `null` si le chapitre n'a pas de métadonnées. Dans `alerts`, `page` est en base 0 (le téléphone affiche `page + 1`) et `kind` ∈ `source_copy`, `source_text`, `overflow`, `too_small`.

`cancel.json` : `{ "jobId": "...", "requestedAt": "..." }`.
`received.json` : `{ "jobId": "...", "receivedAt": "...", "installedPath": "local/Blue Lock/Chapter 214.cbz" }`.

`pc/heartbeat.json` : `{ "protocol": 1, "pcName": "PC-BUREAU", "appVersion": "2.4.1", "updatedAt": "...", "slots": 4, "activeJobIds": ["7f1c...", "..."], "queue": { "active": 4, "waiting": 7, "failed": 1 } }`. `slots` = valeur en vigueur de `chapterConcurrency` (1 à 4) ; `activeJobIds` = jobs en cours de traitement, au plus `slots`.

`pc/index.json` : `{ "protocol": 1, "updatedAt": "...", "chapters": [ { "mihonPath": {...}, "original": true, "fr": true } ] }`. Seuls les chapitres issus de Mihon y figurent (arrivés par ADB ou par Drive).

### 7.3 Règles

1. **Marqueur de fin d'écriture :** un job n'existe pour le PC que lorsque `request.json` est présent, et un résultat n'existe pour le téléphone que lorsque `result.json` est présent. Les deux sont écrits après le fichier lourd, ce qui évite de traiter un fichier incomplet.
2. **Intégrité :** SHA-256 vérifié à la réception, dans les deux sens. En cas d'écart : nouvel essai, puis échec explicite.
3. **Idempotence :** le PC ignore un `jobId` déjà connu. Le téléphone n'installe pas deux fois le même `jobId`.
4. **Dédoublonnage :** avant de créer un job, le téléphone vérifie qu'aucun job actif ni aucun chapitre de l'index ne porte le même `mihonPath`.
5. **Fréquence d'écriture PC :** `status.json` à chaque changement d'étape, au plus toutes les 20 s par job. `heartbeat.json` toutes les 60 s.
6. **PC « en ligne »** si `heartbeat.updatedAt` date de moins de 5 min.
7. **Rafraîchissement économique :** une seule requête Drive liste les fichiers modifiés depuis le dernier rafraîchissement (`modifiedTime > T`), au lieu de parcourir chaque dossier de job.
8. **Envois reprenables** (upload resumable Drive) : une coupure réseau reprend là où elle s'est arrêtée.
9. **Scope OAuth :** `drive.file`. Les clients OAuth Android et Windows appartiennent au **même projet Google Cloud**. Le fait que chaque client voie les fichiers créés par l'autre est à valider au jalon 0 ; repli : scope `drive` complet, en application non vérifiée.
10. **Conservation :** par défaut, rien n'est supprimé (5 To disponibles). Options : « Après 30 jours » (dossier du job supprimé 30 jours après `received.json`) ou « Après récupération » (supprimé dès `received.json`).
11. **Parallélisme :** le PC traite jusqu'à `chapterConcurrency` jobs à la fois (1 à 4). Le téléphone lit un `status.json` par job actif ; l'ordre de la file reste celui du PC (`queuePosition`).
12. **Vérification et retraduction groupées :** une action sur N chapitres (sélection ou série entière) crée N jobs, un par chapitre, jamais un job groupé.

### 7.4 Ce que TMM-Win doit implémenter (hors de ce dépôt)

Un module « pont Drive » dans TMM-Win qui :
- interroge Drive périodiquement pour trouver les nouveaux `request.json`, `cancel.json` et `pc/commands/*.json` ;
- en mode `translate`, télécharge `source.cbz` et crée un job dans sa file durable existante, avec source locale ;
- en modes `verify` et `retranslate`, crée le job correspondant de sa propre file (vérification complète ou ciblée par pages, retraduction depuis l'original) sur sa copie du chapitre ;
- traite jusqu'à `chapterConcurrency` jobs en parallèle ;
- recopie les changements d'état de chaque job dans son `status.json` ;
- à la fin, dépose `result.cbz` (le CBZ optimisé sans perte) puis `result.json` avec `quality` ;
- applique les commandes de réglages à chaud (section 7.5) et publie `pc/settings.json` ;
- tient à jour `pc/heartbeat.json` et `pc/index.json`.

Ce module fera l'objet d'un cahier des charges séparé, dans le dépôt TMM-Win.

### 7.5 Réglages de traduction du PC depuis le téléphone

Le téléphone lit et modifie les réglages de traduction de TMM-Win. Les clés d'API et le dossier de sortie ne transitent jamais.

`pc/settings.json` (écrit par le PC au démarrage et après chaque changement, quelle qu'en soit l'origine) :
```json
{
  "protocol": 1, "updatedAt": "...", "version": 12, "lastCommandId": "c0a1...",
  "values": {
    "sourceLanguage": "auto", "targetLanguage": "FRA", "translationProvider": "gemini",
    "autoFailoverToLocalLlm": true, "autoVerifyAfterTranslate": true,
    "chapterConcurrency": 4, "concurrency": 2, "pileSize": 3, "useGpu": true,
    "detector": "default", "preferredOcr": "auto", "inpainter": "lama_large",
    "inpaintingSize": 1280, "font": "Anime Ace"
  }
}
```

`pc/commands/<cmdId>.json` (écrit par le téléphone) :
```json
{ "protocol": 1, "cmdId": "c0a1...", "createdAt": "...", "type": "settings", "baseVersion": 12, "changes": { "chapterConcurrency": 2 } }
```

Valeurs autorisées (liste fermée, identique à `GlobalSettings` de TMM-Win) :

| Clé | Valeurs |
|---|---|
| `sourceLanguage` | `auto`, `JPN`, `KOR`, `CHS` |
| `targetLanguage` | `FRA`, `ENG`, `SPA`, `DEU` |
| `translationProvider` | `gemini`, `openai`, `claude`, `deepl`, `groq`, `openrouter`, `tokenhub`, `offline`, `local_llm` |
| `detector` | `default`, `ctd`, `paddle` |
| `preferredOcr` | `auto`, `mocr`, `48px`, `32px` |
| `inpainter` | `lama_large`, `lama_mpe`, `sd`, `none` |
| `inpaintingSize` | `1024`, `1280`, `1800`, `2048` |
| `font` | `Anime Ace`, `Manga Temple` |
| `chapterConcurrency`, `concurrency` | 1 à 4 |
| `pileSize` | 1 à 3 |
| `useGpu`, `autoVerifyAfterTranslate`, `autoFailoverToLocalLlm` | booléen |

Règles :
1. Une commande ne contient que les clés modifiées. Le PC ignore une clé inconnue ou une valeur hors liste et ne l'applique pas.
2. Application **à chaud** : les nouveaux réglages valent pour les pages suivantes, sans redémarrage ni interruption des chapitres en cours. Une baisse de `chapterConcurrency` laisse finir les jobs en cours et ne démarre pas de nouveau job tant que le nombre actif dépasse la nouvelle valeur.
3. Après application, le PC supprime la commande, incrémente `version`, renseigne `lastCommandId` et réécrit `settings.json`. Le dernier écrit l'emporte, sans fusion.
4. Le téléphone considère la commande appliquée quand `settings.json` porte `lastCommandId == cmdId`. Tant que l'écran « Traduction sur le PC » est ouvert, il relit `settings.json` toutes les 5 s pendant 60 s au plus après un envoi.

---

## 8. Intégration Mihon

### 8.1 Accès

À l'onboarding, l'utilisateur sélectionne le **dossier de stockage de Mihon** (celui qui contient `downloads/`, `local/` et `autobackup/`) via le sélecteur de dossier Android. La permission est rendue persistante. La structure exacte est à vérifier sur le téléphone au jalon 0.

### 8.2 Lecture des téléchargements

- Parcours `downloads/<source>/<série>/<chapitre>` : dossier d'images ou `.cbz`.
- Utiliser des requêtes `DocumentsContract` directes plutôt que `DocumentFile` : ce dernier fait un aller-retour système par appel et devient très lent sur des centaines de chapitres.
- Cache : ne pas redescendre dans un dossier série dont la date de modification n'a pas changé.
- Un dossier de chapitre contenant un fichier temporaire de téléchargement Mihon est considéré comme incomplet et ignoré.

### 8.3 Lecture de la sauvegarde

- Source automatique : le fichier le plus récent de `autobackup/`.
- Source manuelle : bouton « Importer » (sélecteur de fichier), pour un `.tachibk` exporté à la main.
- Pas de nouvelle lecture si nom, taille et date sont identiques à la dernière importation.
- Lecture : gunzip puis décodage protobuf avec les modèles de Mihon, en tolérant les champs inconnus.
- Le cache Room est remplacé en une seule transaction : pas d'état à moitié importé.

### 8.4 Écriture dans `Mihon/local`

- Destination : `local/<localFolderName>/<chapterFileName>` (fournis par `result.json`).
- Écriture sous un nom temporaire, vérification du SHA-256, puis renommage. Un échec ne laisse jamais de `.cbz` partiel visible par Mihon.
- Le CBZ produit par TMM-Win contient déjà `ComicInfo.xml`.

### 8.5 Rapprochement téléchargements / catalogue

Les noms de dossiers Mihon sont nettoyés (caractères interdits remplacés) et le dossier chapitre combine scanlator et nom du chapitre. Méthode :
1. Source : nom du dossier source comparé au nom de source de la sauvegarde.
2. Série : titre nettoyé de la même façon que Mihon, comparé au dossier série.
3. Chapitre : `<scanlator>_<nom>` nettoyé, sinon le nom seul, comparé au dossier chapitre.
4. En cas d'échec, le chapitre reste utilisable (envoi possible via `mihonPath`), signalé « hors catalogue ».

La règle de nettoyage exacte doit être lue dans le code source de Mihon au jalon 2, pas devinée.

---

## 9. Écrans

Les maquettes `maquettes/*.dc.html` sont la référence visuelle et comportementale (textes, tailles, couleurs, animations, états). Mode d'emploi : `maquettes/README.md`. Captures : `maquettes/captures/`. Ce chapitre résume ; en cas d'écart, la maquette l'emporte, sauf pour les règles de 9.12 qu'elle ne peut pas montrer.

### 9.1 Nombre de pages et navigation

**V1 : 10 écrans, 1 feuille modale, 3 états transverses.**

| # | Écran | Maquette | Accès |
|---|---|---|---|
| 1 | Onboarding (3 étapes) | `Onboarding.dc.html` | Premier lancement, Réglages |
| 2 | Accueil | `Main.dc.html` | Onglet 1 |
| 3 | Bibliothèque | `Library.dc.html` | Onglet 2 |
| 4 | Fiche série | `Series.dc.html` | Bibliothèque, Accueil, Transferts |
| 5 | Transferts | `Sync.dc.html` | Onglet 3 (pastille = à envoyer + à récupérer) |
| 6 | File | `Queue.dc.html` | Onglet 4 |
| 7 | Catalogue | `Catalog.dc.html` | Onglet 5 |
| 8 | Réglages | `Settings.dc.html` | Icône d'en-tête et pastille PC de l'Accueil |
| 9 | Traduction sur le PC | `TranslationSettings.dc.html` | Réglages, lien « Régler » de la File |
| 10 | Permission Mihon perdue | `Permission.dc.html` | Bloquant, au lancement si l'accès SAF est perdu |
| F | Feuille « Nouvelle traduction » | `NewTranslation.dc.html` | Tuile « Nouvelle traduction » (Accueil), File |
| E1 | État vide | `Empty.dc.html` | Accueil sans téléchargement ni sauvegarde |
| E2 | Hors ligne / jeton expiré | `Offline.dc.html` (propriété `cause`) | Bandeau en tête de tout écran à onglet |

Archives v0 (non implémentées en V1) : `maquettes/v0/`. V2 ajoute : Appairage direct (`v0/Pairing.html`), éditeur de catégories, Profil et glossaire de série. V3 : Regroupements.

Barre de navigation basse, 5 onglets : Accueil, Bibliothèque, Transferts, File, Catalogue.

### 9.2 Onboarding

Barre de progression en 3 segments et compteur « x/3 ».
1. **Relier le téléphone et le PC :** « Se connecter avec Google » (bouton `info`), puis carte du compte et « Dossier TranslateMyManga/ trouvé · PC vu il y a … ». Création du dossier s'il manque.
2. **Où est le dossier Mihon ? :** sélecteur de dossier Android, puis contrôle animé de `downloads/`, `local/`, `autobackup/` (coche verte par dossier trouvé, message clair sinon).
3. **Votre bibliothèque :** lecture immédiate de la dernière sauvegarde, compteurs animés séries, catégories, téléchargées ; message « N chapitres téléchargés dans Mihon ne sont pas encore sur le PC » ; bouton « Ouvrir TMM Mobile ».

« Étape précédente » disponible aux étapes 2 et 3. Chaque étape est reprenable depuis Réglages.

### 9.3 Accueil (`Main.dc.html`)

- En-tête : logo 訳, « TMM Mobile », pastille d'état PC (en ligne, vu il y a…, hors ligne), icône Réglages.
- Titre « Accueil », « Vérifié il y a … », bouton Rafraîchir avec anneau de délai de 30 s (section 11.3).
- Carte sombre « À faire · Mihon ↔ PC » : compteurs animés « à envoyer » et « à récupérer », boutons « Envoyer » et « Récupérer » avec progression.
- « Sur le PC en ce moment » : jusqu'à 4 jobs actifs (badge de mode, série et chapitre, mini-barre), lien « File (N) ».
- Deux tuiles : « Nouvelle traduction » (feuille F), « Scanner Mihon ».
- Rail « Derniers chapitres traduits ».
- Toast de confirmation avec action « Voir ».

### 9.4 Bibliothèque (`Library.dc.html`)

- Titre, sous-titre « N séries · N catégories Mihon » (en sélection : « N séries sélectionnées »), bouton « Sélectionner » / « Terminer », recherche.
- Puces de catégories Mihon avec compteur ; filtre Toutes / À traduire / Non téléchargées.
- Grille de 3 colonnes : couverture, badge d'état (`send` à envoyer, `busy` en cours, `get` à récupérer, `absent` non téléchargée, `ok` à jour), titre, barre « FR x/y ».
- **Sélection multiple (règles en 9.12)** : tuiles avec case à cocher, anneau `accent` et réduction à 96 % quand cochées ; bandeau sombre « Touchez les séries à inclure » avec « Tout cocher (N) » / « Tout décocher » ; barre d'actions 2 × 2 qui additionne les chapitres concernés des séries cochées : **Envoyer au PC** (principal), **Récupérer**, **Vérifier**, **Retraduire**.
- « Retraduire » demande confirmation (boîte de dialogue avec une note bleue qui suggère « Vérifier », moins coûteux).
- Après une action : toast « … · Voir la file ».

### 9.5 Fiche série (`Series.dc.html`)

- Retour, titre, catégorie, sens de traduction, interrupteur **Traduction automatique**.
- Feuille « Toute la série » : 4 actions avec compteurs (envoyer, récupérer, vérifier, retraduire).
- Trois compteurs : Téléphone, PC original, Traduits.
- Alerte de trou avec « Ignorer ».
- Liste des chapitres : case à cocher, numéro, trois indicateurs (téléphone, PC, FR), statut (6.3). Bouton de statistiques dépliable : pages, bulles traduites, non traduites, SFX gardés, « Vérifié le … » ou « Jamais vérifié ». Lignes verrouillées (job en cours, trou) non cochables.
- Raccourci « Tout ce qui reste à faire » ; barre d'actions 2 × 2 avec compteurs, « N chapitres sélectionnés », « Désélectionner » ; confirmation avant retraduction.

### 9.6 Transferts (`Sync.dc.html`)

- Schéma animé Téléphone ↔ Google Drive ↔ PC-BUREAU avec compteurs.
- « En cours maintenant » : transferts actifs (envois et téléchargements) avec progression.
- Bloc « À envoyer au PC » : aperçu par série, case « Traduire dès l'arrivée sur le PC » (cochée par défaut ; décochée, le PC garde l'original sans le traduire), bouton d'envoi.
- Bloc « À récupérer » : aperçu par série, bouton de téléchargement vers `Mihon/local/<série>`.
- Carte de scan `Mihon/downloads` avec « Rescanner ».
- Lien « N chapitres manquants partout ».

### 9.7 File (`Queue.dc.html`)

- Onglets Actives / Terminées / Échecs avec compteurs.
- Bandeau d'emplacements « N/4 en parallèle sur PC-BUREAU » et lien « Régler » (écran 9).
- Carte sombre du job suivi : badge de mode, titre, « Page x sur y · moteur », pourcentage, frise des 5 étapes (Récup., OCR, Trad., Lettrage, Vérif.). Les autres jobs actifs sont listés dessous ; un appui les met en avant.
- « Ensuite (N) » : position (à partir de 05), icône d'origine, bouton ✕ « Retirer de la file » (écrit `cancel.json`).
- Terminées : puces de statistiques et alertes par page, bouton « Vérifier la page » (job `verify` ciblé) qui passe à « Dans la file ».
- Échecs : message du PC, « Réessayer à partir de la page N » (nouveau job).
- Jobs en cours d'envoi depuis le téléphone : en tête, avec la progression de l'envoi.

### 9.8 Catalogue (`Catalog.dc.html`)

- Carte de la sauvegarde lue : nom du fichier, « Importée … · depuis Mihon/autobackup », bouton « Relire la dernière sauvegarde » avec progression par étapes, bouton « Importer » (manuel).
- Statistiques : séries, non téléchargées, catégories.
- Catégories avec compteur et barre ; un appui ouvre la Bibliothèque filtrée. Mention « lecture seule en V1 ».

### 9.9 Réglages (`Settings.dc.html`)

- Carte sombre d'état du PC : nom, version TMM-Win, protocole, « En ligne », dernier signal.
- **Google Drive :** compte (Changer), dossier `TranslateMyManga/`, conservation (Jamais supprimer / Après 30 jours / Après récupération).
- **Mihon :** dossier (Changer), dernière sauvegarde lue (vers Catalogue), « Refaire l'installation ».
- **Traduction :** « Réglages de traduction du PC » (résumé moteur, langues, parallélisme) vers l'écran 9.
- **Réseau :** « Wi-Fi uniquement » (désactivé par défaut).
- **Diagnostic :** export des journaux. Pied : version de l'app et du protocole.

### 9.10 Traduction sur le PC (`TranslationSettings.dc.html`)

- Sous-titre « Les mêmes réglages que dans TMM-Win ». Bandeau d'état : « Synchronisé avec PC-BUREAU », « Envoi au PC... » pendant l'envoi, « Appliqué sur PC-BUREAU » à la confirmation (7.5). Sans confirmation après 60 s : « En attente du PC », « Sera appliqué au prochain démarrage du PC ».
- Groupes : Langues (source, cible) ; Moteur (moteur, « Secours LLM local ») ; Après traduction (« Vérifier automatiquement ») ; Performance (Chapitres simultanés 1-4, Pages simultanées 1-4, Pages par envoi au moteur 1-3, carte graphique).
- « Vision avancée (5 réglages) » repliable : détecteur, OCR, effacement, taille d'effacement, police.
- Choix par feuille de boutons radio ; la ligne modifiée clignote brièvement.
- Note fixe : clés API et dossier de sortie restent sur le PC ; application sans redémarrage.
- Écran en lecture seule (contrôles désactivés) tant qu'aucun `pc/settings.json` n'a été reçu.

### 9.11 Feuille « Nouvelle traduction » et états transverses

- **Feuille F :** étape 1 « série » (recherche, séries ayant des chapitres pas encore envoyés, « N à envoyer ») ; étape 2 « chapitres » (téléchargés dans Mihon, absents du PC, tous cochés par défaut, « Tout cocher / Tout décocher », case « Traduire à l'arrivée ») ; bouton « Envoyer N chapitres » puis « Ajouté à la file · Voir ».
- **Vide (`Empty.dc.html`) :** aucun chapitre téléchargé (bouton « Scanner Mihon »), pas de sauvegarde (instructions d'activation, lien « J'ai une sauvegarde, la lire »), file vide.
- **Hors ligne (`Offline.dc.html`) :** bandeau « Pas de connexion… » ou alerte « Accès à Google Drive expiré » avec « Reconnecter » ; bloc « En attente d'envoi » ; bloc « Toujours disponible ».
- **Permission (`Permission.dc.html`) :** écran bloquant « L'app ne voit plus le dossier Mihon », dossier attendu, « Choisir à nouveau le dossier », puis « Accès rétabli ».

### 9.12 Sélection multiple par appui long

Même geste que la galerie photo d'Android.

**Bibliothèque (grille de séries) :**
1. **Entrée :** hors mode sélection, un appui simple ouvre la Fiche série ; un appui long (délai système) active le mode sélection et coche la série, avec un retour haptique d'appui long.
2. **En mode sélection :** un appui simple coche ou décoche la série au lieu de l'ouvrir. Un appui long agit comme un appui simple.
3. **Sortie :** décocher la dernière série cochée, bouton « Terminer », ou retour système. La sortie vide la sélection.
4. **Alternative accessible :** le bouton « Sélectionner » entre en mode sélection sans rien cocher ; dans ce cas, décocher la dernière série ne fait **pas** sortir du mode (seuls « Terminer » et le retour système en sortent).
5. **Affichage :** en-tête, bandeau, cases, anneau et barre d'actions exactement comme dans `Library.dc.html` en mode sélection. La barre d'actions n'apparaît qu'avec au moins une série cochée.
6. **Persistance :** la sélection survit à la rotation et au changement de recherche, de filtre ou de catégorie (les séries masquées restent cochées et comptées). Elle est vidée en quittant l'écran.

**Fiche série (liste de chapitres) :** chaque ligne est déjà une case à cocher (maquette) : un appui simple coche ou décoche, un appui long fait de même avec retour haptique. Les lignes verrouillées ne réagissent pas. Avec au moins un chapitre coché, le retour système vide d'abord la sélection au lieu de quitter l'écran.

**TalkBack :** chaque élément expose un état coché ; l'action d'appui long de la Bibliothèque est annoncée « Sélectionner ».

---

## 10. Charte graphique

Tirée des maquettes. Référence visuelle : `maquettes/*.dc.html` (390 × 844, soit le format d'un téléphone moyen).

### 10.1 Couleurs (thème clair V1)

| Jeton | Valeur | Usage |
|---|---|---|
| `ink` | `#16161A` | Texte principal, carte sombre, pastille FR, indicateur d'onglet actif |
| `paper` | `#F4F1EA` | Fond d'écran de l'app ; texte sur fond `ink` |
| `paperRaised` | `#FBF9F4` | Barre de navigation, cadres en pointillés |
| `surface` | `#FFFFFF` | Cartes, boutons secondaires |
| `line` | `#DDD6C8` | Bordures de cartes, séparateurs |
| `lineStrong` | `#D5CDBC` | Bordures de pastilles et de champs |
| `track` | `#ECE7DC` | Fond des barres de progression |
| `textMuted` | `#5E5A52` | Texte secondaire, onglets inactifs |
| `textOnInkMuted` | `#CFC8B8` / `#E4DED0` | Texte secondaire sur la carte sombre |
| `accent` | `#C8391F` | Action principale, liens, pastille « à envoyer », badge de navigation |
| `accentPressed` | `#9E2A14` | Action principale pressée |
| `dangerBg` / `dangerText` | `#FBE3DC` / `#7A2210` | Erreurs, alertes de trou, pourcentage en cours |
| `info` / `infoBg` | `#2B4C7E` / `#E3E9F2` | « À récupérer », icônes de réception |
| `success` | `#2F7D4F` | PC en ligne, état « à jour » |
| `absent` / `absentText` | `#B9B0A0` / `#9C9486` | Indicateurs en pointillés (absent de...) |

Placeholders de couverture : aplats pastel (`#C9D3DE`, `#E2C6BB`, `#CFD8C6`, `#D8D2E4`...) couverts d'une **trame de points** (points `ink` à 28 % d'opacité, pas de 5 px) qui rappelle la trame manga. Utilisés tant qu'une couverture ne charge pas, et définitivement si elle échoue.

### 10.2 Typographie (polices incluses dans l'APK, licence OFL)

| Rôle | Police | Graisse | Tailles |
|---|---|---|---|
| Titres d'écran, chiffres clés | Bricolage Grotesque | 800 | 36 (titre d'écran), 40 (chiffres clés), 24/22 (titres de fiche), 19 (titres de section) |
| Nom d'app, sous-titres forts | Bricolage Grotesque | 600-800 | 15-20 |
| Corps de texte | IBM Plex Sans | 400/500/600 | 15 (boutons), 14, 13 (principal), 12 (secondaire), 11 (étiquettes, navigation) |
| Badges techniques (FR, %, compteurs de chapitres) | IBM Plex Mono | 500 | 10-11 |

Étiquettes de section : 12-13, graisse 600, majuscules, interlettrage 0,04 em.

### 10.3 Formes et espacements

- Marges latérales : 20. Espacement entre blocs : 14 ; entre éléments d'une grille : 10.
- Rayons : 16 (grandes cartes), 14 (tuiles de raccourci, alertes), 12 (boutons principaux), 8 (couvertures), 6 (petits badges), pilule complète (pastille d'état, onglet actif).
- Bouton principal : hauteur 48, fond `accent`, texte blanc 15/600, icône 20 à gauche.
- Bouton secondaire : hauteur 36-48, fond `surface`, bordure `line`.
- Bouton d'ajout : bordure en pointillés `#9C9486` de 1,5, fond transparent.
- Barre de navigation : hauteur 76, fond `paperRaised`, bordure haute `line`. Onglet actif : pilule `ink` de 52 × 30 autour de l'icône, libellé 11/600 ; inactif : `textMuted` 11/500.
- Couvertures : ratio proche de 0,72 dans la grille (3 colonnes) ; rail d'accueil 108 × 120.

### 10.4 Iconographie

Icônes linéaires 24 × 24, trait de 1,8, extrémités arrondies (style des maquettes, proche de Lucide). Icônes en 20 dans la navigation, 22 dans les tuiles.

### 10.5 Langage visuel des statuts

| Statut | Représentation |
|---|---|
| Présent (téléphone / PC) | Icône pleine `ink` |
| Absent | Cadre en pointillés `absent`, icône `absentText` |
| Traduit (FR) | Badge mono « FR », fond `ink`, texte `paper` |
| FR absent | Badge « FR » en pointillés |
| En traduction | Badge mono « 45% », fond `dangerBg`, texte `dangerText` |
| À envoyer | Pilule `accent`, texte blanc |
| À récupérer | Pilule `info`, texte blanc |
| Échec | Alerte `dangerBg`/`dangerText` + bouton « Réessayer » bordé |
| PC en ligne | Point `success` |

### 10.6 Accessibilité

- Zones tactiles d'au moins 48 × 48.
- Chaque indicateur d'état a une description pour TalkBack (reprendre les `title`/`aria-label` des maquettes : « Sur le téléphone », « Absent du PC », « Traduit »...).
- Le statut n'est jamais porté par la couleur seule (icône ou texte en plus).
- Respect de la taille de police système.

### 10.7 Mode sombre

V2. Les jetons sont définis dès la V1 dans un seul objet de thème pour que le mode sombre ne soit qu'une seconde palette.

---

## 11. Exigences non fonctionnelles

### 11.1 Performances

- Ouverture de l'app jusqu'au contenu affiché depuis le cache : moins d'une seconde.
- Scan d'une bibliothèque de 150 séries et 3 000 chapitres téléchargés : moins de 10 s au premier passage, moins de 2 s ensuite (cache).
- Lecture d'une sauvegarde de 150 séries : moins de 3 s, hors du thread principal.

### 11.2 Fiabilité

- Un transfert interrompu (réseau, app fermée, téléphone redémarré) reprend seul via WorkManager.
- Aucune écriture partielle visible par Mihon (section 8.4).
- Aucune perte de job : l'état est en Room avant tout appel réseau.

### 11.3 Rafraîchissement manuel anti-spam

- Une seule exécution à la fois : un appui pendant un rafraîchissement ne relance rien.
- Bouton désactivé avec indicateur pendant l'exécution, puis délai de 30 s (anneau décompté, maquette Accueil) avant de le réactiver.
- Tirer vers le bas et bouton passent par la même fonction.
- Les erreurs (réseau, quota Drive, jeton) s'affichent en bandeau sans faire planter l'écran.

### 11.4 Réseau

- Données mobiles autorisées par défaut ; option « Wi-Fi uniquement » appliquée comme contrainte WorkManager.
- Nouveaux essais avec délai croissant sur les erreurs Drive temporaires (429, 5xx).

### 11.5 Sécurité et confidentialité

- Aucune clé d'API de traduction sur le téléphone : elles restent sur le PC.
- Jetons Google gérés par les services Google du téléphone ; rien en clair dans l'app.
- Journaux sans jeton ni URL signée.
- Les fichiers Drive ne sont accessibles qu'au compte de l'utilisateur.

### 11.6 Distribution

- APK signé avec une clé conservée hors du dépôt.
- Numéro de version affiché dans Réglages ; `protocol` vérifié à chaque rafraîchissement. Si le PC annonce un protocole plus récent, bandeau « Mettez à jour l'app ».

---

## 12. V2 détaillée

### 12.1 Connexion directe (Tailscale ou réseau local), Drive en secours

- **Principe :** avant chaque opération, le téléphone teste le PC en direct (requête de santé, délai court de 2 s). S'il répond, le job passe en direct ; sinon, il passe par Drive. Le `jobId` est le même dans les deux cas et le PC dédoublonne.
- **Côté PC :** une écoute séparée, liée **uniquement** à l'adresse Tailscale (et éventuellement au réseau local), jamais à `0.0.0.0`, avec une authentification par jeton. Le backend principal reste sur `127.0.0.1`.
- **Appairage :** écran `Pairing.html` : QR code ou code à 6 chiffres affiché par TMM-Win pour échanger le jeton ; liste des PC associés, bouton « Oublier ».
- **Bénéfices :** progression en temps réel (WebSocket), transferts plus rapides, pas de quota Drive.
- **Règle :** un job commencé sur un canal y reste jusqu'à son résultat, pour ne jamais être traité deux fois.

### 12.2 Rafraîchissement automatique et notifications

- Tâche WorkManager périodique (15 min, le minimum imposé par Android) : rafraîchissement, téléchargement automatique des résultats, scan Mihon, envoi des nouveaux chapitres des séries en traduction auto.
- Notifications locales : « 3 chapitres traduits prêts dans Mihon », « Échec de traduction : Blue Lock ch. 214 ».
- Option pour une notification instantanée via ntfy.sh (sans serveur à maintenir), à évaluer.

### 12.3 Édition du catalogue et export `.tachibk`

- Édition : créer, renommer, supprimer, réordonner des catégories ; déplacer des séries entre catégories.
- Export : un `.tachibk` reconstruit à partir de la **dernière sauvegarde brute conservée**, dont seules les catégories changent. Tout le reste (historique, trackers, préférences, champs des forks) est recopié **à l'identique**.
- Garde-fou : l'export est refusé si Mihon a produit une sauvegarde plus récente que celle qui a servi de base ; l'app propose de réimporter puis de réappliquer les modifications.
- L'utilisateur restaure ensuite ce fichier dans Mihon (restauration = fusion).
- Tests obligatoires : aller-retour lecture puis écriture d'une vraie sauvegarde sans modification, avec un résultat identique octet pour octet après décompression, ou à défaut sémantiquement identique.

### 12.4 Chapitres traduits dans la série d'origine

Objectif : ne plus avoir de série en double. Les chapitres FR apparaissent dans la série d'origine avec un scanlator dédié (« Translate My Manga FR »), et le filtre par scanlator de Mihon permet d'afficher l'un ou l'autre.

Mécanisme envisagé :
1. Ajouter, dans une sauvegarde générée, des chapitres à la série d'origine avec le scanlator « Translate My Manga FR » et une URL propre (par exemple `tmm://<jobId>`).
2. Placer le CBZ traduit dans `downloads/<source>/<série>/` sous le nom que Mihon attend pour ce chapitre, afin qu'il le considère comme téléchargé et le lise sans réseau.
3. Restaurer la sauvegarde dans Mihon.

À valider par un prototype avant tout développement :
- la mise à jour de la bibliothèque de Mihon supprime-t-elle les chapitres que la source ne renvoie pas ? (à lire dans le code de Mihon) ;
- la règle exacte de nommage des dossiers de téléchargement ;
- ce qui se passe si le fichier est absent (l'extension de la source tentera d'ouvrir une fausse URL).

Repli si le prototype échoue : garder `Mihon/local` et ranger automatiquement l'entrée locale dans la même catégorie que la série d'origine (via la sauvegarde générée).

### 12.5 Traduction automatique des nouveaux chapitres disponibles

La détection des nouveaux chapitres chez les sources est déjà faite par Mihon : mise à jour automatique de la bibliothèque et **téléchargement automatique des nouveaux chapitres par catégorie**. La V2 consiste à :
- documenter dans l'onboarding le réglage Mihon correspondant (par exemple une catégorie « Auto-traduction ») ;
- proposer de lier l'interrupteur « Traduction automatique » à cette catégorie ;
- laisser la tâche périodique (12.2) envoyer les chapitres dès qu'ils sont téléchargés.

Aucune interrogation des sources n'est codée dans TMM-Mobile.

### 12.6 Autres V2

- Pause et reprise globales de la file (commande dans `pc/commands/`).
- Profil et glossaire de la série, modifiables depuis le téléphone (envoyés au PC par le canal actif).
- Export du catalogue en JSON ou CSV ; « Mettre à jour le PC » : envoi d'un instantané du catalogue pour que TMM-Win connaisse aussi les séries non téléchargées.
- Mode sombre.

---

## 13. V3 : pistes

### 13.1 Regroupement de séries découpées en plusieurs entrées

Cas : une œuvre publiée par tomes ou saisons, chacun étant une série distincte dans Mihon.
- Détection : titres normalisés (sans « Vol. », « Tome », « Season », « Part », numéros, ponctuation), similarité de chaînes, même auteur si disponible.
- L'app **propose** des regroupements ; l'utilisateur confirme ou refuse (refus mémorisé).
- Un groupe est une vue dans TMM-Mobile (Bibliothèque, Fiche série fusionnée). Rien n'est modifié dans Mihon.

### 13.2 Amis

Options à étudier : chaque ami avec son propre PC et son Drive (aucun changement, simple distribution de l'APK) ; ou un ami qui utilise le PC du propriétaire via un dossier Drive partagé (quotas, file partagée, priorités, et scope `drive.file` à revoir).

---

## 14. Risques et pièges

| Risque | Impact | Parade |
|---|---|---|
| Jetons OAuth expirant après 7 jours (projet en « Testing ») | Synchro cassée chaque semaine | Projet Cloud en « In production » dès le jalon 0 |
| `drive.file` : fichiers d'un client invisibles pour l'autre | Protocole inopérant | Test au jalon 0 ; repli scope `drive` |
| Relecture puis réécriture `.tachibk` avec un schéma incomplet (V2) | Perte silencieuse de l'historique ou des trackers | Conserver l'original brut, ne modifier que les catégories, test d'aller-retour |
| Sauvegarde Mihon obsolète | Catalogue en retard | Afficher la date de la sauvegarde ; les téléchargements scannés restent la vérité pour les chapitres |
| Rapprochement dossier / catalogue raté | Chapitre « hors catalogue » | Règle de nettoyage reprise de Mihon ; envoi toujours possible via `mihonPath` |
| Lenteur SAF | Scan de plusieurs dizaines de secondes | `DocumentsContract` direct + cache par date de modification |
| Fichier traité avant la fin de son envoi | CBZ corrompu traduit | Marqueur de fin (`request.json`/`result.json` en dernier) + SHA-256 |
| Double traitement (deux appuis, deux canaux en V2) | Travail en double, doublons dans Mihon | Dédoublonnage par `mihonPath` + idempotence par `jobId` |
| Désynchronisation des protocoles entre les deux apps | Erreurs incompréhensibles | Champ `protocol` partout, bandeau de mise à jour |
| Permission SAF perdue (mise à jour Android, dossier déplacé) | App aveugle | Vérification au lancement, écran de ré-autorisation |
| Évolution du format de sauvegarde Mihon | Import cassé | Tolérance aux champs inconnus ; tests sur de vraies sauvegardes de plusieurs versions |

---

## 15. Critères d'acceptation V1

1. Après l'onboarding, la Bibliothèque affiche toutes les séries et catégories de la dernière sauvegarde Mihon, y compris les séries non téléchargées.
2. Un nouveau `.tachibk` dans `autobackup/` est pris en compte à la prochaine ouverture, sans action manuelle.
3. Un chapitre téléchargé dans Mihon apparaît « À envoyer au PC » ; après « Envoyer », il est présent sur Drive avec `request.json` écrit en dernier.
4. Envoi avec le PC éteint : le job reste « envoyé » ; au démarrage du PC, il est traité sans action côté téléphone.
5. La File montre l'étape et la page du job en cours après un rafraîchissement.
6. « Retirer de la file » annule un job en attente ; « Réessayer » relance un échec.
7. Un résultat se télécharge, se vérifie et s'installe dans `Mihon/local/<série>/`, lisible dans Mihon sans autre manipulation.
8. Une série en traduction auto : un nouveau chapitre téléchargé part seul au prochain lancement ou scan.
9. Dix appuis rapides sur Rafraîchir ne déclenchent qu'un rafraîchissement et aucun plantage.
10. Couper le réseau pendant un envoi puis le rétablir : l'envoi reprend et aboutit.
11. L'option « Wi-Fi uniquement » bloque les transferts en données mobiles.
12. Aucun chapitre n'est envoyé deux fois, même avec des appuis multiples.
13. Bibliothèque : un appui long sur une série active la sélection et la coche avec retour haptique ; les appuis suivants cochent ou décochent ; décocher la dernière, « Terminer » ou le retour système quittent le mode. La barre d'actions additionne correctement les chapitres des séries cochées.
14. « Vérifier » et « Retraduire » sur un chapitre, une sélection ou une série créent un job par chapitre concerné, sans `source.cbz` ; « Vérifier la page » crée un job `verify` avec `pages` en base 0.
15. Après réception d'un `result.json`, les statistiques du chapitre (bulles, SFX, date de vérification, alertes) s'affichent dans la Fiche série et dans « Terminées ».
16. Avec 4 jobs actifs sur le PC, l'Accueil et la File en montrent 4, chacun avec son étape et sa page.
17. Un changement dans « Traduction sur le PC » écrit une commande ; l'écran affiche « Appliqué sur PC-BUREAU » quand `settings.json` confirme, sans redémarrage du PC.

---

## 16. Jalons de réalisation V1

| Jalon | Contenu | Livrable |
|---|---|---|
| **J0 : prototypes techniques** | (a) projet Google Cloud en production, deux clients OAuth, test de visibilité croisée `drive.file` ; (b) lecture SAF du dossier Mihon + décodage d'un vrai `.tachibk` ; (c) écriture d'un CBZ dans `Mihon/local` et lecture dans Mihon ; (d) règle de nettoyage des noms de dossiers relevée dans le code de Mihon | Note de résultats, décisions de repli |
| **J1 : squelette** | Projet Android, thème (section 10), navigation 5 onglets, Room, `AppContainer` | App navigable avec données factices |
| **J2 : Mihon en lecture** | Onboarding étapes 2 et 3, import de la sauvegarde, scan des téléchargements, rapprochement ; écrans Bibliothèque, Fiche série, Catalogue | Bibliothèque réelle, sans réseau |
| **J3 : envoi** | Onboarding étape 1, `DriveTransport`, préparation du CBZ, envoi reprenable, `request.json` ; **en parallèle dans TMM-Win** : pont Drive minimal (lecture des requêtes, statuts, résultats) | Chapitre traduit de bout en bout |
| **J4 : suivi et récupération** | Rafraîchissement anti-spam, File, Transferts, installation dans `Mihon/local`, `received.json`, heartbeat et index, modes `verify`/`retranslate`, statistiques | Cycle complet depuis l'app |
| **J5 : finitions** | Traduction auto par série, Réglages, Traduction sur le PC (7.5), états vides et erreurs, export des journaux, APK signé | V1 livrée, critères de la section 15 validés |

Toutes les maquettes V1 sont faites (`maquettes/`). Plan d'exécution détaillé : `docs/PLAN_GEMINI.md`.

---

## 17. Décisions complémentaires (validées le 26/09/2026)

1. Nom affiché de l'app : **TMM Mobile** (l'en-tête des maquettes est à adapter).
2. Dossier `Mihon/local` : **même convention que l'export ADB de TMM-Win**, relevée dans son code et fournie par le PC dans `result.json`.
3. Envoi sans traduction : **conservé en V1** (case « Traduire dès l'arrivée sur le PC » dans Transferts, pour mettre des originaux à l'abri sur le PC).
4. Langue source : réglage global du PC (défaut « auto »), modifiable depuis « Traduction sur le PC » ; pas de réglage par série en V1.
5. Onglet « Synchro » renommé **« Transferts »**.
6. Sélection multiple des séries **par appui long**, comme la galerie photo (9.12), en plus du bouton « Sélectionner ».
7. Jusqu'à **4 chapitres traduits en parallèle** sur le PC ; réglage `chapterConcurrency` modifiable depuis le téléphone.
8. **Réglages de traduction du PC modifiables depuis le téléphone**, appliqués à chaud (7.5). Les clés d'API restent exclusivement sur le PC.
9. Vérification et retraduction disponibles au niveau chapitre, sélection et série ; vérification ciblée d'une page depuis les alertes.
