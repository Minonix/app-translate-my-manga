# Changelog

Toutes les evolutions notables de TMM Mobile sont consignees ici.

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Numerotation : [Semantic Versioning](https://semver.org/lang/fr/) (`MAJEUR.MINEUR.CORRECTIF`) :

- **MAJEUR** : rupture de compatibilite (protocole Drive/direct avec le PC, migration Room destructive, reinstallation requise).
- **MINEUR** : nouvelle fonctionnalite compatible.
- **CORRECTIF** : correction de bug ou amelioration de performance sans nouvelle fonctionnalite.

`versionCode` augmente de 1 a chaque release, quel que soit le niveau.

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
