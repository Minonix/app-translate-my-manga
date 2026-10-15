# Changelog

Toutes les evolutions notables de TMM Mobile sont consignees ici.

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Numerotation : [Semantic Versioning](https://semver.org/lang/fr/) (`MAJEUR.MINEUR.CORRECTIF`) :

- **MAJEUR** : rupture de compatibilite (protocole Drive/direct avec le PC, migration Room destructive, reinstallation requise).
- **MINEUR** : nouvelle fonctionnalite compatible.
- **CORRECTIF** : correction de bug ou amelioration de performance sans nouvelle fonctionnalite.

`versionCode` augmente de 1 a chaque release, quel que soit le niveau.

## [1.13.1] - 2026-10-16

### Corrige
- Un chapitre bien telecharge dans Mihon restait « Pas telecharge » quand son URL ou son titre avait change depuis la sauvegarde : il est rattache par son numero s'il est le seul a le porter.

## [1.13.0] - 2026-10-15

### Ajoute
- Bibliotheque et fiche serie : tirer la liste vers le bas, en haut, relance un scan complet de Mihon (sans le cache) et une synchronisation avec le PC.

### Corrige
- Un chapitre dont le telechargement se terminait pendant un scan restait « Pas telecharge » : le dossier serie n'est plus mis en cache tant qu'un telechargement y est en cours.

## [1.12.0] - 2026-10-15

### Ajoute
- Reglages de traduction : « Montrer la page au modele » active ou coupe l'envoi d'une miniature de chaque page au modele (Gemini, ou modele OpenRouter qui lit les images). Synchronise avec le PC comme les autres reglages.

## [1.11.1] - 2026-10-15

### Corrige
- Fiche serie : un chapitre telecharge en .cbz, traduit par le PC puis supprime des telechargements de Mihon (lu) n'apparait plus « Non telechargee ». Il redevient « Pret a installer » (ou « Dans Mihon ») et « Installer » le redemande au PC : plus de chapitres manquants dans la serie FR.

## [1.11.0] - 2026-10-15

### Ajoute
- Repli Drive : un chapitre traduit envoye en direct mais jamais recupere (Tailscale coupe, PC eteint) est telecharge et installe depuis la copie que le PC depose sur Drive. Une seule installation, jamais de double traduction.
- Reglages : ligne « Application » qui verifie la derniere release GitHub et installe l'APK (verifie avant ouverture de l'installateur Android).

## [1.8.1] - 2026-10-14

### Corrige
- Tests : un test instable (coroutines de fond apres fermeture de la base) bloquait la publication de 1.8.0 ; 1.8.1 contient les memes corrections que 1.8.0.

## [1.8.0] - 2026-10-14

### Ajoute
- Activite : « Reessayer » sur tout echec et « Vider » dans Terminees et Echecs (sans supprimer l'historique du suivi).

### Corrige
- Statuts : une traduction deja presente dans Mihon n'apparait plus « Prete a installer ».
- Envoi : un original que le PC possede deja n'est plus renvoye, le PC le retraduit depuis sa copie.
- Serie : « Retraduire » est propose des que le PC a l'original.
- Accueil : le bandeau « Le PC n'accede plus a Google Drive » ne s'affiche plus a tort.

## [1.7.0] - 2026-10-13

### Ajoute
- Activite : « Tout annuler » pour les chapitres en attente et pour les envois en cours.
- Serie : choisir les chapitres a traiter ; Accueil : synchronisation visible.

### Corrige
- Activite : un chapitre dont l'annulation est demandee quitte « Ensuite » tout de suite (la croix agit).
- « PC vu il y a… » : l'etat se rafraichit chaque minute tant que l'app est ouverte, aussi en canal direct.
- Serie et Bibliotheque : « Recuperer » redemande au PC un chapitre deja traduit qu'il est seul a connaitre, sans le retraduire (R13).

## [1.6.0] - 2026-10-13

### Corrige
- Serie et suivi : un chapitre se choisit par edition, une seule par numero (R2).
- Suivi : un chapitre annule ou deja traite n'est plus renvoye (R3, R7).
- Plus d'etat bloque ou faux sur le telephone (envois, suppressions, actualisation).
- Premier lancement : plus de valeurs de maquette (PC, dossier Drive) et le bouton de l'ecran vide lance vraiment le scan.

### Modifie
- Reglages : un message signale quand une modification ecrase une valeur venue du PC (R8).

### Retire
- Choix de la police et action « Recuperer depuis Drive » (R12).

## [1.5.4] - 2026-10-13

### Corrige
- Traduire : la recherche de serie ne perd plus les lettres tapees rapidement (seule la premiere restait).

## [1.5.3] - 2026-10-13

### Corrige
- Chapitres telecharges d'une serie absente de la sauvegarde Mihon (ajoutee apres la derniere sauvegarde, ou autre source) : la serie apparait dans la Bibliotheque avec ses chapitres, au lieu de tout marquer "Pas telecharge".
- Bibliotheque et Fiche serie comptent le meme total de chapitres (les doublons du catalogue ne comptent qu'une fois).
- Traduire : quand plusieurs scantrads fournissent le meme numero, une seule copie est cochee par defaut (la scantrad la plus presente) ; la scantrad s'affiche sur les doublons pour choisir, ou cocher les deux. Une copie dont le numero est deja envoye ou traduit n'est plus precochee.

## [1.5.2] - 2026-10-13

### Corrige
- Fiche serie : une ligne par chapitre, plus de plages trompeuses (« Ch. 1 a 2 », « 2 a 3 »...) quand un numero existe en double ; l'exemplaire telecharge masque ses doublons du catalogue.
- Chapitres telecharges detectes sur la bonne serie quand elle est deux fois dans la bibliotheque (FR et EN d'une meme source, ou ajoutee en double).

## [1.5.1] - 2026-10-13

### Corrige
- Demarrage plus rapide : l'ecran s'affiche apres les verifications rapides, le scan et le rafraichissement Drive passent en arriere-plan.
- L'onglet Accueil ne rouvre plus une serie ouverte depuis l'Accueil ; un retour au premier plan ne ramene plus a l'Accueil.
- Chapitres manquants : comptes seulement si la serie a au moins un chapitre sur le telephone ou le PC ; la ligne des manquants est retiree de l'Accueil.
- Le numero de chapitre -1 n'etire plus la plage des manquants et s'affiche "Sans numero" ; seuls les numeros consecutifs sont regroupes.
- Couvertures : celles de la source locale Mihon s'affichent, et les redirections vers http:// sont suivies en https.

## [1.5.0] - 2026-10-12

Remplace la 1.4.0, qui n'a pas ete publiee.

### Ajoute
- Gestion des erreurs avec le PC : chaque erreur affiche un libelle et une action (Relancer, Reconnecter Drive, Mettre a jour le PC...).
- Bandeaux "Drive plein" et "Le PC n'accede plus a Google Drive".
- Detection d'un job bloque sur le PC, avec Relancer.
- Parametres : "Recuperer depuis Google Drive" recree les traductions recues mais pas installees.
- Reglages de traduction : le motif d'un refus du PC est affiche.

### Corrige
- Premier envoi en traduction : la source et la demande partaient dans deux dossiers Drive differents.
- Une perte d'autorisation Google ne fait plus echouer definitivement les jobs.
- Un resultat sans fichier ne bloque plus l'envoi des accuses.
- Installation : l'ancien chapitre n'est plus supprime avant que le nouveau soit en place, et un nouvel essai ne retelecharge pas.
- Plusieurs reglages modifies d'affilee ne sont plus perdus.

### Note
- La base locale passe en version 3 (migration conservatrice). Reinstaller une version anterieure efface l'historique local des jobs.

## [1.4.0] - 2026-10-12

### Ajoute
- Interface simplifiee (maquette v2) : Accueil, Bibliotheque, Fiche serie, feuille Traduire, Activite, Parametres et Reglages de traduction.
- Feuille Traduire : "Traduire" envoie les chapitres a traduire, "Copier" les envoie sans traduction.
- Activer le suivi d'une serie envoie aussi ses chapitres deja telecharges.
- Parametres : reimport d'une sauvegarde Mihon.
- Notifications d'envoi et de reception distinctes (canal "Envois et receptions").

### Modifie
- Le Catalogue et l'ecran Nouvelle traduction sont remplaces par la Bibliotheque et la feuille Traduire.
- Accueil : "dans N series" accorde au pluriel.

### Corrige
- Reception : progression mise a jour pendant le telechargement, et un chapitre supprime pendant la reception n'est plus reinstalle.
- Activite : la coche s'affiche correctement (plus de "racine carree").
- Actualiser renvoie vers l'ecran Permission quand l'acces au dossier Mihon a ete retire.

## [1.3.1] - 2026-10-11

### Corrige
- Crash au lancement apres la mise a jour 1.3.0 (remise a plat sur une grosse file de traductions).

## [1.3.0] - 2026-10-11

### Ajoute
- Remise a plat unique au premier lancement : traductions en cours et en file annulees, chapitres deja envoyes gardes sur le PC (non traduits), traductions terminees conservees.
- Parametres : la version affichee est celle de l'application installee.

### Modifie
- "Traduire a l'arrivee" est desactive par defaut, et la traduction automatique par serie est remise a zero : rien ne part en traduction sans demande.
- Boite aux lettres Drive dans `App/TranslateMyManga` (TMM-Win a jour requis).

### Corrige
- Accueil : plus de clignotement a chaque actualisation (un etat Drive plus ancien n'ecrase plus l'etat recu en direct du PC).
- Bandeau "Reconnecter Google Drive" qui restait affiche apres l'autorisation.
- Bandeau hors ligne qui clignotait avec plusieurs reseaux actifs (Wi-Fi + VPN).

## [1.2.0] - 2026-10-09

### Ajoute
- Suppression de chapitres depuis la Fiche serie : traduction locale, telechargement Mihon, fichiers sur le PC (commande `delete`, ignoree par un PC plus ancien).
- Bibliotheque : feuille Trier et filtrer (tri, affichage, categories) a la place du lien vers le Catalogue.
- File : le bouton + ouvre la Bibliotheque en mode selection.
- Le bouton Retraduire devient Traduire quand aucun chapitre de la selection n'a ete traduit.

### Corrige
- Accueil : carte "A faire" et sections vides masquees.
- File : onglet "En cours" ; echecs des chapitres non telecharges masques et traduction refusee pour eux.
- Couvertures : en-tetes navigateur et URL normalisee.
- Reconnexion Drive (bandeau Reconnecter) et dossier `APP/TranslateMyManga`.

## [1.1.0] - 2026-10-05

Renumerotation de la v1.0.6 : elle ajoutait des fonctionnalites et aurait du etre une version mineure. Le code est identique a la v1.0.6.

### Ajoute
- Connexion directe au PC (Tailscale) : transport direct, routage Drive/direct, section Reglages pour l'adresse du PC et le code d'appairage.
- Bibliotheque : filtres "Telechargees" et "Sur le PC", barre de filtres defilante.

### Corrige
- Scan Mihon : seuls les dossiers et `.cbz` contenant des pages comptent comme chapitres (`cover.jpg`, `.nomedia` et dossiers vides gonflaient "a envoyer") ; purge des anciennes lignes sans page.
- Envoi massif sans crash : 2 `SendJobWorker` au plus en parallele, CBZ construit page par page, creation des jobs en une transaction Room.

### Performance
- Statuts de l'accueil calcules en O(n).

## [1.0.5] - 2026-10-01

### Performance
- Bibliotheque : liste partagee (`LibraryCache`) et instantane disque (`library_snapshot.json`) affiche des le lancement, recalcul Room en fond, calcul suspendu 5 s apres la sortie de l'ecran.
- Actions Bibliotheque : chapitres relus a la demande pour les seules series selectionnees.
- Fiche serie : jointure limitee a la serie ouverte.
- Scan `downloads/` incremental (dossiers inchanges ignores, une transaction par serie) ; scan `local/` avec cache par date de dossier.
- Import d'une nouvelle sauvegarde : rapprochement des telechargements existants sans relire le stockage.

### Corrige
- Chapitres, series et fichiers supprimes de Mihon retires de la base.

## [1.0.4] - 2026-09-28

### Corrige
- Freeze/crash de la Bibliotheque avec ~1000 series : jointure chapitres indexee une seule fois, calcul des etats hors thread principal.
- Crash de la grille quand deux sources ont le meme titre (cle = `seriesKey`).
- Nouvelle traduction : l'envoi lit la selection de maniere synchrone.

## [1.0.3] - 2026-09-28

### Corrige
- Barre de navigation basse invisible en release : la comparaison de routes utilisait des noms de classes renommes par R8, remplacee par `NavDestination.hasRoute`.

## [1.0.2] - 2026-09-27

### Corrige
- Crash a la lecture des sauvegardes Mihon : numeros protobuf de `BackupManga` alignes sur Mihon.
- Une sauvegarde illisible ne fait plus planter l'onboarding ni le demarrage.

## [1.0.1] - 2026-09-27

### Corrige
- Contenu sous la barre de statut/gestes (edge-to-edge impose par targetSdk 37).
- Connexion Google redemandee quand le process est tue pendant le selecteur SAF.

### Modifie
- Dossier Drive `TranslateMyManga` cree sous `App/` (nouveaux onboardings uniquement).

## [1.0.0] - 2026-09-27

Premiere version : scan des chapitres Mihon, envoi au PC via la boite aux lettres Google Drive, suivi des traductions, installation des chapitres traduits dans la bibliotheque locale Mihon, reglages PC synchronises.

[1.1.0]: https://github.com/Minonix/app-translate-my-manga/compare/v1.0.5...v1.1.0
[1.0.5]: https://github.com/Minonix/app-translate-my-manga/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/Minonix/app-translate-my-manga/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/Minonix/app-translate-my-manga/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/Minonix/app-translate-my-manga/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/Minonix/app-translate-my-manga/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/Minonix/app-translate-my-manga/releases/tag/v1.0.0
