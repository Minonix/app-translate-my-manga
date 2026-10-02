# Plan : connexion directe via Tailscale (côté mobile)

Date : 2026-09-28. Plan compagnon côté PC : `translate-my-manga-win/doc/plans/tailscale_pc.md`.

## Contexte

Aujourd'hui, le téléphone (`app-translate-my-manga`, Kotlin) et le PC (`translate-my-manga-win`, FastAPI) ne se parlent que par la **boîte aux lettres Google Drive** : un sondage toutes les 15 s côté PC, des fichiers JSON et un aller-retour lent. Tu as choisi Tailscale (rapport `doc/reports/acces_distant_cloudflare_vs_tailscale.md`).

**Décisions déjà prises :**
- **Transport :** Tailscale en direct quand le PC répond, Drive en secours, avec bascule automatique.
- **Livrables :** un plan `.md` par repo, avec le contrat commun recopié à l'identique dans les deux.
- **Travail déjà prévu :** le cahier des charges mobile prévoit ce « DirectTransport » en v2 (`docs/CAHIER_DES_CHARGES.md` §4.3, §11.5, §12.1). Il fixe un jobId identique sur les deux canaux, un health-check de 2 s, un jeton d'appairage (jamais en clair, jamais dans les logs) et la règle « un job reste sur son canal jusqu'à son résultat ». La maquette `maquettes/v0/Pairing.html` existe déjà.

**Principe directeur :** on **réutilise les contrats JSON du protocole Drive v1**, tels quels, sur HTTP : `RequestJson`, `StatusJson`, `ResultJson`, `Heartbeat`, `PcIndex`, `PcSettings`, `SettingsCommand`, `CancelJson`, `ReceivedJson`. Le « direct » n'est qu'une autre boîte aux lettres, plus rapide. Il n'y a pas de nouveau modèle de données, juste un nouveau transport de chaque côté.

> Le contrat commun ci-dessous est recopié à l'identique dans l'autre repo. Toute modification doit être faite des deux côtés.

## Contrat commun v1 (identique dans les deux docs)

**Transport :** HTTPS sur `https://<pc>.<tailnet>.ts.net/api/remote/v1/`, certificat MagicDNS fourni par `tailscale serve`. Il n'y a jamais de HTTP en clair, donc aucune `network_security_config` n'est nécessaire côté Android.

**Authentification :** `Authorization: Bearer <token>` sur toutes les routes, sauf `health` et `pair`. Si le jeton est absent ou invalide, le PC répond 401 avec `{"error":{"code":"unauthorized"}}`.

**Corps :** tous les JSON portent `protocol: 1`. Un client qui reçoit `protocol > 1` passe en `protocolTooNew`, comme sur Drive. Les objets sont exactement ceux des fixtures `app/src/test/resources/protocol/*.json`.

| Méthode | Route | Corps / réponse |
|---|---|---|
| GET | `health` | Sans auth, réponse en moins de 2 s : `{protocol, pcName, appVersion, capabilities:["jobs","settings","inventory"]}` |
| POST | `pair` | Sans auth : `{code, deviceId, deviceName}`, réponse `{token, pcName}`. Le code fait 6 chiffres, vaut 5 min, sert une seule fois et accepte 5 essais au maximum |
| GET | `poll?since=<iso>` | `{cursor, statuses:[StatusJson], results:[ResultJson], heartbeat, index, settings}`, soit le miroir de `PollResult`. `statuses` couvre **tous** les jobs connus du PC, quel que soit leur canal |
| PUT | `jobs/{jobId}/source` | Corps brut CBZ, avec en-têtes `Content-Length` et `X-Sha256`. Le PC vérifie la taille et le SHA-256, puis écrit en `.tmp` suivi de `os.replace`. Réponse 204 |
| POST | `jobs/{jobId}` | `RequestJson`, réponse `StatusJson`. Une requête **idempotente** sur le jobId renvoie le statut existant. Si le job est déjà connu via Drive, la réponse est 409 `{"error":{"code":"other_channel"}}` |
| GET | `jobs/{jobId}/result.cbz` | Le fichier de sortie, retrouvé côté PC à partir de l'état du job. Le client ne fournit **jamais** de chemin |
| POST | `jobs/{jobId}/cancel` | `CancelJson`, réponse 204 |
| POST | `jobs/{jobId}/received` | `ReceivedJson`, réponse 204. Le PC clôt le job, comme la suppression du dossier sur Drive |
| POST | `commands` | `SettingsCommand`, validé par `SETTINGS_SPEC`. Réponse : le `PcSettings` à jour |
| PUT | `inventory` | `PhoneInventory` (nouveau, voir plus bas), réponse 204 |

**Nouveau fichier Drive `pc/remote.json` :** `{protocol:1, url, updatedAt}`. Le PC l'écrit quand l'accès distant est actif, et le téléphone y découvre l'URL sans saisie.

**Nouveau modèle `PhoneInventory` (téléphone vers PC) :**
- Enveloppe : `{protocol:1, deviceName, updatedAt, series:[...]}`.
- Chaque série : `{seriesKey, title, sourceName, chapters, downloaded, read, fr, downloadedPaths:[mihonPath]}`.
- On n'envoie que des compteurs par série et la liste des chapitres **téléchargés**. Environ 1000 séries donnent quelques centaines de Ko en gzip.

**Codes d'erreur ajoutés à `JobError.code` :** `unauthorized`, `other_channel`. Les existants sont inchangés : `bad_request`, `protocol`, `source_missing`, `integrity`, `translation_failed`.

**Matrice des actions selon l'état du chapitre** (les deux UIs la respectent) :

| État (`ChapterStatus`) | Téléphone | PC (vue « Téléphone ») |
|---|---|---|
| `CATALOG_ONLY` (dans la sauvegarde, non téléchargé) | Affiché grisé « Non téléchargé », **aucune action** | Compté dans la série (« 120 chapitres, 30 téléchargés »), **aucun bouton** |
| `TO_SEND` (téléchargé, absent du PC) | Traduire | Affiché, lecture seule (voir phase 3) |
| `QUEUED` / `TRANSLATING` | Progression, Annuler | Déjà dans la file |
| `FAILED` | Réessayer, détail de l'erreur | Idem dans la file |
| `TO_FETCH` | Récupérer (automatique) | Rien |
| `NOT_TRANSLATED` (original sur le PC) | Retraduire, sans renvoi du fichier | Traduire, depuis l'original local |
| `UP_TO_DATE` | Vérifier / Retraduire | Idem |

## Plan 2 : mobile (`app-translate-my-manga`)

**Livrable doc :** `docs/PLAN_TAILSCALE_MOBILE.md`, qui contient ce plan et le contrat commun.

**Contraintes du projet :** pas de Hilt ni de Retrofit, injection manuelle via `AppContainer`, OkHttp 5.5.0 déjà présent. Tout nouveau texte d'interface doit figurer dans `mockup_texts.tsv` et dans la maquette (`MockupTextCoverageTest`). Il faut aussi respecter la palette et les icônes existantes, et commenter en français.

1. **`transport/DirectTransport.kt : JobTransport`**, avec OkHttp et `ProtocolJson` (les mêmes modèles) :
   - `ensureLayout` et `ensureJobFolder` ne font rien et renvoient le `jobId` ;
   - `uploadSource` appelle `PUT source` en rapportant la progression, `writeRequest` appelle `POST jobs/{id}` ;
   - `poll` appelle `GET poll`, `downloadResult` appelle `GET result.cbz` ;
   - `writeCancel` et `writeReceived` appellent `POST cancel` et `POST received` ;
   - `deleteJobFolder` ne fait rien, puisque le PC clôt le job à la réception de `received` ;
   - `health()` utilise un client dédié avec un délai de connexion de 2 s.
2. **Routage du canal :** une colonne `channel` (`"drive"` ou `"direct"`) dans `JobEntity`, avec une migration Room (`app/schemas/` sera régénéré).
   - `AppContainer` expose `driveTransport`, `directTransport` et `transportFor(job)`.
   - À la **création** d'un job, `SendJobWorker` choisit direct si `health()` répond, sinon Drive, et fige le choix.
   - `InstallJobWorker`, `OutboxWorker` et `JobRepository.cancel` passent par `transportFor(job)`.
   - Pour le direct, `resultFileId` prend la valeur du `jobId`, afin de garder la garde existante de `InstallJobWorker`.
3. **`RefreshCoordinator`** :
   - si le direct répond, il fait le `poll` direct (heartbeat, index, réglages, **tous** les statuts). Il ne fait le `poll` Drive que s'il reste des jobs actifs `channel == "drive"`, pour leurs résultats ;
   - sinon, il fait le `poll` Drive comme aujourd'hui ;
   - l'appel à `ensureLayout` (lignes 63-65) passe derrière le canal Drive ;
   - le délai de 30 s est ramené à environ 5 s quand le direct est actif et qu'un écran de suivi est ouvert.
4. **`SettingsSync`** : `writeCommand` et `readSettings` passent par le canal actif, et la confirmation devient quasi immédiate en direct.
5. **Découverte et appairage :**
   - `DriveTransport.poll` lit en plus `pc/remote.json` et stocke `direct_url` dans `AppPrefs`, avec une saisie manuelle possible ;
   - l'écran d'appairage (maquette `Pairing.html`, **code à 6 chiffres**) envoie `pair`, qui renvoie le jeton ;
   - le jeton est chiffré avec une clé AES-GCM de l'**Android Keystore** (API de la plateforme, sans nouvelle dépendance) et stocké dans DataStore (`direct_token_enc`). Il ne doit jamais apparaître dans `AppLog` : ajouter le motif à la censure des logs ;
   - un 401 efface le jeton et affiche « Appairage expiré ».
   - Le scan de QR est volontairement omis : il faudrait une bibliothèque. À ajouter si la saisie du code gêne.
6. **Réglages et bandeau :**
   - dans Réglages, une section « Connexion directe » avec l'état (« Directe via Tailscale », « PC injoignable, Google Drive utilisé » ou « Non associée »), l'URL, « Associer » et « Oublier ce PC » ;
   - `MainViewModel` et `PcTone` : « En ligne » devient immédiat si `health` répond, au lieu de se fier à un heartbeat de moins de 5 min ;
   - `OfflineBanner` n'a pas de nouvel état, puisque Drive prend le relais en silence ;
   - le libellé « le PC écrit sur Drive toutes les minutes » devient conditionnel ;
   - la préférence `wifi_only` s'applique aussi aux envois directs.
7. **Inventaire** : après chaque import de sauvegarde ou scan (`Startup`, `SyncViewModel`), on calcule `PhoneInventory` à partir des DAO `series`, `chapter`, `downloaded_chapter` et `local_fr_chapter`, puis on fait `PUT inventory` si le direct est joignable. On n'envoie rien par Drive, car c'est inutile hors ligne.
8. **Matrice des actions :** elle est déjà respectée (les actions filtrent sur `TO_SEND`, `UP_TO_DATE` et `RESULT_READY`, et `CATALOG_ONLY` n'offre rien). Il faut juste l'ajouter au doc et vérifier `NOT_TRANSLATED`.

### Tests mobile (`./gradlew testDebugUnitTest lintDebug`)

- `DirectTransportTest` avec `FakePcHttp` sur MockWebServer : parcours complet (envoi, poll, résultat, `received`), 401, SHA faux, 409 `other_channel`.
- `ChannelRoutingTest` : le direct répond, donc le job est `direct`. Le direct tombe en cours de route, et le job **reste** `direct` puis réessaie. Le direct est absent, donc le job part en `drive`.
- Un test de migration Room pour `channel`.
- `ProtocolModelsTest` couvre les nouvelles fixtures (`health.json`, `pair.json`, `inventory.json`, `remote.json`), **copiées dans le repo PC**.
- `PairingScreen` : capture Roborazzi et couverture des textes.

## Phase 3, optionnelle (non incluse, à décider plus tard)

- **« Demander au téléphone »** depuis la vue Téléphone du PC, pour les chapitres `TO_SEND` : le PC ajoute une entrée `wanted` dans `poll`, et le téléphone lance lui-même l'envoi. À ajouter si tu veux piloter depuis le PC.
- **WebSocket** `remote/v1/ws` pour une progression en temps réel : le sondage toutes les 5 s suffit au début.
- **ADB en Wi-Fi via Tailscale** (`adb connect <ip-tailscale>:5555`) pour l'export Mihon à distance, qui reste capricieux.

## Ordre d'exécution après validation

1. Écrire les deux docs de plan dans les deux repos.
2. PC : extraction du cœur de la boîte aux lettres (bridge, avec ses tests existants au vert), puis `remote/`, les routes d'administration, l'UI et les correctifs de l'audit.
3. Mobile : `DirectTransport`, le routage et la migration, l'appairage, les réglages, l'inventaire.
4. Test de bout en bout réel : le téléphone en 4G avec Tailscale actif. On vérifie `health`, l'appairage, l'envoi d'un chapitre, la progression, la réception dans Mihon, puis on coupe Tailscale et on vérifie que Drive prend le relais.
