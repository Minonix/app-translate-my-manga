# Contrat d'erreurs et de reprise entre TMM Mobile et TMM-Win

Document partagé, copie identique dans les deux dépôts (`app-translate-my-manga/docs/` et
`translate-my-manga-win/docs/`). Il complète le CdC section 7 (protocole v1) sans le casser :
**`protocol` reste à 1**, tous les ajouts sont des champs optionnels ou de nouvelles valeurs de
`error.code`. Un client ancien ignore un champ inconnu (`ignoreUnknownKeys` côté téléphone, `dict.get`
côté PC) et affiche un code inconnu comme une erreur générique.

Liste machine des codes : `error_codes.json` (copie identique dans
`app/src/test/resources/protocol/error_codes.json` et
`core-engine/tests/fixtures/protocol/error_codes.json`). Les tests des deux côtés s'appuient dessus :
toute modification d'un code se fait dans les deux copies.

## 1. Codes d'erreur

### 1.1 Émis par le PC (`status.json` `error.code`, statut du poll direct, corps HTTP direct)

| code | quand | réessayable | action proposée par le téléphone |
|---|---|---|---|
| `bad_request` | request.json illisible, type de champ invalide, mode inconnu | non | `resend` (nouveau job) |
| `protocol` | `protocol` > 1 ou non numérique | non | `update_pc` |
| `source_missing` | source.cbz absent, verify/retranslate sans copie PC | oui | `resend` (renvoyer l'original) |
| `integrity` | taille ou SHA-256 de source.cbz faux après 2 essais | oui | `resend` |
| `duplicate` | un autre jobId actif traite déjà ce `mihonPath` | non | aucune (le job existant continue) |
| `disk_full` | ENOSPC côté PC | oui | `retry` après libération |
| `model_missing` | modèle absent ou SHA invalide | non | aucune (à corriger sur le PC) |
| `api_key` | clé du fournisseur absente ou refusée | oui | `retry` après correction sur le PC |
| `provider_quota` | quota ou limite du fournisseur de traduction | oui | `retry` |
| `engine_crash` | moteur MIT planté ou injoignable après relances | oui | `retry` |
| `drive_quota` | Drive plein : result.cbz impossible à déposer | oui | `redeliver` après libération |
| `job_lost` | tâche disparue de la file du PC (purge, base perdue) | oui | `retry` |
| `translation_failed` | tout autre échec du traitement (repli) | oui | `retry` |

Réponses HTTP du canal direct, toujours au format `{"error":{"code","message"}}`, y compris
404/405/500 : `unauthorized` (401), `bad_request` (400), `protocol` (400), `integrity` (400),
`not_found` (404), `other_channel` (409), `internal` (500).

### 1.2 Locaux au téléphone (stockés en Room, jamais écrits sur Drive)

| code | quand | action |
|---|---|---|
| `network` | coupure, timeout, 5xx/429 épuisés | `retry` (automatique puis bouton) |
| `auth` | jeton Google refusé, compte retiré, accès révoqué, erreur Play Services | `reconnect_drive` |
| `pairing` | jeton direct refusé (401) | `repair` |
| `drive_quota` | 403 storageQuotaExceeded à l'envoi | `retry` après libération |
| `source_unreadable` | chapitre Mihon supprimé ou illisible | aucune |
| `permission` | accès SAF au dossier Mihon perdu | `open_permission` |
| `phone_disk_full` | plus de place sur le téléphone | `retry` après libération |
| `download` | result.cbz illisible ou SHA faux après essais | `redeliver` |
| `result_missing` | result.json présent mais result.cbz absent (404) | `redeliver` |
| `install` | écriture dans le dossier local Mihon impossible | `retry` (sans retéléchargement) |
| `pc_stalled` | job orphelin (règle R8) | `retry` |
| `protocol_too_new` | protocol.json ou fichier PC avec `protocol` > 1 | `update_app` |

### 1.3 Message

`error.message` est en français, au plus 200 caractères, **sans chemin absolu, nom d'utilisateur
Windows, jeton ni clé**. Le PC le nettoie avant publication (remplacer les chemins par leur nom de
fichier, retirer `AIza...`, `sk-...`, `Bearer ...`). Le téléphone n'affiche jamais le message brut
comme seul texte : il affiche le libellé associé au code, et le message en détail secondaire.

## 2. Ajouts optionnels au protocole v1

- `request.json` `options.redeliver: true` (mode `translate`, `file: null`) : « renvoie-moi le
  chapitre traduit que tu as déjà ». Le PC dépose la copie traduite existante sans retraduire ;
  s'il ne l'a pas, il retraduit depuis l'original ; sans original, `source_missing`. Un PC ancien
  ignore le champ et échoue en `source_missing` (pas de fichier) : c'est acceptable.
- `pc/settings.json` `lastCommandResult: {"cmdId", "status": "applied"|"rejected", "code"?, "message"?,
  "deleted"?, "failed"?, "overwritten"?, "kept"?}` : accusé explicite de la dernière commande. Codes de rejet :
  `stale_version`, `superseded`, `invalid_value`, `bad_request`. `overwritten`/`kept` (R8) : réglages dont la valeur du PC a été écrasée / conservée. `deleted`/`failed` : compteurs d'une commande
  `delete`.
- `pc/heartbeat.json` `driveOk: true|false` et `lastErrorCode` optionnels : état du pont vu par le
  PC (le heartbeat via direct reste lisible même si Drive est cassé).

## 3. Règles de robustesse

**R1 - Écritures idempotentes sur Drive.** Tout fichier à nom fixe (request, cancel, received,
commandes, status, result, heartbeat, index, settings, remote) est écrit en « chercher puis mettre à
jour par fileId, créer seulement s'il n'existe pas ». Un `fileId` connu est réutilisé.

**R2 - Dossier de job unique.** Le téléphone crée le dossier `jobs/<jobId>` une fois, enregistre son
id en base **avant** tout autre envoi, et le relit depuis la base (jamais depuis une copie locale
périmée). `uploadSource` n'appelle jamais `createFolder` : il exige le dossier ou fait
chercher-puis-créer. Si plusieurs dossiers portent le même jobId, le PC retient celui qui contient
request.json, sinon le plus ancien, et journalise le doublon.

**R3 - Lecture tolérante.** Plusieurs fichiers de même nom : pour les fichiers réécrits (status,
heartbeat, index, settings), prendre le `modifiedTime` le plus récent ; pour request/result/cancel,
le plus ancien. Un JSON vide, tronqué ou d'un mauvais type est « pas encore prêt » : réessai au
cycle suivant, jamais d'exception qui remonte.

**R4 - Une entrée ne bloque jamais le cycle.** Côté PC, chaque dossier, commande et job est traité
dans son propre `try` : une entrée invalide passe en `failed` avec un code (§1.1) et un status.json
est publié ; une erreur réseau sur une entrée la laisse pour le cycle suivant. Heartbeat, index et
remote sont publiés à chaque cycle quoi qu'il arrive plus tôt. Erreurs réseau répétées : backoff
exponentiel entre cycles (15 s à 5 min), remis à zéro au premier succès. Côté téléphone, le curseur
de rafraîchissement n'avance pas au-delà d'une entrée qui n'a pas pu être lue.

**R5 - Erreurs HTTP Drive (les deux côtés).** 401 : renouveler le jeton une fois, puis `auth`. 403 :
lire `error.errors[0].reason` ; `rateLimitExceeded`/`userRateLimitExceeded` → backoff comme 429 ;
`storageQuotaExceeded` → `drive_quota` ; autre 403 → `auth`. 404 : traité explicitement par
l'appelant (jamais écrit dans un fichier téléchargé). 429/5xx/timeout/DNS : backoff exponentiel avec
`Retry-After` (secondes ou date HTTP). Un corps d'erreur n'est jamais écrit dans un fichier de
destination.

**R6 - Fin de job dans l'ordre.** Le PC dépose result.cbz (envoi reprenable, session conservée entre
cycles) puis result.json, et seulement ensuite publie `state: "done"`. Un échec d'envoi laisse le
job actif pour le cycle suivant ; `drive_quota` le passe en `failed`.

**R7 - Idempotence et dédoublonnage.** Le PC ignore un jobId déjà connu quel que soit le canal. Un
second jobId pour un `mihonPath` déjà actif échoue en `duplicate` sans toucher au job existant (et
sans partager sa tâche). Si le dossier d'un job actif disparaît de Drive, le PC annule la tâche.

**R8 - Job orphelin (téléphone).** PC en ligne (R9) et job envoyé, et : soit aucun status.json 15 min
après l'envoi, soit état `fetching`/`translating` sans que le jobId figure dans `activeJobIds`
depuis 10 min. Le job s'affiche « bloqué sur le PC » (`pc_stalled`) avec « Relancer ». Côté PC, une
tâche disparue est publiée `failed`/`job_lost` (jamais `cancelled`), et `waiting_recovery` ne publie
pas `failed` tant que la récupération est possible.

**R9 - Horloges.** « PC en ligne » se décide sur l'heure serveur : `modifiedTime` Drive du
heartbeat comparé à l'en-tête `Date` de la réponse Drive (ou heure de réception en direct), jamais
`heartbeat.updatedAt` contre l'horloge du téléphone. Le PC borne ses écarts de temps à >= 0 et
utilise un temps monotone pour ses cadences.

**R10 - Réception et installation (téléphone).** Le CBZ téléchargé est gardé en cache jusqu'au
succès de l'installation ; un nouvel essai d'installation ne retélécharge pas. L'installation écrit
un fichier temporaire, le vérifie, puis remplace l'ancien sans le supprimer avant que le nouveau soit
en place. Lecture/écriture en flux (pas de `readBytes` du fichier entier). `result.json` avec
`file: null` met à jour les statistiques et clôt le job sans installation ni exception.

**R11 - Annulation.** Annulation pendant l'envoi : suppression locale et du dossier. Après envoi :
cancel.json. Si un résultat ou un état terminal arrive après la demande d'annulation, le job est clos
localement (annulé, résultat non installé) et ne bloque plus le chapitre.

**R12 - Réglages.** Le PC compare `baseVersion` à sa version. Différent et `createdAt` illisible → `rejected`/`stale_version`
sans rien appliquer. Différent avec `createdAt` valide (R8, la modification la plus récente gagne) : chaque réglage changé
sur le PC après `createdAt` est conservé (`kept`), les autres sont appliqués (`overwritten` si la valeur du PC est remplacée) ;
rien d'appliqué → `rejected`/`superseded`. Valeur hors liste → `rejected`/`invalid_value`. Le PC écrit l'accusé
(settings.json avec `lastCommandId` et `lastCommandResult`) **avant** de supprimer la commande, et
ignore un `cmdId` déjà traité. Le téléphone ne considère une commande appliquée que si
`lastCommandId == cmdId` (et `lastCommandResult.status == "applied"` s'il est présent) ; sur
`stale_version`, il relit les réglages et renvoie une seule fois ses changements sur la nouvelle base.

**R13 - Auth.** Téléphone : toute exception levée par l'autorisation Google (Play Services hors
ligne, compte retiré, consentement requis) est `auth` → retry + bandeau « Reconnecter », jamais
FAILED définitif ; tous les workers positionnent le même drapeau ; le jeton refusé est invalidé
(`clearToken`). PC : état `authLost` visible par un bandeau global de l'interface, reprise automatique
après reconnexion.

**R14 - Récupération des données.** Téléphone : action « Récupérer depuis Google Drive » (Paramètres)
qui parcourt `jobs/*`, et pour chaque dossier avec result.json sans received.json, recrée un job local
prêt à installer ; les chapitres déjà installés sont reconnus et ignorés. La base Room ne doit jamais
empêcher l'app de démarrer : downgrade destructif autorisé, une migration absente est un bug à tester.
PC : drive_bridge.json illisible ou d'un mauvais type est mis de côté (`.bad`) et l'état est
reconstruit depuis Drive (job avec result.json → clos ; sans → repris).

**R15 - Aucun crash.** Aucune exception non rattrapée dans un worker, une coroutine de ViewModel,
une tâche de fond du PC ou un endpoint. Les tâches de fond du PC ont un callback de fin qui journalise
et redémarre.
