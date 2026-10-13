# Audit de l'interaction PC (translate-my-manga-win) et mobile (TMM Mobile)

Date : 2026-10-13. Audit en lecture seule, rien n'a été exécuté ni modifié.
Branche mobile `feat/v1-android` (8a4b1ee, v1.5.4) ; dépôt PC au commit c530aaa.

Légende :
- **V** : lu dans le code par un agent d'audit.
- **V+** : revérifié directement pendant la consolidation.
- **S** : supposé, à confirmer par un test.

Chemins : `mobile/` = `app/src/main/java/com/tmm/mobile/` ; `pc/` = `translate-my-manga-win/core-engine/`.

## Synthèse

Le contrat JSON (request, status, result, codes d'erreur) est cohérent des deux côtés, et la lecture est tolérante. L'ordre d'écriture du résultat côté PC est sûr. L'authentification du transport direct est solide. Aucune faille n'est exploitable sans jeton d'appairage ou sans accès au Drive.

Les vrais problèmes sont fonctionnels et viennent de deux causes :

1. **Le PC identifie un chapitre par titre + nom de chapitre, sans source ni scanlator.** Deux éditions du même chapitre se marchent dessus : original écrasé, mauvaise traduction livrée, suppression croisée.
2. **L'auto-traduction réagit à tout chapitre qui redevient « À envoyer ».** Elle annule l'effet d'une annulation ou d'une suppression, sans prévenir et en consommant du quota.

## Gravité haute

### H2. Original écrasé sur le PC et mauvais chapitre traduit (V+)

`pc/drive/bridge.py:283-292` : `canonical_original` ne dépend que du titre de la série et du nom du chapitre. Le téléphone envoie bien le `scanlator` (`mobile/workers/SendJobWorker.kt:332`), mais le PC ne l'utilise pas pour nommer le fichier.

En mode translate, le CBZ reçu est écrit à `dest` (`bridge.py:1036-1040`) **avant** le test `duplicate` de `_add_one` (`bridge.py:1073-1077`).

Scénario : le job A (ch. 14, scanlator X) est en file. Le job B (ch. 14, scanlator Y, même nom « Chapter 14 ») arrive. B écrase l'original de A puis échoue en `duplicate`. A traduit alors les pages de B.

**Correctif :**
- inclure le scanlator, ou le hash de l'URL du chapitre, dans le nom de l'original et de la sortie ;
- et/ou tester le doublon avant d'écrire.

### H3. Traduction d'une autre édition livrée puis installée par-dessus (V+ sur la clé du registre)

Côté PC, le registre et le court-circuit `already_done` sont indexés par (titre, numéro) :
- `pc/jobs/translation_ledger.py:136-137` ;
- `pc/routers/queue.py:170-189` ;
- `pc/jobs/preflight.py:145-150`.

Une demande pour l'édition B reçoit donc tout de suite la traduction de l'édition A, sous le même `chapterFileName`. `already_done` ignore aussi `targetLanguage`.

Côté mobile, `mobile/mihon/LocalInstaller.kt:71-89` remplace le fichier existant de même nom sans rien demander. La ligne LocalFr (clé `localPath`) passe d'une édition à l'autre (`InstallJobWorker.kt:122-131`). L'autre édition repasse « À récupérer », et chaque récupération écrase la précédente : on tourne en boucle.

Même cause pour la suppression : un delete de A efface le fichier partagé avec B (`bridge.py:1393`).

**Correctif :** même clé d'identité que H2, de bout en bout (registre, preflight, nom de sortie, nom installé).

### H4. Annuler est défait par l'auto-traduction (V+)

`mobile/data/JobRepository.kt:174-187` : en PREPARING/UPLOADING, `cancel()` supprime le job. Le chapitre redevient TO_SEND (`data/status/ChapterStatus.kt:53`).

Ensuite, `runAutoTranslate` (`data/AutoTranslate.kt:40-62`) le recrée au prochain refresh si la série est suivie : il est appelé à chaque refresh (`RefreshCoordinator.kt:124`, branché dans `AppContainer.kt:141`).

L'utilisateur voit son annulation s'effacer sans explication.

**Correctif :** mémoriser l'annulation, par exemple avec un job CANCELLED conservé ou une liste d'exclusion par `mihonPath`, et faire en sorte que l'auto-traduction l'ignore.

### H5. Supprimer un chapitre traduit relance sa traduction (V+ sur la logique)

Scénario : série suivie, suppression cochée « téléphone + PC », téléchargement Mihon conservé.

1. Le PC efface l'original et la sortie (`pc/drive/chapter_delete.py:140`) ; l'index passe à `fr=false, original=false`.
2. Le dernier job est INSTALLED, état que `deriveStatus` ne traite pas. On tombe sur `downloaded`, donc TO_SEND.
3. L'auto-traduction renvoie le chapitre et consomme du quota.

La feuille de suppression ne prévient pas.

**Correctif :** au minimum un avertissement dans la feuille. Mieux : exclure de l'auto-traduction tout chapitre que l'utilisateur a supprimé lui-même.

## Gravité moyenne

### M0. Sélection par numéro entier dans la fiche série (V+)

`mobile/ui/screens/series/SeriesViewModel.kt:357` : `number = (... ?: 0f).toInt()`. `rowsFor` (`:215-218`) filtre `row.number in nums`.

Les mêmes lignes servent à Supprimer (`:207`), Traduire (`:229`) et Récupérer (`:257`).

- Cocher le 7 agit aussi sur le 7.5.
- Cocher un chapitre sans numéro agit sur tous les chapitres sans numéro.
- Deux copies téléchargées du ch. 12 (deux scanlators) sont traitées ensemble.

Ce n'est pas silencieux : chaque ligne s'affiche cochée selon `row.number in state.selected` (`SeriesScreen.kt:409`), donc toutes les lignes du même numéro apparaissent cochées. Le défaut réel est qu'on ne peut pas cibler une seule copie ni le 7 sans le 7.5. Avec la case Mihon, la suppression est irréversible.

**Correctif :** sélectionner par `mihonPath` (ou une clé de ligne unique), pas par numéro.

### M1. CANCEL_REQUESTED peut rester bloqué pour toujours (V)

Le job reste « actif » tant que `pcState` n'est ni `failed` ni `cancelled` (`ChapterStatus.kt:10-13`). Il s'affiche donc « En file », et `createJobs` refuse le chapitre (`JobRepository.kt:86`).

De son côté, le PC ne traite `cancel.json` que pour une entrée active (`bridge.py:1127-1128`). Si le PC a perdu ou fermé le job sans écrire de status (remise à plat `bridge.py:539`, mode quiet `bridge.py:949-951`), rien ne le débloque : `detectStalledJobs` ne regarde que SENT (`RefreshCoordinator.kt:313`).

**Correctif :** délai de garde sur CANCEL_REQUESTED, puis passage local en CANCELLED.

### M2. Job PC_STALLED affiché « En file » (V)

`deriveStatus` ne lit pas `errorCode` (`ChapterStatus.kt:46`). Un job SENT marqué `pc_stalled` apparaît donc « En file »/« En cours ». Il bloque aussi la suppression (`ChapterDeleter.kt:126`) et tout nouvel envoi.

### M3. Le résultat d'une suppression sur le PC est invisible (V)

Le PC répond toujours `status: applied`, avec un compteur `failed` (`bridge.py:778-779`). `SettingsSync.reconcile` ne lit que le statut (`SettingsSync.kt:196-199`).

Le toast annonce donc une suppression même quand le PC l'a refusée, par exemple pour une tâche en cours (`chapter_delete.py:121-124`).

À l'inverse, si la version des réglages monte pendant qu'un delete est en vol, le téléphone le marque REJECTED (`SettingsSync.kt:220`). Le PC l'applique pourtant, car un delete n'est pas contrôlé par `baseVersion`.

### M4. Le téléphone gagne silencieusement les conflits de réglages (V+)

Une commande pas encore envoyée est recalée sur la nouvelle version du PC sans contrôle (`SettingsSync.kt:218-219`). Après un refus `stale_version`, elle est renvoyée une fois sur la nouvelle base (`:200-211`). Le garde `baseVersion` du PC (`bridge.py:782-787`) ne protège donc rien.

Scénario : téléphone hors ligne, moteur passé à `deepl` ; sur le PC, l'utilisateur choisit `claude`. Au retour, `deepl` écrase `claude` sans avertir.

L'inverse existe aussi : l'interface PC renvoie toute la configuration à chaque enregistrement (`src/.../configStore.ts:138-204`). Elle ne se resynchronise que sur l'événement WebSocket `settings_changed`. Si le socket est coupé, un enregistrement sur le PC remet l'ancienne valeur.

### M5. La police affichée « appliquée » est ignorée par le PC (V)

Le PC ignore la clé `font` (`bridge.py:87,797`) mais répond `applied` et incrémente la version (`:804-805`). Le téléphone affiche « Manga Temple appliqué » (`TranslationSettingsViewModel.kt:97,116-119`). À la réouverture de l'écran, il revient à « Anime Ace ».

**Correctif :** retirer le choix côté mobile, ou l'implémenter côté PC.

### M6. Dossiers Drive jamais nettoyés (V)

L'outbox ne supprime que les jobs INSTALLED (`OutboxWorker.kt:46-58`). La rétention de 30 jours exige `receivedWrittenAt` (`:91`).

Restent donc sur Drive, avec source.cbz et parfois result.cbz :
- les jobs échoués ou annulés ;
- les anciens jobs remplacés par `retry`/`recreate` ;
- les dossiers abandonnés lors d'une bascule Drive vers direct pendant UPLOADING (`SendJobWorker.kt:139-143`, `DirectTransport.kt:225`).

Conséquences : le quota Drive se remplit (erreur `drive_quota`). Le PC relit ces dossiers indéfiniment et peut les retraduire s'il perd `drive_bridge.json` (`bridge.py:403-413, 929-932`). Le téléphone applique alors ce nouveau status à un ancien job sans filtre (`RefreshCoordinator.kt:182-206`).

### M7. Chapitre annulé réinstallé par « Récupérer depuis Drive » (V, scénario S)

Un résultat arrivé après une annulation n'est pas accusé (`RefreshCoordinator.kt:219-224`). « Récupérer depuis Drive » (`JobRepository.kt:320-353`) le recrée en RESULT_READY et l'installe.

### M8. Double traduction après « Relancer » des deux côtés (S)

Un job `failed` reste suivi sur le PC, qui peut le relancer (`bridge.py:996-998`). Si l'utilisateur fait aussi « Relancer » sur le téléphone, `recreate()` crée un nouveau job (`JobRepository.kt:276`).

Le contrôle de doublon du PC exclut les jobs `failed`. Résultat : deux traductions, deux installations, quota consommé deux fois.

### M9. Renvoi en masse des jobs SENT sans status (S)

`resendStalledDriveJobs` (`JobRepository.kt:232-242`) supprime et recrée les jobs SENT restés 10 min sans status, sans vérifier que le PC est en ligne.

Sur un gros lot, le PC télécharge les sources une par une (`bridge.py:854-871`). Des jobs qu'il n'a pas encore vus sont donc détruits et renvoyés, avec un risque de `duplicate`.

### M10. Sécurité : zip bomb dans les CBZ (V code, S impact)

`pc/.../archive_handler.py:194,259,393,568` font `z.read()` sans plafond de taille décompressée. Une seule entrée très compressée peut provoquer un MemoryError dans le moteur.

Il n'y a pas de zip-slip. L'attaque exige un téléphone appairé ou un accès en écriture au dossier Drive.

### M11. Sécurité : portée Drive complète (V)

Le téléphone et le PC demandent `auth/drive` (`GoogleAuth.kt:17-19`, `pc/drive/api.py:34,475`), pas `drive.file`. CLAUDE.md dit `drive.file` : la documentation est fausse.

Corollaire : quiconque peut écrire `pc/remote.json` redirige le transport direct du téléphone (`RefreshCoordinator.kt:169`), et le jeton Bearer part vers cette URL (`DirectTransport.kt:232-234`). HTTPS est imposé par défaut (S).

### M12. Sécurité : `allowedLogin` jamais configuré (V)

`auth.py:43` reste `""`, et aucune route ne l'écrit. `_check_login` (`remote/app.py:127-129`) laisse donc passer tout nœud du tailnet, y compris un nœud partagé.

`/pair` reste protégé par un code à 6 chiffres (300 s, 5 essais), mais un pair hostile peut épuiser les essais et bloquer l'appairage.

## Gravité basse

- **B1** (V) : faux « bloqué sur le PC » pendant `fetching`. `activeJobIds` ne contient que processing/paused (`bridge.py:1417`), alors que le téléphone lève `pc_stalled` au bout de 10 min (`RefreshCoordinator.kt:316-322`).
- **B2** (V) : un job verify ou retranslate en mode quiet est fermé sans status.json (`bridge.py:949-951`). Le téléphone n'en sort que par `pc_stalled` au bout de 15 min.
- **B3** (V) : « Oublier le PC » ne révoque pas l'appareil côté PC (`DirectTransport.kt:115-120`). Le jeton reste valide.
- **B4** (V) : un appareil révoqué choisit quand même le direct, car `/health` n'est pas authentifié. Le premier envoi échoue en PAIRING au lieu de passer sur Drive (`DirectTransport.kt:256-259`).
- **B5** (V) : la bascule direct vers Drive est silencieuse quand le PC est éteint (health-check de 2 s, `TransportRouter.kt:16-17`). Ce n'est pas un bug, mais l'utilisateur ne sait pas quel canal est utilisé.
- **B6** (V) : traversée d'un niveau. Un titre de série `..` donne `Originaux/../cNNNN - x.cbz` (`sanitize_filename` ne filtre pas `..`, `pc/.../mihon_naming.py:11,22`). Cela reste dans le dossier de sortie.
- **B7** (V, timing S) : course entre `cancel()` et `deliver`. `SendJobWorker.kt:203-208` fait un `upsert(SENT)` après avoir relu le job ; si `cancel()` a supprimé la ligne entre-temps, le job annulé réapparaît en SENT.
- **B8** (V) : reprise d'upload resumable sur un CBZ reconstruit. Le CBZ n'est pas déterministe (`CbzPacker.kt:66-70`, heure des entrées). Le PC le détecte (`integrity`), mais le job est perdu.
- **B9** (V) : `LocalInstaller` écrase aussi un CBZ que l'utilisateur a placé lui-même dans `local/<dossier>`.
- **B10** (S) : orphelins sur le PC après suppression : `.tmm_work/<titre>/<nom>` et dossiers de série vides.
- **B11** (S) : un chapitre sans numéro et sans hash Mihon prend le numéro par défaut 1.0 (`bridge.py:290`). Risque de collision sur `c0001 - ...`.
- **B12** (V) : pas de limite de taille sur le corps JSON de `/inventory` et `/commands` (`remote/app.py:160-165`). Il faut un jeton.

## Points vérifiés sains

- Les codes d'erreur sont identiques des deux côtés (`ErrorCatalog.kt:34` = `errors.py:16`). Les valeurs de réglages forment une liste fermée identique (`ProtocolModels.kt:310-327` = `bridge.py:68-84`).
- Ordre d'écriture PC : result.cbz, puis result.json, puis status `done`. SHA-256 vérifié à la réception.
- Le PC ne supprime jamais un chemin reçu : il relit son propre enregistrement, borné aux racines de la bibliothèque. La suppression a une confirmation avec l'avertissement « Irréversible » pour Mihon.
- Une annulation côté PC remonte au téléphone. Un résultat arrivé après une annulation n'est pas installé automatiquement.
- Pas de boucle d'auto-traduction sur un échec (FAILED n'est pas TO_SEND).
- `SettingsSync.values()` avec un Int et un Boolean en attente (envoyés comme chaînes `"2"`, `"true"`) se décode correctement : vérifié par un test jetable (1 test, 0 échec), supprimé ensuite.
- Jeton mobile chiffré AES-GCM dans le Keystore, `allowBackup=false`. Côté PC, seul le hash du jeton est stocké (DPAPI), avec une comparaison en temps constant. L'API locale est limitée à localhost avec contrôle de l'Origin.

## À trancher par des tests

- `direct_submit` ne prend pas `_direct_lock` (`bridge.py:1455`) : course possible avec le cycle Drive (S).
- `Startup.kt:101` lance l'auto-traduction avant le premier poll, donc sur un `pc_index` peut-être périmé (S).
- Affichage côté React des jobs annulés ou relancés depuis le PC : non lu.

## Ordre de correction recommandé

1. **H4 + H5** : l'auto-traduction doit respecter les annulations et les suppressions. Correctif mobile.
2. **H2 + H3** : clé d'identité incluant le scanlator côté PC. C'est le plus gros chantier : il touche le nommage, le registre et preflight, avec une migration des fichiers existants à prévoir. En attendant, refuser côté mobile d'envoyer deux éditions d'un même chapitre, ce qui coûte peu.
3. **M0** (sélection par `mihonPath`), puis **M1, M2, M3** : états bloqués ou faux affichés.
4. **M6** : nettoyage Drive des jobs terminés non installés.
5. Sécurité **M10-M12** : plafond de décompression, puis `allowedLogin`, puis corriger CLAUDE.md sur la portée Drive.
