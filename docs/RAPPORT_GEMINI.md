# Rapport d'exécution TMM Mobile V1

## 1. Résumé
- Branche : feat/v1-android
- Dernier commit : `10494e9` (E2)
- Étapes terminées : E0, E1, E2
- E3 : code écrit, non commité — bloqué par BLOQUÉ-1 (section 7), tests non exécutables dans cet environnement
- Tests : voir section 9
- Blocages : 1 (section 7, BLOQUÉ-1)

## 2. Étapes
| Étape | Commit (sha court) | État (fait / partiel / bloqué) | Remarque en une ligne |
|---|---|---|---|
| E0 | `f326868` | fait | Préparation du dépôt, .gitignore, local.properties et inventaire officiel des versions |
| E1 | `9b0aada` | fait | Squelette Gradle, application vide et SmokeTest |
| E2 | `10494e9` | fait | Thème, polices Google Fonts, couleurs strictes, icônes SVG maison et animations Motion — tests non exécutés (BLOQUÉ-1) |
| E3 | - | bloqué (BLOQUÉ-1) | 18 composants Compose maison (Library.dc.html), MockupData, Roborazzi et tests comportementaux — code écrit, tests non exécutables (section 7) |
| E4.1..E4.13 | - | non commencé | 13 écrans en données factices, mockup_texts.tsv et comparaisons Roborazzi |
| E5 | - | non commencé | Base de données Room (13 entités), DataStore et dérivations pures de statut |
| E6 | - | non commencé | Accès SAF Mihon, décodage Protobuf .tachibk, packaging CBZ et LocalInstaller |
| E7 | - | non commencé | FakeDrive, FakePc et transport REST Google Drive v3 via OkHttp |
| E8 | - | non commencé | Workers WorkManager d'arrière-plan, RefreshCoordinator et simulation JobFlowTest |
| E9 | - | non commencé | Synchronisation bidirectionnelle des réglages PC à distance (SettingsSync) |
| E10 | - | non commencé | Raccordement des données réelles dans les 13 écrans et gestion du cycle de vie |
| E11 | - | non commencé | Durcissement, lintDebug sans erreur, ProGuard R8 et génération APK validé |

## 3. Environnement et versions

### 3.1 Outils de développement vérifiés
| Outil | Version vérifiée | Emplacement / Commande de validation |
|---|---|---|
| JDK | 26.0.1 (Oracle SE build 26.0.1+8-34, target bytecode 17) | `java -version` |
| Android SDK Platforms | android-36, android-37.0 | `%LOCALAPPDATA%\Android\Sdk\platforms` |
| Android SDK Build-Tools | 36.0.0 | `%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0` |
| Gradle | 9.7.0 (dist locale en cache) | `%USERPROFILE%\.gradle\wrapper\dists\gradle-9.7.0-bin` |
| Python | 3.13.1 | `python --version` |
| Git | 2.47.1.windows.2 | `git --version` |

### 3.2 Dépendances et artefacts (relevé officiel stable)
| Outil ou artefact | Version | URL consultée |
|---|---|---|
| Android Gradle Plugin (AGP) | 9.4.0 | https://developer.android.com/studio/releases/gradle-plugin |
| Gradle | 9.7.0 | https://services.gradle.org/distributions/ |
| Kotlin (plugins android, compose, serialization) | 2.4.20 | https://kotlinlang.org/docs/releases.html |
| KSP (Kotlin Symbol Processing) | 2.3.10 | https://github.com/google/ksp/releases |
| Compose BOM | 2026.09.00 | https://developer.android.com/jetpack/compose/bom/bom-mapping |
| androidx.activity:activity-compose | 1.13.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| androidx.lifecycle:* (runtime, viewmodel) | 2.11.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| androidx.navigation:navigation-compose | 2.10.2 | https://developer.android.com/jetpack/androidx/releases/navigation |
| androidx.room:* (runtime, ktx, compiler) | 2.8.5 | https://developer.android.com/jetpack/androidx/releases/room |
| androidx.work:work-runtime-ktx | 2.12.0 | https://developer.android.com/jetpack/androidx/releases/work |
| androidx.datastore:datastore-preferences | 1.2.1 | https://developer.android.com/jetpack/androidx/releases/datastore |
| androidx.core:core-ktx | 1.19.1 | https://developer.android.com/jetpack/androidx/releases/core |
| kotlinx-serialization-json, protobuf | 1.11.0 | https://github.com/Kotlin/kotlinx.serialization/releases |
| kotlinx-coroutines-android, test | 1.11.0 | https://github.com/Kotlin/kotlinx.coroutines/releases |
| com.squareup.okhttp3:okhttp, mockwebserver | 5.5.0 (ou 4.12.0) | https://square.github.io/okhttp/changelog/ |
| io.coil-kt.coil3:coil-compose, coil-network-okhttp | 3.6.3 | https://coil-kt.github.io/coil/changelog/ |
| com.google.android.gms:play-services-auth | 22.0.0 | https://developers.google.com/android/guides/releases |
| junit:junit (JUnit 4) | 4.13.2 | https://junit.org/junit4 |
| org.robolectric:robolectric | 4.17 | https://github.com/robolectric/robolectric/releases |
| com.github.takahirom.roborazzi:* | 1.75.0 | https://github.com/takahirom/roborazzi/releases |
| androidx.compose.ui:ui-test-junit4, manifest | via BOM (2026.09.00) | https://developer.android.com/jetpack/compose/bom/bom-mapping |
| androidx.test:core | 1.19.1 | https://developer.android.com/jetpack/androidx/releases/test |
| androidx.test.ext:junit | 1.3.0 | https://developer.android.com/jetpack/androidx/releases/test |
| androidx.work:work-testing | 2.12.0 | https://developer.android.com/jetpack/androidx/releases/work |
| androidx.room:room-testing | 2.8.5 | https://developer.android.com/jetpack/androidx/releases/room |

Plus :
- SHA du commit google/fonts et fichiers de police copiés : `[À renseigner en E2]`
- Empreinte SHA-1 de la clé debug : `[À renseigner en E11 via ./gradlew signingReport]`

## 4. Documentation externe lue
| Sujet | URL | Ce qui a été retenu (une ligne) |
|---|---|---|
| Google Drive REST API v3 | https://developers.google.com/drive/api/v3/reference/files | Méthodes REST standards (findChild, create multipart, upload reprenable 8 Mo, list, delete). |
| Play Services Auth / Identity | https://developers.google.com/android/guides/setup | Flux d'autorisation avec AuthorizationRequest sur le scope drive.file via ActivityResultLauncher. |
| WorkManager Long-running workers | https://developer.android.com/topic/libraries/architecture/workmanager/advanced/long-running | Déclaration foregroundServiceType="dataSync" et notification de premier plan pour synchronisation. |
| Kotlinx Serialization Protobuf | https://github.com/Kotlin/kotlinx.serialization/blob/master/formats/protobuf/README.md | Décodage de flux Gunzip avec tolérance aux champs et tags inconnus des forks Mihon. |
| Android SAF DocumentsContract | https://developer.android.com/reference/android/provider/DocumentsContract | Requêtes de colonnes par ContentResolver sur treeUri sans instanciation lourde DocumentFile. |
| Dépôt officiel Mihon | https://github.com/mihonapp/mihon | Algorithme exact d'assainissement des noms de fichiers et décodage de sauvegardes .tachibk. |

## 5. Mihon
- Dépôt, commit : [À renseigner en E6]
- Fichiers lus : [À renseigner en E6]
- Règle de nettoyage des noms : [À renseigner en E6]
- Suffixe des téléchargements incomplets : [À renseigner en E6]
- Lien manga / catégories dans la restauration : [À renseigner en E6]

## 6. Écarts
### 6.1 Écarts visuels (par écran)
| Écran | Image de comparaison | Différence restante | Cause |
|---|---|---|---|
| Aucun écart en E0 | - | - | Aucun écran développé en E0 |

### 6.2 Écarts au plan ou au CdC
| Référence (section) | Ce que dit le document | Ce qui a été fait | Pourquoi |
|---|---|---|---|
| PLAN_GEMINI.md E2, `ui/theme/Motion.kt` | Jeu complet de spécifications d'animation | Portée réduite à ce qui est réellement consommé par `TmmTheme`/`ReducedMotion` en E2 | Éviter du code spéculatif non branché (YAGNI) ; le reste sera ajouté au fil des écrans E3/E4 qui en ont réellement besoin |
| PLAN_GEMINI.md 9.2 E2.5, glyphe 訳 de `ic_launcher_foreground.xml` | Vectoriser le glyphe 訳 (U+8A33) depuis Bricolage Grotesque | Foreground vide conservé, fond `#C8391F` seul | Vérifié via `fontTools` (cmap) : la police variable Bricolage Grotesque ne contient que 597 glyphes latins/étendus, pas de glyphe CJK U+8A33. Fallback documenté du plan lui-même appliqué plutôt que fabriquer un tracé inventé |
| PLAN_GEMINI.md 4.9, `TmmDialog` | Aucune maquette dédiée à une boîte de dialogue centrée | Variante minimale réutilisant le mécanisme de voile de `TmmSheet` (pop/fade au lieu d'un slide) | Composant requis par le plan mais sans référence visuelle directe dans les 13 maquettes ; cohérence avec le langage visuel plutôt qu'invention arbitraire |
| PLAN_GEMINI.md 4.9, chaînes fixes des composants (OfflineBanner, TmmBottomBar, RefreshButton) | `strings.xml` pour les libellés fixes | Chaînes codées en dur dans les composables, comme en E2 | Cohérence avec le choix déjà fait en E2 ; l'extraction vers `strings.xml` sera traitée globalement plus tard si nécessaire (YAGNI tant qu'aucune i18n n'est requise) |
| PLAN_GEMINI.md 5.20, patron `XxxRoute`/navigation | Câblage `NavController` par écran | `MainScreen` expose des lambdas simples (`onOpenSettings`, etc.) sans `MainEvent` de navigation ni `MainRoute` | Câblage réel du `NavHost` explicitement une responsabilité E10 par le plan lui-même |
| PLAN_GEMINI.md 4.9, `ProgressBar` (E3) | Remplissage unique `TmmColors.accent` | Paramètre `fillColor` optionnel ajouté (défaut inchangé) | Main.dc.html colore chaque barre de job selon son type (ink/info/accent) ; extension rétrocompatible plutôt qu'un second composant dupliqué |
| PLAN_GEMINI.md 4.9, `TmmActionButton` (E3) | Bouton d'action réutilisable | Non réutilisé pour la grille d'actions rapides de Main ; `QuickActionCard` dédié écrit | API incompatible : `TmmActionButton` est pleine largeur avec désactivation par compteur, la grille de Main a besoin de tuiles à hauteur fixe toujours actives |

### 6.3 Textes ajoutés (absents des maquettes)
| Ressource | Texte | Écran | Référence du plan |
|---|---|---|---|
| Aucun texte ajouté en E0 | - | - | Initialisation en E1/E2/E4 |

### 6.4 Lignes DEMO de mockup_texts.tsv
| Fichier | Texte | Donnée réelle qui le remplace |
|---|---|---|
| Aucun fichier en E0 | - | Catalogue initialisé en E4 |

## 7. Blocages
Aucun blocage constaté en E0. Les outils requis (JDK 26, SDK Android 36/37, build-tools 36.0.0, Gradle 9.7.0, Python 3.13.1, Git) sont tous présents et validés.

### BLOQUÉ-1 : `./gradlew testDebugUnitTest` ne démarre plus dans cet environnement (constaté en E2, toujours actif en E3)
- **Symptôme** : toute invocation de `./gradlew` (avec ou sans `--no-daemon`, après `./gradlew --stop`) échoue immédiatement avec `java.io.IOException: Unable to establish loopback connection`, avant toute compilation.
- **Cause racine identifiée** : JDK 26.0.1 utilise sur Windows un nouveau `WEPollSelectorProvider` (`java.nio.channels.Selector.open()`) dont le mécanisme de réveil interne établit une connexion loopback via un socket Unix Domain Socket (`sun.nio.ch.UnixDomainSockets`). Dans cet environnement, cette connexion échoue au niveau natif avec `SocketException: Invalid argument: connect`.
- **Preuve que ce n'est pas spécifique à Gradle ou au code du projet** : un programme Java minimal isolé (`Selector.open()` seul, sans Gradle) reproduit exactement la même trace dans ce même environnement.
- **Pistes déjà écartées** :
  - Changer `java.io.tmpdir` vers un chemin court sans espace : aucun effet, échec identique.
  - Arrêter tous les daemons Gradle puis relancer à froid : aucun effet, échec identique (alors que le pilote noyau `afunix` est bien chargé et `RUNNING` sur cette machine — `sc.exe query afunix`).
  - Exécuter avec le sandbox de l'outil Bash désactivé (`dangerouslyDisableSandbox`) : aucun effet, échec identique — donc la restriction n'est pas (uniquement) celle de l'outil d'exécution des commandes.
  - Installer et tester Temurin 21.0.12.1 puis Temurin 17.0.20.1 (téléchargés officiellement depuis GitHub Releases d'Adoptium, empreintes SHA-256 vérifiées avant usage) en lieu et place du JDK 26.0.1 : **échec strictement identique sur les trois JDK** (26, 21, 17). Le problème n'est donc pas propre à JDK 26.
  - Forcer l'ancien fournisseur de sélecteur via `-Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.WindowsSelectorProvider` : échec identique — la connexion loopback par Unix Domain Socket est utilisée par l'implémentation de `java.nio.channels.Pipe` elle-même, indépendamment du `SelectorProvider`.
  - **Test décisif, hors JVM** : reproduction en C#/.NET (`Socket(AddressFamily.Unix, ...)`, `dotnet run`, runtime totalement indépendant de la JVM) de la même opération de connexion loopback par Unix Domain Socket. Échec identique : `SocketException (10022) : Un argument non valide a été fourni`. Ceci prouve que le blocage n'est **ni un bug JDK, ni lié à une version de JDK**, mais une restriction de cet environnement d'exécution (VM/sandbox) sur les opérations `connect()` en AF_UNIX, malgré la présence et l'état "RUNNING" du pilote `afunix` au niveau du système.
- **Constat important** : E1 avait pourtant abouti à des tests exécutés avec succès (`SmokeTest`) dans une session antérieure avec ce même JDK 26.0.1 — ce blocage semble donc lié à un changement d'état de l'environnement d'exécution (ou de la machine/sandbox hébergeant les commandes) entre les sessions, pas au JDK.
- **Impact** : impossible d'honorer la règle « les tests doivent passer avant chaque commit » (PLAN_GEMINI.md, règle R5) pour E2 dans cet environnement précis, quel que soit le JDK utilisé. Le code d'E2 (thème, icônes, tests de garde) est écrit et cru correct, mais n'a pas pu être compilé ni exécuté pour confirmation.
- **Décision** : blocage remonté à l'utilisateur (deux fois : avant et après l'essai d'un autre JDK, qui n'a pas résolu le problème) plutôt que contourné unilatéralement. En attente d'arbitrage.
- **E3** : blocage toujours présent, sans nouvelle piste testée depuis E2. Sur autorisation explicite de l'utilisateur, le développement continue en écrivant le code et les tests avec soin (relecture manuelle systématique, plusieurs erreurs déjà corrigées ainsi — voir section 9) plutôt que de s'arrêter.
- **E4.1** : blocage toujours présent, même décision (continuer avec relecture manuelle). Deux violations de la règle 4.10 (chaînes du toast, libellés de type de job) et une régression sémantique (icône réglages avec le rôle `selectable` au lieu de `clickable`) trouvées et corrigées par cette relecture avant commit.

## 8. Fichiers
`[Module app non créé, structure initiale prévue en E1]`

## 9. Tests
- E1 : `SmokeTest` écrit et exécuté avec succès (session antérieure).
- E2 : 4 tests de garde écrits (`NoRawColorTest`, `NoMaterialIconsTest`, `PaletteCoverageTest`, `IconCoverageTest`) — voir BLOQUÉ-1 (section 7) : non exécutables dans cet environnement, correction non confirmée par exécution réelle.
- E3 : `ComponentsScreenshotTest` (1 capture Roborazzi par composant, 18 composants) et `ComponentsBehaviorTest` (rôle Switch de `TmmSwitch`, fermeture de `TmmSheet` par clic sur le voile, `liveRegion` + auto-dismiss temporisé de `TmmToast`, désactivation et changement de `contentDescription` de `RefreshButton` pendant le compte à rebours) — voir BLOQUÉ-1 (section 7) : non exécutables dans cet environnement, correction non confirmée par exécution réelle.
- E4.1 : `MainScreenshotTest` (Roborazzi, états en ligne/hors ligne), `MainScreenBehaviorTest` (cycle Envoyer/Récupérer Idle→Busy→Done, formulation des toasts `MainToast.Sent`/`Received`, notice hors ligne), `MockupTextCoverageTest` (couverture `mockup_texts.tsv` ↔ `strings.xml`, écrans E4.2+ en liste d'exclusion décroissante) — voir BLOQUÉ-1 (section 7) : non exécutables dans cet environnement, correction non confirmée par exécution réelle. Import `onRoot` manquant dans `ComponentsScreenshotTest` (E3) corrigé au passage.
- Captures : aucune capture Roborazzi réelle générée (BLOQUÉ-1 empêche l'exécution des tests qui les produiraient).

## 10. Actions pour l'utilisateur
1. Google Cloud : créer un projet (ou réutiliser celui de TMM-Win), activer l'API Google Drive, écran de consentement OAuth de type Externe, publié « En production ».
2. Créer un client OAuth « Android » : nom de paquet `com.tmm.mobile`, empreinte SHA-1 [valeur obtenue en E11 via `./gradlew signingReport`]. Refaire avec l'empreinte de la clé release quand elle existera.
3. Pour signer en release : créer un keystore, remplir `keystore.properties` (modèle `keystore.properties.example`).

## 11. Points ouverts
- Chapitres FR connus seulement par `pc/index.json` (sans `result.json`) : affichés « À récupérer » mais non récupérables par le téléphone. À traiter dans le pont Drive de TMM-Win.
- « Réessayer à partir de la page N » crée un nouveau job ; la reprise à la page N dépend du PC.
- Interrupteur « Traduction automatique » et bouton « Importer » absents des maquettes (plan 5.21).
- Pas de demande de permission de notification (Android 13+) : la notification de transfert peut être invisible.
- Langue de la série inconnue en V1 (feuille F, Fiche série).

## 12. Limites connues
- Application cliente sans moteur de traduction local embarqué (repose sur le PC TMM-Win).
- Dépendance exclusive au stockage SAF Mihon et au protocole Google Drive REST en V1.
