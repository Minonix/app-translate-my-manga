# Rapport d'exécution TMM Mobile V1

## 1. Résumé
- Branche : feat/v1-android
- Dernier commit : [En attente du commit E0: preparation du depot]
- Étapes terminées : E0
- Tests : 0 exécutés, 0 réussis, 0 échoués, 0 ignorés (lancement initial en E1)
- Blocages : 0 (section 7)

## 2. Étapes
| Étape | Commit (sha court) | État (fait / partiel / bloqué) | Remarque en une ligne |
|---|---|---|---|
| E0 | [en cours] | fait | Préparation du dépôt, .gitignore, local.properties et inventaire officiel des versions |
| E1 | - | non commencé | Squelette Gradle, libs.versions.toml et application vide avec SmokeTest |
| E2 | - | non commencé | Thème, polices Google Fonts, couleurs strictes, icônes SVG maison et animations Motion |
| E3 | - | non commencé | Composants maison Compose et tests d'architecture/garde |
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
| Android SDK Platforms | android-36, android-37.0 | `C:\Users\clement martini\AppData\Local\Android\Sdk\platforms` |
| Android SDK Build-Tools | 36.0.0 | `C:\Users\clement martini\AppData\Local\Android\Sdk\build-tools\36.0.0` |
| Gradle | 9.7.0 (dist locale en cache) | `C:\Users\clement martini\.gradle\wrapper\dists\gradle-9.7.0-bin` |
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
| Aucune déviation | - | - | Respect strict des directives E0 |

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

## 8. Fichiers
`[Module app non créé, structure initiale prévue en E1]`

## 9. Tests
- Sortie résumée de `./gradlew testDebugUnitTest` : [Premier test unitaire prévu en E1 (SmokeTest)]
- Classes de test et nombre de tests par classe : [0 classe en E0]
- Captures : liste des fichiers de app/src/test/screenshots/ et docs/rapport_gemini/compare/ : [0 capture en E0]

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
