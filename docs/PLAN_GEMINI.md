# Plan d'exécution TMM Mobile V1

Destinataire : l'agent qui écrit le code (Gemini). Rédigé le 26/09/2026. Référence fonctionnelle : `docs/CAHIER_DES_CHARGES.md` (appelé CdC). Référence visuelle : `maquettes/` (appelée « la maquette »).

Ce document ne te demande **aucune décision de conception**. Tout ce qui se voit à l'écran est fixé par la maquette et par les règles de traduction de la section 4. Tout ce qui ne se voit pas est fixé par les contrats de la section 5. Tu suis les étapes de la section 6 dans l'ordre, tu écris les tests demandés, tu remplis le rapport de la section 9.

---

## 0. Règles d'exécution (à relire avant chaque étape)

| # | Règle |
|---|---|
| R1 | Tu exécutes, tu ne conçois pas. Aucun élément d'interface, champ de protocole, écran, option, texte, couleur, taille ou animation qui ne soit pas dans la maquette, le CdC ou ce plan. |
| R2 | Avant d'écrire du code, lis en entier : ce plan, le CdC, `maquettes/README.md`, les 13 fichiers `maquettes/*.dc.html` (gabarit **et** script) et regarde les 13 images `maquettes/captures/*.png`. |
| R3 | Ne modifie jamais `docs/CAHIER_DES_CHARGES.md`, `docs/PLAN_GEMINI.md`, ni rien sous `maquettes/`. |
| R4 | Git : crée la branche `feat/v1-android` depuis `main`. Un commit par étape (ou sous-étape quand le plan en définit), message **exactement** celui indiqué dans l'étape. Pas de `push`, pas de `--force`, pas de réécriture d'historique. L'utilisateur pousse lui-même. |
| R5 | À partir de E1, avant chaque commit : `./gradlew testDebugUnitTest` doit passer. Si un test échoue, corrige le code, pas le test. Seule exception : le test contredit ce plan, alors note-le dans le rapport (section « Écarts »). |
| R6 | Versions de dépendances : jamais de mémoire. Pour chaque artefact, relève la dernière version **stable** sur la source officielle indiquée en E0, note l'URL consultée et la version dans le rapport. |
| R7 | API externes (Drive REST v3, autorisation Google, WorkManager en premier plan, SAF, ProtoBuf kotlinx) : lis la page de documentation officielle avant de coder, note son URL dans le rapport. Si la doc contredit ce plan sur un nom de méthode ou un paramètre, suis la doc et note l'écart. |
| R8 | Mihon : lis le code source sur `https://github.com/mihonapp/mihon` à un commit fixe de la branche par défaut. Note le SHA du commit et le chemin de chaque fichier lu dans le rapport. Ne devine aucune règle Mihon. |
| R9 | Blocage (outil absent, doc contradictoire, cas non couvert) : ajoute une entrée `BLOQUÉ-n` dans le rapport (étape, problème, ce que tu as essayé, contournement appliqué **parmi ceux que ce plan autorise**), puis passe à la tâche suivante. Si aucun contournement n'est autorisé : n'implémente pas l'élément concerné. N'invente jamais. |
| R10 | Pas d'« amélioration » : pas de fonctionnalité en plus, pas de bibliothèque hors liste E0, pas de composant Material par défaut là où ce plan impose un composant maison, pas de refactorisation d'une étape déjà validée sauf si une étape suivante l'exige explicitement. |
| R11 | Langues : identifiants de code en anglais. Textes d'interface en français, copiés à l'identique de la maquette, tous dans `res/values/strings.xml`. Commentaires de code rares, en français. |
| R12 | Aucun secret dans le dépôt : `keystore.properties` et `*.jks` ignorés par git. Aucune clé d'API dans l'app. Aucun jeton ni URL signée dans les journaux. |
| R13 | Données factices uniquement dans les tests et aperçus : `vous@gmail.com`, `PC-BUREAU`, séries et chiffres des maquettes. |
| R14 | Le rapport `docs/RAPPORT_GEMINI.md` est mis à jour à la fin de **chaque** étape et inclus dans le commit de l'étape. |
| R15 | Seules libertés : noms de fonctions et variables privées, découpage interne d'un fichier imposé, ordre des fonctions privées. Tout le reste est fixé. |
| R16 | Si ce plan et la maquette divergent sur un point visuel, la maquette gagne. Si ce plan et le CdC divergent sur un point non visuel, ce plan gagne (il est plus récent et plus précis) ; note l'écart dans le rapport. |

---

## 1. Le produit en 12 lignes (contexte seulement)

- App Android native, Kotlin, Compose. Nom affiché : **TMM Mobile**. `applicationId` : `com.tmm.mobile`.
- Elle lit la bibliothèque de l'app de lecture **Mihon** sur le même téléphone : sauvegardes `.tachibk` (catalogue, catégories) et dossiers `downloads/` (chapitres téléchargés), via le Storage Access Framework (SAF).
- Elle envoie des chapitres à traduire au PC (app TMM-Win, hors de ce dépôt) par un dossier Google Drive `TranslateMyManga/` (CdC section 7). Elle ne traduit rien elle-même.
- Elle suit la file du PC (fichiers `status.json`, `heartbeat.json`), récupère les chapitres traduits et les installe dans `Mihon/local/`.
- Elle modifie à distance les réglages de traduction du PC (CdC 7.5).
- Toute la V1 fonctionne sans le PC côté tests : un faux Drive et un faux PC (étape E7) simulent l'autre côté.

---

## 2. Environnement

### 2.1 Outils requis (vérifiés en E0)

| Outil | Vérification | Si absent |
|---|---|---|
| JDK 17 ou plus | `java -version` | `BLOQUÉ` : l'utilisateur doit installer un JDK 17 (Temurin). Arrête-toi. |
| Android SDK | variable `ANDROID_HOME` ou `ANDROID_SDK_ROOT`, dossier `platforms/` présent | `BLOQUÉ` : l'utilisateur doit installer Android Studio ou les command-line tools. Arrête-toi. |
| Gradle wrapper | créé en E1 | Si `gradle` n'est pas installé pour générer le wrapper : télécharge la distribution depuis `https://services.gradle.org/distributions/` (version relevée en E0), décompresse-la dans le scratchpad hors dépôt, lance `gradle wrapper --gradle-version <v>`. |
| Python 3 | `python --version` | Facultatif (seulement pour servir les maquettes). |

### 2.2 Commandes de référence

Toutes lancées à la racine du dépôt :

```bash
./gradlew testDebugUnitTest
./gradlew recordRoborazziDebug
./gradlew lintDebug
./gradlew assembleDebug
```

Sous Windows sans bash : `gradlew.bat` avec les mêmes tâches.

---

## 3. Arborescence cible (fixe)

```
.gitignore
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
gradle/wrapper/...
keystore.properties.example
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/plurals.xml          (les <plurals> vont ici)
app/src/main/res/values/themes.xml           (thème de fenêtre minimal)
app/src/main/res/font/*.ttf
app/src/main/res/mipmap-*/ic_launcher*        (icône, voir E2)
app/src/main/java/com/tmm/mobile/
  TmmApp.kt                       Application, crée AppContainer
  MainActivity.kt                 edge-to-edge, setContent { TmmRoot() }
  AppContainer.kt                 injection manuelle
  core/
    AppLog.kt                     journal en fichier tournant
    Clock.kt                      interface Clock { fun now(): Instant } + SystemClock
    Formatters.kt                 relativeFr, sinceFr, sizeFr, chapterRangeFr
    Hashing.kt                    sha256 de fichier et de flux
    ReducedMotion.kt              lecture ANIMATOR_DURATION_SCALE
  data/
    db/TmmDatabase.kt
    db/Entities.kt
    db/Daos.kt
    db/Converters.kt
    prefs/AppPrefs.kt             DataStore Preferences
    status/ChapterStatus.kt       dérivation CdC 6.3 (fonction pure)
    status/SeriesSummary.kt       compteurs par série (fonction pure)
  mihon/
    MihonTree.kt                  accès SAF DocumentsContract
    BackupModels.kt               modèles protobuf repris de Mihon
    BackupReader.kt               gunzip + décodage
    BackupImporter.kt             écriture Room en une transaction
    MihonNaming.kt                règle de nettoyage des noms reprise de Mihon
    DownloadScanner.kt
    Matcher.kt                    rapprochement 8.5
    CbzPacker.kt
    LocalInstaller.kt
  protocol/
    ProtocolModels.kt             @Serializable, CdC 7.2 et 7.5
    ProtocolJson.kt               instance Json configurée
  transport/
    JobTransport.kt               interface (CdC 4.3)
    DriveApi.kt                   appels REST Drive v3 via OkHttp
    DriveTransport.kt             implémentation de JobTransport
    GoogleAuth.kt                 autorisation Google, jeton d'accès
  jobs/
    JobRepository.kt              création, dédoublonnage, actions
    RefreshCoordinator.kt         rafraîchissement anti-spam
    SettingsSync.kt               CdC 7.5 côté téléphone
    Startup.kt                    séquence de lancement
    workers/SendJobWorker.kt
    workers/InstallJobWorker.kt
    workers/OutboxWorker.kt
    workers/WorkerSupport.kt      notification de premier plan, contraintes
  ui/
    TmmRoot.kt                    NavHost + barre basse + bandeaux
    nav/Routes.kt
    theme/TmmColors.kt
    theme/TmmType.kt
    theme/TmmTheme.kt
    theme/Motion.kt               modificateurs d'animation de la maquette
    icons/TmmIcons.kt
    components/                   un fichier par composant (liste en 4.9)
    mock/MockupData.kt            données des maquettes pour aperçus et captures
    onboarding/  home/  library/  series/  transfers/  queue/  catalog/
    settings/  translationsettings/  permission/  newtranslation/
      chaque dossier : XxxScreen.kt (sans état), XxxState.kt, XxxViewModel.kt, XxxRoute.kt
app/src/test/java/com/tmm/mobile/...        tests JVM (JUnit4 + Robolectric)
app/src/test/resources/...                  fixtures
docs/RAPPORT_GEMINI.md
docs/rapport_gemini/compare/*.png           comparaisons maquette / rendu
```

Correspondance écrans / dossiers : Onboarding `onboarding`, Main `home`, Library `library`, Series `series`, Sync `transfers`, Queue `queue`, Catalog `catalog`, Settings `settings`, TranslationSettings `translationsettings`, Permission `permission`, NewTranslation `newtranslation`, Empty `home` (état de l'écran Accueil), Offline `components/OfflineBanner.kt` + `home` (état).

---

## 4. Traduire une maquette en Compose (règles mécaniques)

Méthode pour chaque écran : ouvre le `.dc.html`, parcours le gabarit `<x-dc>` **de haut en bas**, et pour chaque élément HTML écris l'équivalent Compose selon les tables ci-dessous, avec **les valeurs exactes** de ses styles en ligne. Les valeurs calculées (`{{ x }}`) se lisent dans `renderVals()` du script.

### 4.1 Unités

| Maquette | Compose |
|---|---|
| `N px` (taille, marge, rayon, bordure) | `N.dp` |
| `font-size: N px` | `N.sp` |
| `letter-spacing: X em` | `X.em` |
| `letter-spacing: N px` | `N.sp` |
| `line-height: X` (sans unité) | `(X * fontSize).sp` |
| `line-height: N px` | `N.sp` |
| pas de `line-height` | `TextUnit.Unspecified` |
| `opacity: X` | `Modifier.alpha(X)` |
| durées `.Ns` | `N * 1000` ms entiers |

Toutes les `TextStyle` ont `platformStyle = PlatformTextStyle(includeFontPadding = false)` et `lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)`.

### 4.2 Mise en page

| CSS | Compose |
|---|---|
| `display:flex` (sens par défaut, ligne) | `Row` |
| `display:flex; flex-direction:column` | `Column` |
| `gap: N` | `Arrangement.spacedBy(N.dp)` sur l'axe principal |
| `justify-content: center / space-between / flex-end` | `Arrangement.Center / SpaceBetween / End` (ou `Bottom` en colonne). Combiné à `gap` : `Arrangement.spacedBy(N.dp, Alignment.CenterHorizontally)` etc. |
| `align-items: center / flex-start / flex-end / baseline` | `Alignment.CenterVertically / Top / Bottom` (Row) ou `CenterHorizontally / Start / End` (Column) ; `baseline` : `Modifier.alignByBaseline()` sur chaque enfant texte |
| `flex-grow:1` ou `flex:1` ou `flex-basis:0` + `flex-grow:1` | `Modifier.weight(1f)` |
| `flex-shrink:0` | aucun `weight`, taille intrinsèque |
| `min-width:0` | rien |
| `flex-wrap: wrap` | `FlowRow` avec `horizontalArrangement` et `verticalArrangement` = `spacedBy(gap)` |
| `display:grid; grid-template-columns: repeat(K, minmax(0,1fr))` | liste courte : `Column` de `Row` de K cellules `weight(1f)`, `spacedBy(gap)` sur les deux axes. Grille de séries de la Bibliothèque : `LazyVerticalGrid(GridCells.Fixed(3))` |
| `grid-template-columns: 64px minmax(0,1fr) 64px minmax(0,1fr) 64px` | `Row` : `width(64.dp)`, `weight(1f)`, `width(64.dp)`, `weight(1f)`, `width(64.dp)` |
| `position:relative` + enfant `position:absolute; top/right/bottom/left` | `Box` parent ; enfant avec `Modifier.align(...)` puis `offset(...)` reproduisant les distances |
| `width:100%` | `fillMaxWidth()` |
| `width: N px` (N < 200) | `width(N.dp)` |
| `width: N px` (N >= 200) | `fillMaxWidth()` (la maquette fait 390 de large ; ne fige jamais une largeur de plus de 200) |
| `height: N` | `height(N.dp)` |
| `min-height: N` | `heightIn(min = N.dp)` |
| `max-height: N` | `heightIn(max = N.dp)` |
| `min-width: N` | `widthIn(min = N.dp)` |
| `aspect-ratio` ou hauteur de couverture | `aspectRatio(...)` avec la valeur de la maquette |
| `overflow:hidden` | `clip(shape)` |
| classe `scroll` (écran) | `Column(Modifier.verticalScroll(...))`, sauf listes de données de taille variable : `LazyColumn` |
| classe `hscroll` | `LazyRow` pour des données, `Row(Modifier.horizontalScroll(...))` pour une liste fixe |
| `margin: …` | `Modifier.padding(...)` placé **avant** le fond (voir 4.3) |
| `padding: …` | `Modifier.padding(...)` placé **après** fond et bordure |
| `border-radius: N` | `RoundedCornerShape(N.dp)` ; `border-radius: 999px` ou demi-hauteur : `CircleShape` ou `RoundedCornerShape(50)` |
| `border: W solid C` | `Modifier.border(W.dp, C, shape)` |
| `border: W dashed C` | `Modifier.dashedBorder(W.dp, C, shape)` (composant maison, trait 4 dp, espace 3 dp) |
| `border-top` / `border-bottom` / `border-left` seuls | `Modifier.drawBehind { drawLine(...) }` sur le bord concerné, épaisseur exacte |
| `box-shadow: 0 Y B rgba(...)` | `Modifier.shadow(elevation = (B / 2).dp, shape, ambientColor = c, spotColor = c)`, plafonné à 16 dp |
| `text-overflow: ellipsis; white-space: nowrap` | `maxLines = 1, overflow = TextOverflow.Ellipsis` |
| `white-space: nowrap` seul | `maxLines = 1, softWrap = false` |
| `text-align: center` | `textAlign = TextAlign.Center` |
| `text-transform: uppercase` | `.uppercase(Locale.FRENCH)` à l'affichage ; la ressource garde la casse de la maquette |
| `text-decoration: none` | rien |
| `z-index` | ordre de dessin dans le `Box` (le plus haut en dernier) |
| `display:none` / `sc-if` faux | l'élément n'est pas composé |
| `cursor`, `outline`, `:hover`, classe `lift` | ignorés (écran tactile) |

### 4.3 Ordre fixe des modificateurs

```
Modifier
  .<marges externes: padding>          // margin
  .<taille: size/width/height/fillMaxWidth/weight/aspectRatio>
  .<animation d'entrée: rise/pop/...>  // section 4.7
  .shadow(...)
  .clip(shape)
  .background(color, shape)
  .border(...) / .dashedBorder(...)
  .<interaction: pressScale + clickable/toggleable/combinedClickable>
  .<padding interne>                    // padding
```

`box-sizing: border-box` est le cas partout : la taille inclut bordure et padding, ce que donne cet ordre.

### 4.4 Texte et polices

| Classe ou style | Famille |
|---|---|
| aucune classe | IBM Plex Sans (héritée du `body`) |
| `.d` | Bricolage Grotesque |
| `.m` | IBM Plex Mono (graisse 500) |

Graisse : `font-weight` exact (400, 500, 600, 800). Couleur : `color` exact. Taille, interlettrage, interligne : section 4.1.

API unique (fichier `ui/theme/TmmType.kt`) :

```kotlin
object TmmType {
    fun sans(size: Int, weight: Int = 400, lineHeight: Float? = null, letterSpacingEm: Float? = null): TextStyle
    fun display(size: Int, weight: Int = 800, lineHeight: Float? = null, letterSpacingEm: Float? = null): TextStyle
    fun mono(size: Int, weight: Int = 500, lineHeight: Float? = null, letterSpacingEm: Float? = null): TextStyle
}
```

`lineHeight` est le multiplicateur sans unité de la maquette (ou `null`). Les écrans n'appellent jamais `TextStyle(...)` directement.

Bricolage Grotesque est chargée par `@font-face` avec l'axe `opsz` en automatique (`font-optical-sizing: auto`) : le navigateur règle `opsz` à la taille de police. Reproduction : si le fichier retenu en E2 est variable, `display()` construit la police avec `FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", size.coerceIn(12, 96).toFloat()))` (fonctionne dès l'API 26). Mets en cache une `FontFamily` par couple (graisse, taille) dans une `HashMap`.

### 4.5 Couleurs

- Fichier unique `ui/theme/TmmColors.kt`. Il déclare **toutes** les couleurs hexadécimales et rgba présentes dans les maquettes, sous deux formes :
  1. une constante brute par valeur : `val C5E5A52 = Color(0xFF5E5A52)` ; pour rgba : `val Ink28 = Color(0x16, 0x16, 0x1A).copy(alpha = 0.28f)` avec le nom `Ink` + alpha en centièmes, `White55`, `Green55`, `Green0` pour `rgba(47,125,79,…)` ;
  2. les alias sémantiques du CdC 10.1 (`ink`, `paper`, `paperRaised`, `surface`, `line`, `lineStrong`, `track`, `textMuted`, `textOnInkMuted`, `textOnInkMuted2` pour `#E4DED0`, `accent`, `accentPressed`, `dangerBg`, `dangerText`, `info`, `infoBg`, `success`, `absent`, `absentText`).
- Un écran utilise l'alias s'il existe pour la valeur, sinon la constante brute.
- Inventaire relevé dans les maquettes (à reproduire intégralement) : `#5E5A52 #16161A #FFFFFF #C8391F #F4F1EA #DDD6C8 #ECE7DC #2B4C7E #D5CDBC #2F7D4F #7A2210 #CFC8B8 #FBF9F4 #FBE3DC #E9E4D8 #E3E9F2 #9E2A14 #2E2D29 #D8D2E4 #CFD8C6 #9C9486 #E2C6BB #E6DCC0 #C9D3DE #E8F1EB #B9B0A0 #9BD3AE #F4B3A3 #A9A293 #DCCFC4 #3F6B50 #FBF1D6 #E4DED0 #8A5A00 #3A3934`, et `rgba(47,125,79,0 | .55)`, `rgba(255,255,255,.55 | .22 | .18 | .85)`, `rgba(22,22,26,.28 | .08 | .25 | .2 | .45 | .12 | .06)`. Si tu trouves une valeur absente de cette liste en lisant un script, ajoute-la et signale-la dans le rapport.
- `transparent` : `Color.Transparent`.
- Test de garde (E2) : aucune occurrence de `Color(0x` ni `Color(` avec des littéraux hors de `ui/theme/`.
- Palette de couvertures (placeholders) : `listOf(C9D3DE, E2C6BB, CFD8C6, D8D2E4, E6DCC0, DCCFC4)`. Couleur d'une série réelle : `palette[(seriesKey.hashCode() and 0x7fffffff) % 6]`.

### 4.6 Icônes

- Aucune icône Material (`androidx.compose.material.icons` interdit, test de garde en E2).
- Fichier `ui/icons/TmmIcons.kt`. Chaque icône est une liste d'éléments SVG copiés **à l'identique** des maquettes : `path d`, `rect x y width height rx`, `circle cx cy r`, avec leurs attributs `fill`, `stroke`, `viewBox` (24 × 24 sauf mention contraire).
- Conversion : `path` via `addPathNodes(d)` (paquet `androidx.compose.ui.graphics.vector`). `rect` sans `rx` : `M x,y h w v h h -w Z`. `rect` avec `rx` : chemin à 4 arcs `A rx rx 0 0 1`. `circle` : deux arcs `M cx-r,cy a r,r 0 1,0 2r,0 a r,r 0 1,0 -2r,0`.
- Rendu : `ImageVector.Builder(defaultWidth = size.dp, defaultHeight = size.dp, viewportWidth = 24f, viewportHeight = 24f)`, chemins avec `stroke = SolidColor(Color.Black)`, `strokeLineWidth` = `stroke-width` de la maquette à cet endroit, `strokeLineCap = Round`, `strokeLineJoin = Round`, `fill` = `null` si `fill="none"`. Couleur appliquée par `tint`.
- API : `@Composable fun TmmIcon(icon: TmmIconId, size: Dp, tint: Color, strokeWidth: Float = 1.8f, contentDescription: String? = null)`. `TmmIconId` est un `enum` dont chaque entrée porte la liste d'éléments. Les `ImageVector` sont mémorisés par (icône, taille, trait).
- Noms d'entrées : le nom utilisé dans l'objet `I` du script de la maquette quand il existe (`up`, `down`, `check`, `redo`...), sinon le nom anglais de ce que l'icône représente, en `camelCase`. Liste complète dans le rapport avec le fichier maquette d'origine.
- Épaisseurs relevées : 1.5, 1.6, 1.8 (majoritaire), 2, 2.2, 2.4, 2.6, 3. Toujours celle de la maquette à l'endroit utilisé.

### 4.7 Animations (bloc `<helmet>` des maquettes)

Fichier `ui/theme/Motion.kt`. Courbes :

```kotlin
val TmmEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)     // cubic-bezier(.2,.8,.2,1)
val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)    // ease
val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)      // ease-out
val CssEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f) // ease-in-out
// linear = LinearEasing
```

Chaque classe CSS devient un modificateur ou un composable, **avec ces valeurs exactes** :

| Classe | Implémentation Compose |
|---|---|
| `rise` (+ `r1`..`r5`) | `Modifier.rise(delayMs)` : au premier affichage, alpha 0 -> 1 et translationY 14 dp -> 0, 550 ms, `TmmEase`. Délais : r1 50, r2 120, r3 190, r4 260, r5 330 ms ; `animation-delay` en ligne : valeur exacte (ex. `i * 0.04s` = `i * 40` ms) |
| `pop` | `Modifier.pop()` : échelle 0.5 -> 1.12 (à 60 %) -> 1, alpha 0 -> 1 (à 60 %), 450 ms, `TmmEase`, via `keyframes` |
| `toast` | alpha 0 -> 1, translationY 16 dp -> 0, 350 ms, `TmmEase` |
| `pill` | scaleX 0.4 -> 1, alpha 0.3 -> 1, 450 ms, `TmmEase`, origine au centre |
| `sheet` | translationY 100 % de la hauteur -> 0, 500 ms, `TmmEase` |
| `fade` | alpha 0 -> 1, 400 ms, `CssEase` |
| `slide` | alpha 0 -> 1, translationX 28 dp -> 0, 450 ms, `TmmEase` |
| `grow` | scaleX 0 -> 1, origine à gauche (`TransformOrigin(0f, 0.5f)`), 1100 ms, `TmmEase`, délai 200 ms |
| `spin` | rotation 0 -> 360 infinie, 900 ms, `LinearEasing` |
| `pulse` | anneau `success` dessiné autour : rayon supplémentaire 0 -> 7 dp et alpha 0.55 -> 0 de 0 à 70 % du cycle, puis rien jusqu'à 100 % ; cycle 2000 ms, `CssEaseOut`, infini |
| `stripes` | bandes diagonales -45°, `White22` sur 6 dp puis transparent 6 dp, motif de 24 dp qui glisse de 24 dp en 900 ms, linéaire, infini |
| `shimmer` | bande dégradée horizontale transparent / `White55` / transparent de 200 dp de large, qui passe de x = -200 dp à +200 dp en 1600 ms, `CssEaseInOut`, infini |
| `float` | translationY 0 -> -6 dp -> 0, 3200 ms, `CssEaseInOut`, infini |
| `blink` | alpha 1 -> 0.35 -> 1, 1400 ms, `CssEaseInOut`, infini |
| `travel` | point de 8 dp qui va de x = 0 à x = largeur - 8 dp en 1800 ms linéaire, alpha 0 (0 %) -> 1 (15 %) -> 1 (85 %) -> 0 (100 %), infini |
| `press` | `Modifier.pressScale(interactionSource)` : échelle 0.97 pendant l'appui, 150 ms `CssEase`. La même `interactionSource` est passée au `clickable`. |
| `tone` | `Modifier.halftone()` : `drawBehind`, points de couleur `Ink28`, rayon 1.15 dp, centrés en (2.5 + 5i, 2.5 + 5j) dp |
| `transition: <prop> Ns <courbe>` en ligne | `animateXAsState(target, tween(N ms, courbe))` sur la propriété concernée (largeur de barre : `animateFloatAsState` de la fraction ; couleur : `animateColorAsState` ; transform : `animateFloatAsState`) |
| anneau SVG `stroke-dasharray` / `stroke-dashoffset` (bouton Rafraîchir) | `Canvas` + `drawArc`, progression animée par `animateFloatAsState(tween(1000, LinearEasing))` |
| compteurs animés (`requestAnimationFrame`, `1 - (1-k)^3`) | `Animatable(0f).animateTo(cible, tween(durée, easing = { 1 - (1 - it).pow(3) }))`, valeur affichée arrondie |

Mouvement réduit : `core/ReducedMotion.kt` lit `Settings.Global.ANIMATOR_DURATION_SCALE` (0 = réduit) et le fournit par `LocalReducedMotion`. Quand il vaut vrai, tous ces modificateurs affichent directement l'état final, les animations infinies sont statiques, les transitions sont instantanées (comme `@media (prefers-reduced-motion:reduce)` de la maquette).

Pas d'effet d'ondulation (ripple) nulle part : `TmmTheme` fournit `LocalIndication provides NoIndication` (objet maison qui ne dessine rien). Le seul retour visuel d'appui est `pressScale` là où la maquette a la classe `press`.

### 4.8 Données dynamiques des scripts

- Les tableaux et objets de données d'un script (ex. `all` dans `Library.dc.html`) sont recopiés **tels quels** dans `ui/mock/MockupData.kt` pour les aperçus et captures.
- Les `setTimeout` / `setInterval` qui **simulent** une opération (réseau, scan) sont remplacés par l'opération réelle. Quand ils **échelonnent l'apparition** de résultats (ex. coches de dossiers une par une), garde un délai minimum identique entre deux apparitions.
- Les durées d'affichage sont conservées telles quelles : toasts 3400 ms (Accueil), 3600 ms (Bibliothèque, Fiche série), valeurs exactes des autres scripts ; anti-spam 30 s.
- Les chaînes construites dans les scripts (ex. `plural(n, 'chapitre') + ' ajouté' + ...`) deviennent des ressources `plurals` ou des chaînes formatées qui produisent **exactement** le même texte pour les mêmes valeurs. Règle du pluriel de la maquette : pluriel si n > 1 (0 et 1 au singulier). Android français range 0 et 1 dans `one` : utilise `quantity="one"` et `quantity="other"`, et vérifie par test que 0, 1, 2 donnent le texte du script.

### 4.9 Composants maison (dossier `ui/components/`, un fichier chacun)

Chaque composant reproduit l'élément de maquette cité, valeurs exactes. Aucun composant Material 3 visible n'est utilisé, sauf `PullToRefreshBox` (indicateur recoloré `ink` sur `surface`) et les primitives (`Text`, `BasicTextField`, `LazyColumn`...).

| Composant | Source dans la maquette |
|---|---|
| `TmmBottomBar` | barre de navigation de toutes les maquettes à onglets : hauteur 76, padding 6/6/14/6, bordure haute `#DDD6C8`, fond `#FBF9F4` ; onglet `weight(1f)`, colonne, gap 4, libellé 11 sp (600 `#16161A` actif, 500 `#5E5A52` inactif) ; pilule active 52 × 30, rayon 15, fond `#16161A`, icône `#F4F1EA`, animation `pill` à chaque changement d'onglet ; icônes 20, trait 1.8 ; pastille de Transferts : mono 10, min 16 × 16, rayon 8, fond `#C8391F`, texte blanc, en haut à 0 et à 6 dp du bord droit de la zone d'icône |
| `TmmSwitch` | `role="switch"` de `Settings.dc.html` : 52 × 32, pouce de 24, décalé de 20 dp quand actif, coche dans le pouce quand actif, couleurs et transition de la maquette |
| `TmmCheckbox` | cases des maquettes Bibliothèque (cercle sur tuile) et Fiche série / Feuille F (carré) : deux variantes, valeurs exactes de chacune |
| `TmmSegmented` | sélecteur Toutes / À traduire / Non téléchargées (Bibliothèque) et onglets de la File : fond, ombre `0 1px 3px rgba(22,22,26,.12)` sur l'actif |
| `TmmChip` | puces de catégories (Bibliothèque) |
| `TmmButton` | bouton principal (48, `accent`, 15/600), secondaire (bordure `line`), bouton d'action 2 × 2 (Bibliothèque, Fiche série) avec pastille d'icône, libellé et indication |
| `TmmSearchField` | champ de recherche Bibliothèque et Feuille F, `BasicTextField` |
| `TmmSheet` | feuille modale maison : voile (`fade`), panneau (`sheet`), ombre `0 -12px 40px rgba(22,22,26,.2)`, fermeture par voile et par retour système (`BackHandler`) ; sémantique `paneTitle` = titre |
| `TmmDialog` | boîte de confirmation « Retraduire » (Bibliothèque, Fiche série), même mécanique que `TmmSheet` mais centrée, animation `pop` |
| `TmmToast` | `role="status"` : ancré en bas au-dessus de la barre, animation `toast`, `liveRegion = Polite`, lien d'action à droite |
| `OfflineBanner` | bandeaux de `Offline.dc.html` : variante `NoNetwork` (`role="status"`) et `TokenExpired` (`role="alert"` + bouton « Reconnecter ») ; variante `ProtocolTooNew` : même style que `NoNetwork`, texte `Le PC utilise une version plus récente du protocole. Mettez à jour l'app.`, sans bouton |
| `Cover` | couverture : `AsyncImage` Coil sur fond de la couleur de placeholder + `halftone()` ; en échec ou sans URL, placeholder seul ; rayon et ratio de la maquette |
| `StatusBadge` | badges d'état et de mode (couleurs du tableau `tone` de la Bibliothèque et `T` de l'Accueil) |
| `PresenceIcons` | trois indicateurs téléphone / PC / FR de la Fiche série (plein, pointillés, badge FR), avec `contentDescription` des `title` de la maquette |
| `ProgressBar` | barres fines (FR x/y, jobs) : fond `track`, remplissage animé |
| `RefreshButton` | bouton Rafraîchir de l'Accueil : icône qui tourne (`spin`) pendant l'exécution, puis anneau de 30 s décompté et nombre de secondes |
| `SectionLabel` | étiquettes de section en majuscules |
| `DashedBorder.kt` | `Modifier.dashedBorder(width, color, shape)` : trait 4 dp, espace 3 dp |

### 4.10 Textes

- Toute chaîne visible ou lue par TalkBack est dans `strings.xml` / `plurals.xml`. Noms de ressources : `<écran>_<rôle>` en `snake_case` (`home_title`, `library_pick_label`...), `common_` pour les textes partagés.
- Guillemets et apostrophes : copie les caractères de la maquette (`’`, `« »`, espaces insécables comprises). Échappe `'` ASCII en `\'`.
- Fichier de correspondance obligatoire `app/src/test/resources/mockup_texts.tsv`, une ligne par texte littéral de chaque gabarit `<x-dc>` (texte entre balises sans `{{`, et valeurs littérales des attributs `aria-label`, `title`, `placeholder`). Colonnes séparées par tabulation :
  `fichier` `texte exact` `type` `ressource` `quantité` `arguments`
  - `type` = `STR` (ressource simple), `FMT` (chaîne formatée ; `arguments` = liste JSON des valeurs qui reproduisent le texte de la maquette), `PLU` (plurals ; `quantité` = n), `DEMO` (donnée factice qui vient de la base au lieu d'une ressource, ex. `PC-BUREAU`, `Blue Lock`, `vous@gmail.com` ; `ressource` vide), `SPLIT` (texte scindé en plusieurs ressources ; `ressource` = noms séparés par `+`).
- Test `MockupTextCoverageTest` (E4) : extrait les textes de chaque gabarit, vérifie que chaque texte a une ligne dans le TSV, et que la ressource indiquée, formatée avec les arguments, reproduit **exactement** le texte. Les lignes `DEMO` sont listées dans le rapport pour revue.

### 4.11 Accessibilité (CdC 10.6)

| Maquette | Compose |
|---|---|
| `aria-label` / `title` | `contentDescription` ou `semantics { contentDescription = ... }` |
| `role="checkbox"` + `aria-checked` | `Modifier.toggleable(value, role = Role.Checkbox)` |
| `role="switch"` | `toggleable(role = Role.Switch)` |
| `role="radio"` / `radiogroup` | `selectable(role = Role.RadioButton)` dans un `Modifier.selectableGroup()` |
| `role="tab"` / `tablist` | `selectable(role = Role.Tab)` |
| `role="dialog"` | `semantics { paneTitle = titre }` |
| `role="status"` | `semantics { liveRegion = LiveRegionMode.Polite }` |
| `role="alert"` | `liveRegion = LiveRegionMode.Assertive` |
| `role="progressbar"` | `progressSemantics(value, 1f..3f)` |
| `aria-expanded` | `stateDescription` « Déplié » / « Replié » |
| `disabled` | `enabled = false` |
| élément cliquable de moins de 48 dp | `Modifier.minimumInteractiveComponentSize()` (la zone tactile grandit, le dessin reste celui de la maquette) |

Tailles de police : les `sp` suivent le réglage système, aucun plafond.

### 4.12 Fenêtre et barres système

- `enableEdgeToEdge()` dans `MainActivity`, icônes de barre d'état sombres (`SystemBarStyle.light(scrim = paper, darkScrim = paper)`).
- Le contenu de chaque écran applique `WindowInsets.safeDrawing` : en haut sur l'en-tête, en bas sur la barre `TmmBottomBar` (ou le pied d'écran s'il n'y a pas de barre).
- Orientation : non verrouillée (la sélection doit survivre à la rotation, CdC 9.12).
- Mode sombre : non (CdC 10.7). Le thème ignore le mode sombre du système.

### 4.13 Captures et comparaison

- Chaque écran a un test Roborazzi dans `app/src/test/java/com/tmm/mobile/screenshots/` qui compose `XxxScreen(state = MockupData.xxx..., onEvent = {})` dans `TmmTheme`, avec :
  `@RunWith(RobolectricTestRunner::class)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `@Config(qualifiers = "w390dp-h844dp-xhdpi", sdk = [<plus haute API prise en charge par la version de Robolectric retenue>])`.
  Horloge : `composeRule.mainClock.autoAdvance = false`, puis `advanceTimeBy(3000)` avant la capture.
- Sortie : `app/src/test/screenshots/<Ecran>.png` (780 × 1688, comme `maquettes/captures/`).
- Le même test écrit `docs/rapport_gemini/compare/<Ecran>.png` : image de 1560 × 1688, maquette à gauche (`maquettes/captures/<Ecran>.png`), rendu à droite, via `javax.imageio.ImageIO` (aucune dépendance en plus).
- États capturés en plus de l'état initial (un fichier chacun, sans comparaison) : `Library_selection` (2 séries cochées), `Series_selection` (3 chapitres cochés), `Series_sheet`, `Queue_done`, `Queue_failed`, `Onboarding_step2`, `Onboarding_step3`, `NewTranslation_step2`, `TranslationSettings_sheet`, `Offline_token`, `Permission_restored`, `Home_toast`, `Library_confirm`.
- Après chaque écran : ouvre l'image de comparaison, liste chaque différence visible dans le rapport (section « Écarts visuels »), corrige tout ce qui n'est pas dû au moteur de rendu (antialiasing, crénage). Objectif : aucune différence de position, taille, couleur, texte ou icône.

---

## 5. Contrats de code

### 5.1 `AppContainer`

Créé une fois dans `TmmApp.onCreate`, exposé par `(application as TmmApp).container`. Il instancie, dans cet ordre : `Clock`, `AppLog`, `AppPrefs`, `TmmDatabase`, `MihonTree`, `BackupReader`, `BackupImporter`, `DownloadScanner`, `Matcher`, `CbzPacker`, `LocalInstaller`, `GoogleAuth`, `OkHttpClient` (un seul), `DriveApi`, `DriveTransport` (typé `JobTransport`), `JobRepository`, `RefreshCoordinator`, `SettingsSync`, `Startup`, `WorkManager`. Les workers récupèrent le conteneur par `applicationContext`. Les ViewModel sont créés par une `ViewModelProvider.Factory` par écran (`viewModelFactory { initializer { ... } }`) qui lit le conteneur.

### 5.2 Préférences (`AppPrefs`, DataStore Preferences)

Clés (toutes optionnelles) :

| Clé | Type | Contenu |
|---|---|---|
| `onboarding_done` | Boolean | onboarding terminé |
| `google_email` | String | e-mail affiché |
| `auth_expired` | Boolean | vrai si une autorisation silencieuse a demandé une interaction |
| `drive_root_id`, `drive_jobs_id`, `drive_pc_id`, `drive_commands_id` | String | identifiants de dossiers Drive |
| `drive_file_ids` | String (JSON `Map<String,String>`) | `"pc/heartbeat.json"` etc. -> id Drive |
| `last_changes_cursor` | String (ISO-8601) | plus grand `modifiedTime` vu au dernier rafraîchissement |
| `last_refresh_at` | Long (epoch ms) | fin du dernier rafraîchissement réussi |
| `heartbeat_json`, `index_updated_at`, `settings_json` | String | derniers `pc/heartbeat.json`, date de l'index, `pc/settings.json` reçus (JSON brut) |
| `pending_commands_json` | String (JSON `List<SettingsCommand>`) | commandes de réglages non confirmées, avec leur date d'envoi |
| `mihon_tree_uri` | String | URI SAF du dossier Mihon |
| `last_scan_at` | Long | fin du dernier scan |
| `retention` | String | `never` (défaut), `days30`, `after_receive` |
| `wifi_only` | Boolean | défaut `false` |
| `translate_on_arrival` | Boolean | défaut `true` (case de Transferts et de la Feuille F) |
| `protocol_too_new` | Boolean | PC annonce `protocol` > 1 |

### 5.3 Room (`TmmDatabase`, version 1, `exportSchema = true`, schémas dans `app/schemas/`)

Chemin Mihon encodé en une chaîne : `mihonPath = "$sourceDir/$seriesDir/$chapterDir"` (les noms de dossiers ne contiennent pas `/`). Clé série : `seriesKey = "$sourceId|$url"`. Clé chapitre : `chapterKey = "$seriesKey|$chapterUrl"`.

```kotlin
@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val seriesKey: String,
    val sourceId: String, val url: String, val title: String,
    val author: String?, val status: Int, val thumbnailUrl: String?,
    val sourceName: String, val dateAdded: Long,
    val mangaType: String?              // voir 5.6 « type de série », null si inconnu
)

@Entity(tableName = "category")
data class CategoryEntity(@PrimaryKey val order: Long, val name: String)

@Entity(tableName = "series_category", primaryKeys = ["seriesKey", "categoryOrder"],
        indices = [Index("categoryOrder")])
data class SeriesCategoryEntity(val seriesKey: String, val categoryOrder: Long)

@Entity(tableName = "chapter", indices = [Index("seriesKey")])
data class ChapterEntity(
    @PrimaryKey val chapterKey: String, val seriesKey: String, val url: String,
    val name: String, val number: Float, val scanlator: String?,
    val read: Boolean, val dateUpload: Long
)

@Entity(tableName = "downloaded_chapter", indices = [Index("seriesKey"), Index("chapterKey")])
data class DownloadedChapterEntity(
    @PrimaryKey val mihonPath: String,
    val sourceDir: String, val seriesDir: String, val chapterDir: String,
    val documentUri: String, val isCbz: Boolean, val sizeBytes: Long, val lastModified: Long,
    val pageCount: Int,                 // nombre d'images (ou d'entrées image du CBZ)
    val seriesKey: String?, val chapterKey: String?, val number: Float?
)

@Entity(tableName = "scanned_series_dir", primaryKeys = ["sourceDir", "seriesDir"])
data class ScannedSeriesDirEntity(val sourceDir: String, val seriesDir: String, val lastModified: Long)

@Entity(tableName = "local_fr_chapter", indices = [Index("mihonPath"), Index("jobId")])
data class LocalFrChapterEntity(
    @PrimaryKey val localPath: String,  // "$folderName/$fileName"
    val folderName: String, val fileName: String, val documentUri: String,
    val lastModified: Long, val jobId: String?, val mihonPath: String?
)

@Entity(tableName = "job", indices = [Index("mihonPath"), Index("phoneState")])
data class JobEntity(
    @PrimaryKey val jobId: String,
    val mode: String,                   // translate | verify | retranslate
    val pages: List<Int>?,              // base 0, verify seulement
    val translate: Boolean,
    val mihonPath: String, val sourceDir: String, val seriesDir: String, val chapterDir: String,
    val seriesKey: String?, val chapterKey: String?,
    val phoneState: String,             // voir 5.4
    val failedStep: String?,            // prepare | upload | request | download | install
    val errorMessage: String?,
    val driveFolderId: String?, val uploadSessionUrl: String?,
    val cachePath: String?, val sha256: String?, val sizeBytes: Long?,
    val transferredBytes: Long, val transferTotal: Long,
    val pcState: String?, val pcStage: String?, val pcPage: Int?, val pcPages: Int?,
    val pcEngine: String?, val pcQueuePosition: Int?,
    val pcErrorCode: String?, val pcErrorMessage: String?, val pcErrorPage: Int?,
    val statusUpdatedAt: String?,
    val resultJson: String?, val resultFileId: String?,
    val createdAt: Long, val updatedAt: Long,
    val finishedAt: String?,            // result.finishedAt
    val installedAt: Long?, val installedPath: String?,
    val receivedWrittenAt: Long?, val cancelWrittenAt: Long?, val driveDeletedAt: Long?
)

@Entity(tableName = "chapter_quality")
data class ChapterQualityEntity(
    @PrimaryKey val mihonPath: String,
    val totalPages: Int, val translatedBubbles: Int, val untranslatedBubbles: Int,
    val sfxKept: Int, val verifiedAt: String?, val alerts: List<QualityAlert>, val jobId: String
)

@Entity(tableName = "series_settings")
data class SeriesSettingsEntity(@PrimaryKey val seriesKey: String, val autoTranslate: Boolean, val targetLang: String = "FR")

@Entity(tableName = "ignored_gap", primaryKeys = ["seriesKey", "number"])
data class IgnoredGapEntity(val seriesKey: String, val number: Int)

@Entity(tableName = "backup_import")
data class BackupImportEntity(
    @PrimaryKey val id: Int = 0, val name: String, val size: Long, val lastModified: Long,
    val importedAt: Long, val source: String  // "autobackup" | "manual"
)

@Entity(tableName = "pc_index_chapter")
data class PcIndexChapterEntity(@PrimaryKey val mihonPath: String, val original: Boolean, val fr: Boolean)
```

`pc_index_chapter` est un cache de `pc/index.json` (remplacé en une transaction à chaque nouvel index), ajouté par ce plan au CdC 6.2. `Converters` : `List<Int>` et `List<QualityAlert>` en JSON via `ProtocolJson`.

DAO : un DAO par table dans `Daos.kt`, requêtes `Flow` pour l'interface, `suspend` pour les écritures. Les agrégats d'écran sont calculés en Kotlin (5.7) à partir de flux combinés, pas en SQL.

### 5.4 États d'un job côté téléphone

```kotlin
enum class PhoneState { PREPARING, UPLOADING, SENT, RESULT_READY, DOWNLOADING, INSTALLED, FAILED, CANCEL_REQUESTED }
```

Stocké par `name`. Transitions autorisées (toute autre est un bug, test en E8) :

```
PREPARING -> UPLOADING -> SENT            (translate)
PREPARING -> SENT                          (verify, retranslate : pas de fichier)
SENT -> RESULT_READY                       (result.json avec file non nul)
SENT -> INSTALLED                          (result.json avec file nul : stats seules)
RESULT_READY -> DOWNLOADING -> INSTALLED
PREPARING | UPLOADING | SENT -> CANCEL_REQUESTED   (« Retirer de la file »)
PREPARING | UPLOADING | DOWNLOADING -> FAILED
```

Un job est **actif** si `phoneState ∈ {PREPARING, UPLOADING, SENT, RESULT_READY, DOWNLOADING, CANCEL_REQUESTED}` **et** `pcState ∉ {failed, cancelled}`. Un job est **en échec** si `phoneState == FAILED` ou `pcState == failed`. Un job est **annulé** si `pcState == cancelled`.

### 5.5 Protocole (`protocol/ProtocolModels.kt`)

Recopie exacte des schémas du CdC 7.2 et 7.5, `@Serializable`, noms JSON identiques. `ProtocolJson = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }`. Dates : `String` ISO-8601 UTC (`Instant.toString()`). Classes : `RequestJson`, `SeriesRef`, `ChapterRef`, `MihonPath`, `FileRef`, `RequestOptions`, `StatusJson`, `JobError`, `ResultJson`, `Quality`, `QualityAlert`, `CancelJson`, `ReceivedJson`, `Heartbeat`, `QueueCounts`, `PcIndex`, `PcIndexChapter`, `PcSettings`, `SettingsValues`, `SettingsCommand`, `ProtocolMarker` (`{"protocol": 1}`).

`SettingsValues` : 14 champs typés (`String`, `Int`, `Boolean`) nommés comme les clés du CdC 7.5. `SettingsCommand.changes` : `JsonObject` (seulement les clés modifiées). Liste fermée des valeurs autorisées : objet `SettingsSchema` avec, par clé, la liste des valeurs (CdC 7.5, tableau), utilisé pour construire les feuilles de choix et pour refuser une valeur hors liste avant envoi.

Valeurs fixes des requêtes : `protocol = 1`, `device = Build.MODEL`, `options.sourceLang = "auto"`, `options.targetLang = "FR"`.

### 5.6 Mihon

- **Modèles de sauvegarde** (`BackupModels.kt`) : recopie depuis le dépôt Mihon les classes de sauvegarde (cherche `class Backup(` et les classes `BackupManga`, `BackupChapter`, `BackupCategory`, `BackupSource`), avec leurs `@ProtoNumber` **exacts**, en ne gardant que les champs : manga (`source`, `url`, `title`, `author`, `status`, `thumbnailUrl`, `dateAdded`, `chapters`, `categories`), chapitre (`url`, `name`, `scanlator`, `read`, `chapterNumber`, `dateUpload`), catégorie (`name`, `order`), source (`sourceId`, `name`). Garde le commentaire de licence Apache 2.0 en tête du fichier avec le lien et le SHA du fichier d'origine.
- `mangaType` de `SeriesEntity` : `null` en V1 (la maquette affiche « Manga » : utilise le texte fixe de la maquette, ressource `series_kind_manga`).
- **Catégories d'une série** : lis dans le code de restauration de Mihon (cherche `backupCategories` dans les classes de restauration) comment `BackupManga.categories` est relié aux catégories, et reproduis exactement ce lien. Note le fichier et le SHA.
- **Champs inconnus** : vérifie par test que le décodage ignore les champs en plus (forks). Si le décodeur de kotlinx les rejette, `BLOQUÉ` avec le message exact.
- **Nettoyage des noms** (`MihonNaming.kt`) : recopie la fonction de Mihon qui construit un nom de fichier valide (cherche `buildValidFilename`) et celles qui construisent les noms des dossiers de source, série et chapitre (cherche `getChapterDirName`, `getMangaDirName`, `getSourceDirName` dans le code des téléchargements). Recopie aussi la règle des dossiers de téléchargement incomplets (cherche le suffixe temporaire utilisé par le téléchargeur, par exemple `_tmp`). Tests avec les exemples lus dans le code ou ses tests.
- **Sources** : le nom de dossier source se compare à `BackupSource.name` nettoyé ; si la sauvegarde ne contient pas la source, le chapitre reste « hors catalogue » (CdC 8.5.4).

### 5.7 Dérivations (fonctions pures, `data/status/`)

```kotlin
enum class ChapterStatus { TO_SEND, QUEUED, TRANSLATING, FAILED, TO_FETCH, UP_TO_DATE, NOT_TRANSLATED, CATALOG_ONLY }

data class ChapterFacts(
    val downloaded: Boolean, val inCatalog: Boolean,
    val index: PcIndexChapterEntity?, val latestJob: JobEntity?, val localFr: Boolean
)

fun deriveStatus(f: ChapterFacts): ChapterStatus
fun translatingPercent(job: JobEntity): Int   // pcPage * 100 / pcPages, 0 si inconnu
```

`latestJob` = job le plus récent (`createdAt`) de ce `mihonPath`, en ignorant les jobs annulés. Ordre de décision (le premier vrai gagne) :

1. `latestJob` actif et `phoneState ∈ {PREPARING, UPLOADING, SENT, CANCEL_REQUESTED}` : `TRANSLATING` si `pcState == "translating"`, sinon `QUEUED`.
2. `latestJob` en échec : `FAILED`.
3. `latestJob.phoneState ∈ {RESULT_READY, DOWNLOADING}` : `TO_FETCH`.
4. `localFr` : `UP_TO_DATE`.
5. `index?.fr == true` : `TO_FETCH`.
6. `index?.original == true` : `NOT_TRANSLATED`.
7. `downloaded` : `TO_SEND`.
8. sinon : `CATALOG_ONLY`.

Libellés affichés : ceux de la maquette Fiche série pour chaque statut (lis `c.status` dans le script).

**Manquant partout** (CdC 6.3) : pour une série, `présents` = numéros entiers des chapitres téléchargés, installés en FR ou du catalogue ; `manquants` = entiers entre min et max de `présents` qui n'y sont pas et ne sont pas dans `ignored_gap`.

**Correspondance chapitre FR local sans jobId** (installé autrefois par câble) : un `LocalFrChapterEntity` sans `jobId` est relié au chapitre téléchargé de la même série dont le dossier `folderName` égale `MihonNaming.buildValidFilename(series.title)` et dont le premier nombre du `fileName` (regex `\d+(\.\d+)?`) égale `number`. `// ponytail: heuristique par titre et numéro, à remplacer si le pont Drive publie le chemin local.`

**`TO_FETCH` venant de l'index seul** (`index.fr` sans `result.json` connu) : il n'existe aucun résultat à télécharger. Ces chapitres sont affichés « À récupérer » mais **ne comptent pas** dans les actions « Récupérer » (compteur et bouton) ; signale ce cas dans le rapport, section « Points ouverts ».

**Résumé par série** (`SeriesSummary.kt`) :

```kotlin
data class SeriesSummary(
    val seriesKey: String, val toSend: Int, val toFetch: Int, val busy: Int,
    val frCount: Int, val onPhone: Int, val verifiable: Int, val onPc: Int, val missing: List<Int>
)
```

`toSend` = chapitres `TO_SEND` ; `toFetch` = chapitres dont le job est `RESULT_READY` ; `busy` = chapitres `QUEUED` ou `TRANSLATING` ; `frCount` = chapitres `UP_TO_DATE` ; `onPhone` = chapitres téléchargés ou installés en FR (ensemble des numéros distincts) ; `verifiable` = chapitres avec `index.original == true` et (`localFr` ou `index.fr`) ; `onPc` = chapitres avec `index.original == true`.

Badge de tuile (Bibliothèque), premier vrai : `busy > 0` -> `busy` « Traduction en cours » ; `toSend > 0` -> `send` « N à envoyer » ; `toFetch > 0` -> `get` « N à récupérer » ; `onPhone == 0` -> `absent` « Non téléchargée » ; sinon `ok` « À jour ». Barre `FR x/y` : `frCount / onPhone`, texte « Dans Mihon seulement » si `onPhone == 0`. Filtre « À traduire » : `send` ou `busy`. Filtre « Non téléchargées » : `absent`.

### 5.8 Formats (`core/Formatters.kt`, tests unitaires obligatoires)

| Fonction | Règle |
|---|---|
| `relativeFr(t, now)` | < 60 s : « à l'instant » ; < 60 min : « il y a N min » ; < 24 h : « il y a N h » ; sinon « il y a N jour(s) » (pluriel si N > 1) |
| `sinceFr(t, now)` | même découpage : « depuis N min », « depuis N h », « depuis N jour(s) » |
| `sizeFr(bytes)` | < 1 Go (10^9) : « N Mo » (10^6, arrondi à l'entier) ; sinon « X,Y Go » (une décimale, virgule) |
| `chapterRangeFr(numbers)` | un seul : « Ch. N » ; plusieurs : « Ch. min à max » ; numéros entiers sans décimale, sinon avec la décimale |
| `langLabelFr(code)` | `auto` « détection auto », `JPN` « japonais », `KOR` « coréen », `CHS` « chinois », `FRA` « français », `ENG` « anglais », `SPA` « espagnol », `DEU` « allemand » (si la maquette donne un autre libellé pour une valeur, la maquette gagne) |

### 5.9 Transport

```kotlin
interface JobTransport {
    suspend fun ensureLayout(): LayoutInfo                // crée ou retrouve les dossiers ; LayoutInfo(created: Boolean)
    suspend fun accountEmail(): String?
    suspend fun uploadSource(job: JobEntity, file: File, onSession: suspend (String) -> Unit,
                             onProgress: suspend (sent: Long, total: Long) -> Unit): String   // renvoie l'id du dossier du job
    suspend fun writeRequest(jobFolderId: String?, request: RequestJson): String               // crée le dossier si null, renvoie son id
    suspend fun writeCancel(job: JobEntity, cancel: CancelJson)
    suspend fun writeReceived(job: JobEntity, received: ReceivedJson)
    suspend fun writeCommand(command: SettingsCommand)
    suspend fun poll(since: String?): PollResult
    suspend fun readSettings(): PcSettings?
    suspend fun downloadResult(job: JobEntity, fileId: String, dest: File,
                               onProgress: suspend (Long, Long) -> Unit)
    suspend fun deleteJobFolder(job: JobEntity)
}

data class PollResult(
    val cursor: String?,                       // plus grand modifiedTime vu
    val statuses: List<StatusJson>,
    val results: List<Pair<ResultJson, String?>>,   // résultat + id Drive de result.cbz s'il existe
    val heartbeat: Heartbeat?, val index: PcIndex?, val settings: PcSettings?,
    val protocolTooNew: Boolean
)
```

`DriveTransport` est la seule implémentation en V1. La classe `DirectTransport` de la V2 n'est **pas** créée.

### 5.10 `DriveApi` (REST Drive v3, OkHttp)

Constructeur : `DriveApi(client: OkHttpClient, token: suspend () -> String, baseUrl: HttpUrl)`. `baseUrl` vaut `https://www.googleapis.com/` en production et l'URL d'un `MockWebServer` en test.

| Méthode | Requête |
|---|---|
| `findChild(parentId, name, folder: Boolean)` | `GET drive/v3/files?q=<Q>&fields=files(id,name,modifiedTime,size)&spaces=drive` avec `Q` = `name = '<name>' and '<parentId>' in parents and trashed = false`, plus ` and mimeType = 'application/vnd.google-apps.folder'` si `folder` |
| `createFolder(parentId, name)` | `POST drive/v3/files?fields=id`, corps `{"name":…,"mimeType":"application/vnd.google-apps.folder","parents":[parentId]}` |
| `createJson(parentId, name, bytes)` | `POST upload/drive/v3/files?uploadType=multipart&fields=id,modifiedTime`, `multipart/related` : partie 1 métadonnées JSON `{"name","parents","mimeType":"application/json"}`, partie 2 contenu |
| `startResumable(parentId, name, size)` | `POST upload/drive/v3/files?uploadType=resumable`, en-têtes `X-Upload-Content-Type: application/zip`, `X-Upload-Content-Length`, corps métadonnées ; renvoie l'en-tête `Location` |
| `putChunk(session, bytes, offset, total)` | `PUT <session>`, `Content-Range: bytes offset-end/total` ; 308 : lit l'en-tête `Range` pour l'octet suivant ; 200/201 : terminé |
| `queryResumable(session, total)` | `PUT <session>` corps vide, `Content-Range: bytes */total` ; 308 + `Range` : reprise ; 404/410 : session expirée, recommencer à `startResumable` |
| `listChanged(sinceIso, pageToken)` | `GET drive/v3/files?q=modifiedTime > '<since>' and trashed = false&fields=nextPageToken,files(id,name,parents,modifiedTime,size)&pageSize=1000&spaces=drive` ; boucle sur `nextPageToken` |
| `download(fileId, sink, onProgress)` | `GET drive/v3/files/<id>?alt=media`, lecture en flux |
| `readText(fileId)` | idem, petit fichier en `String` |
| `delete(fileId)` | `DELETE drive/v3/files/<id>` |
| `about()` | `GET drive/v3/about?fields=user(emailAddress)` |

Morceaux de 8 Mio (multiple de 256 Kio). En-tête `Authorization: Bearer <jeton>` ajouté par un intercepteur qui appelle `token()`. Erreurs : 429 et 5xx -> nouvel essai après 1, 2, 4, 8, 16 s (ou `Retry-After` s'il est présent), 5 essais au plus ; 401 -> invalide le jeton (5.11), nouvel essai une fois, puis `AuthExpiredException`. Ces formes de requête sont **les seules** émises : le faux Drive des tests (E7) ne reconnaît qu'elles. Journal : méthode + chemin sans paramètres + code HTTP, jamais d'en-tête ni d'URL de session.

`DriveTransport.poll(since)` :
1. `listChanged(since ?: "1970-01-01T00:00:00Z")`.
2. Construit la table `id de dossier -> jobId` à partir des dossiers dont le parent est `drive_jobs_id` (nom = jobId) et de la colonne `driveFolderId` des jobs.
3. Pour chaque fichier modifié : parent `drive_pc_id` et nom `heartbeat.json` / `index.json` / `settings.json` -> lire et décoder ; parent `drive_root_id` et nom `protocol.json` -> lire, `protocolTooNew = protocol > 1` ; parent = dossier de job et nom `status.json` -> décoder ; `result.json` -> décoder et chercher l'id de `result.cbz` dans le même dossier (`findChild`).
4. `cursor` = plus grand `modifiedTime` vu, moins 120 s (chevauchement contre les écarts d'horloge). Les doublons sont sans effet (mise à jour idempotente).
5. Tout fichier dont `protocol` vaut plus de 1 : `protocolTooNew = true` et le fichier est ignoré.

`ensureLayout()` : cherche `TranslateMyManga` dans `root` (dossier), sinon le crée ; puis `jobs`, `pc`, `pc/commands` ; puis `protocol.json` (créé s'il manque). Ids mémorisés dans `AppPrefs`.

### 5.11 Autorisation Google (`GoogleAuth`)

- Lis d'abord la page officielle « Authorize access to Google user data » (developer.android.com, identité / autorisation) et note son URL. Utilise l'API d'autorisation de `play-services-auth` qu'elle décrit (`Identity.getAuthorizationClient(...)`, `AuthorizationRequest` avec le scope Drive, `authorize()`, `hasResolution()`, `getPendingIntent()`, `getAuthorizationResultFromIntent`).
- Scope : constante unique `DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"` dans `GoogleAuth.kt`. Repli prévu par le CdC 7.3.9 : changer seulement cette constante.
- Onboarding : `authorize()` ; si `hasResolution()`, lancer le `PendingIntent` par `rememberLauncherForActivityResult(StartIntentSenderForResult())`, puis lire le résultat.
- Hors écran (workers, rafraîchissement) : `authorize()` en silence ; si `hasResolution()`, poser `auth_expired = true` et échouer avec `AuthExpiredException` (bandeau « Accès à Google Drive expiré »). « Reconnecter » relance le flux interactif puis remet `auth_expired = false`.
- Invalidation d'un jeton refusé (401) : utilise la méthode d'effacement de jeton que la doc officielle indique pour cette API ; note-la dans le rapport. Si la doc n'en indique aucune, `BLOQUÉ` et nouvel appel `authorize()` sans effacement.
- E-mail : `DriveApi.about()`. S'il échoue, affiche la ressource `common_google_account` (« Compte Google ») et note-le.
- Aucun identifiant client dans le code : l'autorisation Android repose sur le nom de paquet et l'empreinte SHA-1 déclarés dans Google Cloud. Les étapes que l'utilisateur doit faire dans Google Cloud sont à écrire dans le rapport (section 9, « Actions pour l'utilisateur »).

### 5.12 Travaux en arrière-plan (WorkManager)

| Worker | Nom unique | Politique | Rôle |
|---|---|---|---|
| `SendJobWorker(jobId)` | `send-<jobId>` | `KEEP` | `translate` : PREPARING (CBZ dans `cacheDir/jobs/<jobId>/source.cbz`, SHA-256, taille) -> UPLOADING (envoi reprenable, session sauvée en Room à sa création, progression en Room au plus une fois par seconde) -> `request.json` -> SENT, suppression du fichier en cache. `verify` / `retranslate` : `request.json` (`file = null`) -> SENT. |
| `InstallJobWorker(jobId)` | `install-<jobId>` | `KEEP` | RESULT_READY -> DOWNLOADING (téléchargement en cache, contrôle SHA-256 ; écart : un nouveau téléchargement, puis FAILED étape `download`) -> installation (`LocalInstaller`) -> INSTALLED, ligne `local_fr_chapter`, remplacement de `chapter_quality`, puis mise en file de l'`OutboxWorker`. |
| `OutboxWorker` | `outbox` | `APPEND_OR_REPLACE` | Écrit tout ce qui attend : `cancel.json` des jobs CANCEL_REQUESTED sans `cancelWrittenAt`, `received.json` des jobs INSTALLED sans `receivedWrittenAt`, commandes de `pending_commands_json` pas encore écrites, suppressions de dossiers selon la conservation (CdC 7.3.10). |

- Contraintes : `NetworkType.CONNECTED`, ou `UNMETERED` si `wifi_only`. Changer l'option replanifie les travaux en attente avec la nouvelle contrainte.
- `SendJobWorker` et `InstallJobWorker` : `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)` et `getForegroundInfo()` avec une notification de progression (canal `transfers`, nom « Transferts »). Déclare le service de premier plan de WorkManager avec le type `dataSync` et la permission `FOREGROUND_SERVICE_DATA_SYNC` comme l'indique la doc WorkManager « long-running workers » (note l'URL). Pas de demande de permission de notification (aucune maquette) : note-le dans « Points ouverts ».
- Erreur réseau ou 5xx : `Result.retry()` avec `BackoffPolicy.EXPONENTIAL` 30 s. À partir du 6e essai (`runAttemptCount >= 5`) : FAILED avec l'étape en cause. `AuthExpiredException` : `Result.retry()` sans passer en FAILED.
- L'état est écrit en Room **avant** chaque appel réseau (CdC 11.2).

### 5.13 `LocalInstaller` (CdC 8.4)

1. Dossier `local/<localFolderName>` : trouvé ou créé (`DocumentsContract.createDocument` type `vnd.android.document/directory`).
2. Écrit le fichier sous le nom `.<chapterFileName>.tmm-<jobId>.tmp` (type MIME `application/octet-stream`).
3. Relit sa taille ; si différente de `result.file.size` : suppression du temporaire, échec.
4. Si `<chapterFileName>` existe déjà (vérification, retraduction) : suppression de l'ancien.
5. `DocumentsContract.renameDocument` vers `<chapterFileName>`. Si le fournisseur renvoie un nom différent (suffixe ajouté), échec explicite et suppression.
6. Ne laisse jamais un `.cbz` partiel visible.

### 5.14 `CbzPacker`

- Chapitre déjà en `.cbz` : copie telle quelle.
- Dossier d'images : ZIP en méthode `STORED` (sans recompression). Pour chaque fichier, CRC-32 et taille calculés par une première lecture, puis écriture. Ordre des entrées : tri par nom (ordre naturel des nombres, ex. `2.jpg` avant `10.jpg`). Fichiers retenus : tous sauf les fichiers cachés (commençant par `.`) et les temporaires de Mihon (règle de 5.6).
- `pageCount` = nombre d'entrées d'extension `jpg`, `jpeg`, `png`, `webp`, `gif`, `avif`, `jxl` (insensible à la casse).

### 5.15 `RefreshCoordinator` (CdC 11.3)

```kotlin
data class RefreshState(val running: Boolean, val cooldownSeconds: Int, val lastCheckedAt: Long?, val error: RefreshError?)
enum class RefreshError { NETWORK, DRIVE_QUOTA, AUTH }
```

`refresh()` : `Mutex.tryLock()`, sinon retour immédiat ; aussi retour immédiat si `cooldownSeconds > 0`. Étapes : `ensureLayout` si les ids manquent ; `poll(last_changes_cursor)` ; application en une transaction Room (statuts -> colonnes `pc*` ; résultats -> `resultJson`, `resultFileId`, `finishedAt`, `chapter_quality`, transition SENT -> RESULT_READY ou INSTALLED ; index -> `pc_index_chapter` ; heartbeat, settings -> `AppPrefs`) ; confirmation des commandes (`SettingsSync`) ; traduction automatique (5.16) ; conservation ; `last_refresh_at`. Puis décompte de 30 s, une seconde à la fois. Tirer vers le bas et le bouton appellent cette même fonction.

Un `status.json` ou `result.json` d'un `jobId` inconnu (autre téléphone) est ignoré.

PC en ligne : `heartbeat.updatedAt` il y a moins de 5 min. « Vu il y a … » : `relativeFr(heartbeat.updatedAt)`. Hors ligne : pas de heartbeat ou plus de 5 min.

### 5.16 Séquence de lancement (`Startup`) et traduction automatique

À chaque `onStart` de `MainActivity` (une seule exécution à la fois) :
1. Onboarding non terminé : aller à l'onboarding, fin.
2. Permission SAF : l'URI `mihon_tree_uri` est dans `contentResolver.persistedUriPermissions` avec lecture et écriture, et une requête sur sa racine réussit. Sinon : écran Permission, fin.
3. Sauvegarde : fichier `.tachibk` le plus récent de `autobackup/` ; si nom, taille ou date diffèrent de `backup_import` : import.
4. Scan des téléchargements (cache par date de modification des dossiers séries, CdC 8.2).
5. Traduction automatique : pour chaque série avec `autoTranslate = true`, crée un job `translate` pour chaque chapitre `TO_SEND` (dédoublonnage 5.17).
6. `refresh()`.

### 5.17 `JobRepository` (création et actions)

- `createJobs(mode, targets: List<Target>, pages: List<Int>? = null, translate: Boolean = true)` : un job par chapitre (CdC 7.3.12). Avant chaque création :
  - `translate` : refusé si un job actif existe pour ce `mihonPath` ou si l'index contient ce `mihonPath` avec `original == true` ;
  - `verify` / `retranslate` : refusé si un job actif existe pour ce `mihonPath` ou si `index.original != true`.
  Les refus sont comptés et silencieux. Écriture Room (PREPARING) puis mise en file de `SendJobWorker`. Protection contre les appuis multiples : un `Mutex` autour de la vérification et de l'insertion.
- `fetch(jobIds)` : met en file `InstallJobWorker` pour les jobs RESULT_READY.
- `cancel(jobId)` : PREPARING ou UPLOADING : annule le travail WorkManager, supprime le dossier Drive s'il existe, supprime le job ; SENT : CANCEL_REQUESTED puis `OutboxWorker`.
- `retry(jobId)` (« Réessayer à partir de la page N ») : nouveau job, même `mihonPath`, même mode ; si le mode était `translate` et que `index.original == true`, le nouveau job est `retranslate` (le PC a déjà l'original). Le texte « à partir de la page N » vient de `pcErrorPage` ; le PC décide où il reprend.
- `verifyPage(mihonPath, page0)` : `createJobs(verify, [target], pages = listOf(page0))`.

### 5.18 `SettingsSync` (CdC 7.5)

- `values` affichées = `settings_json.values` avec les `changes` des commandes en attente appliquées par-dessus.
- `change(key, value)` : valide contre `SettingsSchema`, crée `SettingsCommand(cmdId = UUID, baseVersion = settings.version, changes = {key: value})`, l'ajoute à `pending_commands_json`, met en file `OutboxWorker`, état `SENDING`.
- Tant que l'écran 9 est ouvert et qu'une commande attend : relit `pc/settings.json` (`readSettings()`) toutes les 5 s, 60 s au plus. `lastCommandId == cmdId` -> commande retirée, état `APPLIED`. Après 60 s sans confirmation : état `WAITING_PC` (textes « En attente du PC » / « Sera appliqué au prochain démarrage du PC »). Une commande dont le `lastCommandId` confirmé est postérieur (version plus haute) est aussi retirée.
- Aucun `settings_json` reçu : écran en lecture seule, contrôles désactivés.

```kotlin
enum class SyncBanner { SYNCED, SENDING, APPLIED, WAITING_PC }
```

### 5.19 Navigation (`ui/nav/Routes.kt`, routes typées Navigation Compose)

```kotlin
@Serializable object OnboardingRoute
@Serializable object HomeRoute
@Serializable object LibraryRoute
@Serializable data class LibraryFilteredRoute(val categoryOrder: Long)
@Serializable object TransfersRoute
@Serializable object QueueRoute
@Serializable object CatalogRoute
@Serializable data class SeriesRoute(val seriesKey: String)
@Serializable object SettingsRoute
@Serializable object TranslationSettingsRoute
@Serializable object PermissionRoute
```

- Onglets : Accueil, Bibliothèque, Transferts, File, Catalogue (ordre et libellés de la barre de la maquette). Changement d'onglet : `popUpTo(HomeRoute) { saveState = true }`, `launchSingleTop = true`, `restoreState = true`.
- `TmmBottomBar` visible seulement sur les 5 onglets. Pastille de Transferts = `toSend + toFetch` (toutes séries), masquée à 0.
- Feuille F (`NewTranslation`) : ce n'est pas une route, c'est un `TmmSheet` affiché par-dessus l'Accueil ou la File (état `showNewTranslation` du ViewModel de l'écran hôte).
- Bandeaux `OfflineBanner` : en tête du contenu des 5 onglets, au-dessus de l'en-tête, pilotés par un `StateFlow` global (`ConnectivityManager.NetworkCallback` pour le réseau, `auth_expired`, `protocol_too_new`).
- Écran vide E1 : l'Accueil affiche l'état `Empty` quand il n'y a ni chapitre téléchargé ni sauvegarde importée.
- Un lien `href` de la maquette vers un autre écran = navigation vers la route correspondante. `href="Main.dc.html"` depuis Réglages = retour.

### 5.20 Patron des écrans

```kotlin
// XxxState.kt
data class XxxState(...)                // tout ce que l'écran affiche, déjà formaté (chaînes, fractions)
sealed interface XxxEvent { ... }       // un cas par action de la maquette (onClick, onChange...)

// XxxScreen.kt
@Composable fun XxxScreen(state: XxxState, onEvent: (XxxEvent) -> Unit)   // aucun ViewModel, aucune I/O

// XxxViewModel.kt
class XxxViewModel(...) : ViewModel() { val state: StateFlow<XxxState>; fun onEvent(e: XxxEvent) }

// XxxRoute.kt
@Composable fun XxxRoute(nav: NavController) { val vm = viewModel<XxxViewModel>(factory = ...); XxxScreen(vm.state.collectAsStateWithLifecycle().value, vm::onEvent) }
```

Les états éphémères de la maquette (toast, feuille ouverte, section dépliée) sont dans `XxxState`. La sélection de la Bibliothèque et de la Fiche série est dans `SavedStateHandle` (survit à la rotation). `MockupData.xxx` construit un `XxxState` identique à l'état initial de la maquette.

### 5.21 Éléments du CdC absents des maquettes (décision fixe)

La maquette gagne (R16). Ne crée **aucune** interface pour :
- l'interrupteur « Traduction automatique » de la Fiche série (CdC 9.5) : la table `series_settings` et la logique de 5.16 existent, mais aucune ligne n'est créée par l'interface en V1 ;
- le bouton « Importer » manuel du Catalogue (CdC 8.3, 9.8) : seule « Relire la dernière sauvegarde » existe. Le lien « J'ai une sauvegarde, la lire » de l'état vide ouvre le Catalogue.

Liste les deux dans « Points ouverts » du rapport.

---

## 6. Étapes

Chaque étape : tâches, tests, condition « fini quand », message de commit. Ne commence pas une étape tant que la précédente n'est pas commitée.

### E0. Préparation (`E0: preparation du depot`)

1. Vérifie les outils de 2.1. Note les versions trouvées dans le rapport.
2. `git checkout -b feat/v1-android`.
3. Crée `.gitignore` à la racine, contenu exact :
   ```
   .gradle/
   /build/
   /app/build/
   local.properties
   .idea/
   *.iml
   .DS_Store
   *.jks
   *.keystore
   keystore.properties
   .serena/
   .kotlin/
   ```
   Ne mets **pas** `captures/` ni `*.png` : les captures de référence et de test sont versionnées.
4. Relève les versions (dernière stable, pas d'alpha, bêta ou RC) et leur URL de référence :

   | Artefact | Source officielle |
   |---|---|
   | Android Gradle Plugin | developer.android.com, notes de version d'AGP |
   | Gradle | gradle.org/releases (compatible avec l'AGP retenu, tableau de compatibilité d'AGP) |
   | Kotlin (plugins `android`, `plugin.compose`, `plugin.serialization`) | kotlinlang.org, page des versions |
   | KSP | github.com/google/ksp/releases (version correspondant à Kotlin) |
   | Compose BOM | developer.android.com, page « BOM to library version mapping » |
   | `activity-compose`, `lifecycle-*`, `navigation-compose`, `room-*`, `work-runtime-ktx`, `datastore-preferences`, `core-ktx` | developer.android.com, page de version de chaque bibliothèque Jetpack |
   | `kotlinx-serialization-json`, `kotlinx-serialization-protobuf` | github.com/Kotlin/kotlinx.serialization/releases |
   | `kotlinx-coroutines-android`, `kotlinx-coroutines-test` | github.com/Kotlin/kotlinx.coroutines/releases |
   | `okhttp`, `mockwebserver` | square.github.io/okhttp (changelog) |
   | `coil-compose` (Coil 3) et `coil-network-okhttp` | coil-kt.github.io/coil (changelog) |
   | `play-services-auth` | developers.google.com/android/guides/releases |
   | `junit:junit` 4 | junit.org/junit4 |
   | `robolectric` | github.com/robolectric/robolectric/releases |
   | `roborazzi` (plugin + `roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule`) | github.com/takahirom/roborazzi/releases |
   | `ui-test-junit4`, `ui-test-manifest` | via la BOM |
   | `androidx.test:core`, `androidx.test.ext:junit` | developer.android.com, page AndroidX Test |
   | `androidx.work:work-testing` | même version que `work-runtime-ktx` |
   | `androidx.room:room-testing` | même version que Room |

   Aucune autre dépendance n'est autorisée (R10). En particulier : pas de Hilt, pas de Retrofit, pas de bibliothèque d'icônes, pas d'Accompanist, pas de Google API Client Java.
5. Commit : `docs/` et `maquettes/` (s'ils ne sont pas encore suivis), `.gitignore`, `docs/RAPPORT_GEMINI.md` (créé avec le modèle de la section 9, étape E0 remplie).

Fini quand : outils présents (ou `BLOQUÉ` bloquant), branche créée, versions relevées.

### E1. Squelette Gradle (`E1: squelette gradle et application vide`)

1. Wrapper Gradle, `settings.gradle.kts` (`rootProject.name = "TMM-Mobile"`, `include(":app")`, dépôts `google()`, `mavenCentral()`, `gradlePluginPortal()`).
2. `gradle/libs.versions.toml` avec toutes les versions de E0.
3. `app/build.gradle.kts` :
   - `namespace = "com.tmm.mobile"`, `applicationId = "com.tmm.mobile"`, `minSdk = 26`, `targetSdk` et `compileSdk` = plus haute API stable prise en charge par l'AGP retenu, `versionCode = 1`, `versionName = "1.0.0"`.
   - Java et Kotlin JVM target 17. `buildFeatures { compose = true; buildConfig = true }`.
   - Plugins : Android application, Kotlin Android, Kotlin Compose, Kotlin Serialization, KSP, Roborazzi.
   - `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.
   - `testOptions { unitTests { isIncludeAndroidResources = true; all { it.systemProperty("robolectric.graphicsMode", "NATIVE") } } }`.
   - `release` : `isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt` + `proguard-rules.pro`. Signature `release` lue depuis `keystore.properties` **si le fichier existe** (clés `storeFile`, `storePassword`, `keyAlias`, `keyPassword`), sinon pas de signature release.
   - `keystore.properties.example` à la racine avec ces 4 clés et des valeurs vides.
4. `AndroidManifest.xml` : `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` ; `application` avec `android:name=".TmmApp"`, `android:label="@string/app_name"`, `android:icon`, `android:roundIcon`, `android:theme="@style/Theme.Tmm"`, `android:allowBackup="false"`, `android:supportsRtl="true"` ; `MainActivity` exportée, `windowSoftInputMode="adjustResize"`, filtre `MAIN`/`LAUNCHER`. Déclare le service de premier plan de WorkManager (`androidx.work.impl.foreground.SystemForegroundService`) avec `android:foregroundServiceType="dataSync"` et `tools:node="merge"`.
5. `themes.xml` : `Theme.Tmm` parent `android:Theme.Material.Light.NoActionBar`, `android:windowBackground` = `#F4F1EA`.
6. `strings.xml` : `app_name` = `TMM Mobile`.
7. `TmmApp`, `MainActivity` (écran vide couleur `paper`), `AppContainer` vide.
8. Test : `SmokeTest` Robolectric qui lance `MainActivity` sans exception.

Fini quand : `./gradlew testDebugUnitTest` et `./gradlew assembleDebug` passent.

### E2. Thème, polices, couleurs, icônes, animations (`E2: theme polices icones animations`)

1. **Polices** : télécharge depuis `https://github.com/google/fonts` (dossiers `ofl/bricolagegrotesque`, `ofl/ibmplexsans`, `ofl/ibmplexmono`) à un commit fixe, note le SHA et les noms de fichiers. Copie dans `res/font/` en noms `snake_case` : `bricolage_grotesque.ttf` (variable si c'est ce qui existe), `ibm_plex_sans_regular.ttf`, `ibm_plex_sans_medium.ttf`, `ibm_plex_sans_semibold.ttf`, `ibm_plex_mono_medium.ttf`. Si IBM Plex Sans n'existe qu'en police variable, copie-la sous `ibm_plex_sans.ttf` et utilise `FontVariation.weight`. Copie aussi `OFL.txt` de chaque famille dans `app/src/main/assets/licenses/<famille>_OFL.txt`.
2. `TmmType` (4.4), `TmmColors` (4.5), `TmmTheme` (fournit `LocalIndication provides NoIndication`, `LocalReducedMotion`, fond `paper`, `LocalTextStyle` = `TmmType.sans(15)`).
3. `TmmIcons` (4.6) : parcours les 13 maquettes, extrais chaque `<svg>` distinct (même contenu = même icône), crée une entrée par icône. Le logo « 訳 » est un texte, pas une icône.
4. `Motion.kt` (4.7), `ReducedMotion.kt`.
5. **Icône de lanceur** : icône adaptative, fond `#C8391F`, premier plan = texte « 訳 » en Bricolage Grotesque 800 couleur `#F4F1EA`, converti en tracé vectoriel. Si tu ne peux pas convertir le glyphe en tracé : `BLOQUÉ` et utilise un premier plan vide (fond seul).
6. Tests :
   - `NoRawColorTest` : parcourt `app/src/main/java` ; aucun fichier hors `ui/theme/` ne contient `Color(0x`, `Color(red` ni `Color(` suivi d'un chiffre.
   - `NoMaterialIconsTest` : aucune occurrence de `androidx.compose.material.icons` ni `Icons.` dans `app/src/main/java`.
   - `PaletteCoverageTest` : extrait par regex toutes les couleurs `#RRGGBB` et `rgba(...)` des 13 fichiers `maquettes/*.dc.html` (chemin relatif `../maquettes` depuis le module `app`) et vérifie qu'une constante de `TmmColors` a la même valeur (réflexion sur l'objet).
   - `IconCoverageTest` : chaque `d="..."` de `<path>` des maquettes apparaît dans une entrée de `TmmIconId`.

Fini quand : les 4 tests passent.

### E3. Composants (`E3: composants maison`)

1. Écris les composants de 4.9, chacun avec un `@Preview` qui utilise des valeurs de `MockupData`.
2. `MockupData.kt` : commence par les données communes (séries et couleurs des scripts).
3. Tests Roborazzi d'un composant à la fois dans `screenshots/ComponentsScreenshotTest.kt` (une capture par composant, sans comparaison).
4. Tests de comportement (`ComponentsBehaviorTest`, `createComposeRule`) :
   - `TmmSwitch` : un clic appelle `onCheckedChange(!checked)` ; sémantique `Role.Switch` et état coché.
   - `TmmSheet` : clic sur le voile et retour système appellent `onDismiss`.
   - `TmmToast` : `liveRegion` Polite présent ; disparaît après sa durée (horloge `mainClock`).
   - `RefreshButton` : désactivé pendant le décompte, `contentDescription` = « Rafraîchir, disponible dans 30 secondes » juste après.
   - `pressScale` : ne teste que le passage du clic (pas de code de production ajouté pour le test).

Fini quand : tests verts, captures des composants générées.

### E4. Les 13 écrans en données factices (sous-étapes E4.1 à E4.13)

Un commit par écran : `E4.<n>: ecran <Nom>`. Ordre : 1 Main, 2 Library, 3 Series, 4 Sync, 5 Queue, 6 Catalog, 7 NewTranslation, 8 Settings, 9 TranslationSettings, 10 Onboarding, 11 Empty, 12 Offline, 13 Permission.

Pour chaque écran :
1. Écris `XxxState`, `XxxEvent`, `XxxScreen` (5.20) en appliquant la section 4 élément par élément.
2. `MockupData.xxx` = état initial de la maquette (mêmes valeurs que le script au montage, animations terminées : compteurs à leur valeur finale).
3. Toutes les chaînes dans les ressources ; lignes correspondantes dans `mockup_texts.tsv`.
4. Les interactions **internes à l'écran** (onglets, dépliage, sélection, feuille ouverte, recherche) sont gérées dans un `XxxViewModel` provisoire qui part de `MockupData` ; elles seront reliées aux vraies données en E10.
5. Capture Roborazzi + image de comparaison (4.13) + états supplémentaires listés en 4.13.
6. Relis l'image de comparaison ; corrige ; note les écarts restants dans le rapport.

Tests ajoutés en E4 :
- `MockupTextCoverageTest` (4.10), complété à chaque écran (les écrans non encore faits sont ignorés par une liste d'exclusion qui se vide au fil des sous-étapes et doit être vide à E4.13).
- Par écran, un test de comportement couvrant chaque transition du script de la maquette. Cas obligatoires :
  - Library : appui long sur une tuile hors sélection -> mode sélection, 1 cochée, sous-titre « 1 série sélectionnée » ; appui simple sur une autre -> 2 cochées ; décocher les 2 -> sortie du mode ; bouton « Sélectionner » puis cocher et décocher la seule -> reste en mode ; recherche qui masque une série cochée -> toujours comptée dans la barre d'actions ; « Tout cocher (N) » -> N = nombre de tuiles visibles ; action à 0 -> désactivée et « Non applicable » ; « Retraduire » -> dialogue avec titre « Retraduire N chapitres ? ».
  - Series : ligne verrouillée non cochable ; « Tout ce qui reste à faire » coche `send` et `get` ; retour système avec sélection -> vide la sélection, deuxième retour -> quitte.
  - Queue : onglets et compteurs ; « Vérifier la page » -> « Dans la file » ; ✕ -> ligne retirée ; « Réessayer… » -> « Remis dans la file ».
  - NewTranslation : étapes, « Tout cocher / Tout décocher », libellés du bouton d'envoi pour 0, 1, N chapitres.
  - TranslationSettings : ouverture de feuille, choix -> bandeau « Envoi au PC... » ; « Vision avancée (5 réglages) » dépliable.
  - Onboarding : libellés du bouton primaire à chaque état, « Étape précédente » absent à l'étape 1.

Fini quand (pour E4.13) : 13 captures + comparaisons + états, `MockupTextCoverageTest` sans exclusion, tous les tests verts.

### E5. Données locales (`E5: room datastore derivations`)

1. `Entities.kt`, `Daos.kt`, `Converters.kt`, `TmmDatabase.kt` (5.3), `AppPrefs.kt` (5.2).
2. `ChapterStatus.kt`, `SeriesSummary.kt` (5.7), `Formatters.kt` (5.8), `Clock`.
3. Tests :
   - `DaoTest` (Robolectric, base en mémoire) : insertion et lecture de chaque table ; remplacement de l'index en transaction.
   - `ChapterStatusTest` : un cas par règle 1 à 8, plus : job annulé ignoré ; job `failed` côté PC -> `FAILED` même si `phoneState == SENT` ; `index.fr` + fichier local -> `UP_TO_DATE`.
   - `MissingChaptersTest` : présents {1,2,4,7.5,8} -> manquants [3,5,6,7] ; avec `ignored_gap` 5 -> [3,6,7] ; un seul chapitre -> [].
   - `SeriesSummaryTest` : reproduit les 9 séries de la maquette Bibliothèque (badge et « FR x/y » identiques).
   - `FormattersTest` : 59 s, 60 s, 59 min, 60 min, 23 h, 24 h, 48 h ; 999 999 999 o, 10^9 o, 1,25 Go ; plages de chapitres.

### E6. Mihon (`E6: lecture mihon sauvegarde scan installation`)

1. Lis le code Mihon (5.6), note SHA et chemins. Écris `BackupModels`, `MihonNaming`.
2. `BackupReader` : `GZIPInputStream` puis `ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)`.
3. `BackupImporter` : transaction unique qui vide et remplit `series`, `category`, `series_category`, `chapter`, puis `backup_import`.
4. `MihonTree` : `DocumentsContract.buildChildDocumentsUriUsingTree` + `contentResolver.query` avec les colonnes `COLUMN_DOCUMENT_ID`, `COLUMN_DISPLAY_NAME`, `COLUMN_MIME_TYPE`, `COLUMN_LAST_MODIFIED`, `COLUMN_SIZE`. Fonctions : `children(docId)`, `findChild(parentDocId, name)`, `createDir`, `createFile`, `rename`, `delete`, `openInput`, `openOutput`. Interface `MihonFs` implémentée par `MihonTree` (production) et `FakeMihonFs` (tests, sur un dossier temporaire du disque). C'est la seule interface à deux implémentations autorisée en dehors de `JobTransport`.
5. `DownloadScanner` : parcours `downloads/<source>/<série>/<chapitre>`, cache par `scanned_series_dir`, exclusion des dossiers incomplets, `pageCount`, puis `Matcher` (CdC 8.5) qui remplit `seriesKey`, `chapterKey`, `number`. Parcours aussi `local/<dossier>/<fichier>.cbz` pour `local_fr_chapter` (en gardant `jobId` et `mihonPath` des lignes existantes).
6. `CbzPacker` (5.14), `LocalInstaller` (5.13).
7. Onboarding étape 2 réelle : vérification de `downloads/`, `local/`, `autobackup/` (coche par dossier trouvé, délai d'échelonnement 120 ms comme `delay: i * 0.12s` de la maquette). Dossier absent : la ligne n'a pas de coche et le rôle affiche `Dossier introuvable` (ressource `onboarding_folder_missing`, à signaler dans le rapport) ; le bouton « Continuer » est actif seulement si `downloads/` existe.
8. Tests :
   - `BackupReaderTest` : le test encode un `Backup` de 3 séries et 2 catégories, le compresse en gzip, puis le relit ; un second test ajoute un champ inconnu (numéro 999) et vérifie la lecture.
   - `MihonNamingTest` : exemples tirés du code Mihon lu.
   - `DownloadScannerTest` sur `FakeMihonFs` : dossier d'images, `.cbz`, dossier incomplet ignoré, cache (le second scan ne relit pas une série non modifiée : compteur d'appels de `children`).
   - `MatcherTest` : source, série, chapitre avec et sans scanlator, échec -> hors catalogue.
   - `CbzPackerTest` : ordre naturel, entrées `STORED`, CRC valides (relecture par `ZipInputStream`).
   - `LocalInstallerTest` : succès, taille différente -> aucun `.cbz` visible, fichier existant remplacé.

### E7. Protocole et transport (`E7: protocole drive faux drive faux pc`)

1. `ProtocolModels.kt`, `ProtocolJson` (5.5), `SettingsSchema`.
2. `DriveApi` (5.10), `DriveTransport` (5.9, 5.10), `GoogleAuth` (5.11).
3. **Faux Drive** (`app/src/test/java/com/tmm/mobile/fake/FakeDrive.kt`) : `Dispatcher` de `MockWebServer` qui garde en mémoire des fichiers `{id, name, parents, mimeType, modifiedTime, bytes}` et répond **uniquement** aux formes de requêtes de 5.10 (analyse de `q` par expressions régulières ; toute autre forme -> 400 et échec du test). Il gère les sessions reprenables (308 + `Range`), la coupure simulée (couper après K octets), les 429 avec `Retry-After`, et un 401 unique.
4. **Faux PC** (`fake/FakePc.kt`) : agit sur le `FakeDrive` comme TMM-Win : `writeHeartbeat(slots, active, queue)`, `acceptRequest(jobId)` (lit `request.json` et `source.cbz`, vérifie le SHA), `writeStatus(jobId, state, stage, page, pages)`, `finish(jobId, resultCbz, quality)` (écrit `result.cbz` puis `result.json`), `fail(jobId, code, message, page)`, `applyCommands()` (applique les `pc/commands/*.json` valides, incrémente `version`, `lastCommandId`, supprime la commande), `writeIndex(...)`.
5. Tests (`DriveTransportTest`, `ProtocolModelsTest`) :
   - `ensureLayout` crée `TranslateMyManga/`, `jobs/`, `pc/`, `pc/commands/`, `protocol.json` au premier appel, n'en crée aucun au second.
   - Envoi reprenable : coupure à 40 % puis reprise par `queryResumable` ; le fichier final a le bon SHA.
   - 429 puis succès ; 5 échecs -> exception ; 401 puis succès ; deux 401 -> `AuthExpiredException`.
   - `poll` : statut, résultat avec et sans `result.cbz`, heartbeat, index, settings, `protocol: 2` -> `protocolTooNew`.
   - `request.json` écrit après `source.cbz` (ordre des créations dans le faux Drive).
   - Sérialisation : chaque modèle reproduit à l'identique les exemples JSON du CdC 7.2 et 7.5 (fixtures copiées dans `app/src/test/resources/protocol/`).

### E8. Jobs et travaux en arrière-plan (`E8: jobs workers rafraichissement`)

1. `JobRepository` (5.17), `SendJobWorker`, `InstallJobWorker`, `OutboxWorker`, `WorkerSupport` (5.12), `RefreshCoordinator` (5.15), `Startup` (5.16).
2. `AppContainer` complet ; `TmmApp` fournit une `Configuration` WorkManager seulement si la doc de `work-testing` l'exige pour les tests ; note le choix fait.
3. Tests (Robolectric, `WorkManagerTestInitHelper`, `TestListenableWorkerBuilder`, faux Drive, faux PC, `FakeMihonFs`) :
   - **Scénario complet** (`JobFlowTest`) : chapitre téléchargé -> `createJobs(translate)` -> worker d'envoi -> SENT ; faux PC accepte, statut `translating` page 5/20 -> `refresh()` -> statut `TRANSLATING` et 25 % ; faux PC termine -> `refresh()` -> RESULT_READY -> worker d'installation -> fichier dans `local/<dossier>/<fichier>`, INSTALLED, `received.json` écrit par l'outbox, statut `UP_TO_DATE`, qualité enregistrée.
   - Dédoublonnage : deux `createJobs` sur le même chapitre -> un seul job.
   - `verify` et `retranslate` : pas de `source.cbz` ; refus si `index.original != true`.
   - `result.json` avec `file: null` -> INSTALLED sans téléchargement, qualité mise à jour.
   - SHA faux -> un nouveau téléchargement puis FAILED `download`.
   - Annulation en UPLOADING (dossier supprimé, job supprimé) et en SENT (`cancel.json` écrit).
   - Conservation « Après récupération » -> dossier supprimé après `received.json`.
   - Anti-spam (`RefreshCoordinatorTest`) : deux `refresh()` simultanés -> un seul `listChanged` ; un troisième pendant le décompte -> aucun appel.
   - Transitions interdites de 5.4 rejetées (`PhoneStateTransitionTest`).
   - Job d'un autre téléphone ignoré.

### E9. Réglages du PC (`E9: reglages de traduction a distance`)

1. `SettingsSync` (5.18).
2. Tests (`SettingsSyncTest`) : changement -> commande dans `pc/commands/` avec `baseVersion` et une seule clé ; faux PC applique -> bandeau `APPLIED` ; pas d'application en 60 s (horloge virtuelle) -> `WAITING_PC` ; valeur hors liste refusée avant envoi ; lecture seule sans `settings.json`.

### E10. Branchement des écrans (`E10: donnees reelles dans les ecrans`)

1. Remplace les ViewModel provisoires de E4 par les vrais (section 7). Les `XxxScreen` ne changent pas, sauf si un champ d'état manquait : ajoute-le à `XxxState`, garde `MockupData` à jour, relance les captures (elles doivent rester identiques).
2. `TmmRoot` : `NavHost` (5.19), barre, bandeaux, `Startup` lancé à chaque `ON_START` du cycle de vie.
3. Onboarding réel (Google, dossier via `ActivityResultContracts.OpenDocumentTree` + `takePersistableUriPermission` lecture et écriture, import), Permission réelle, bandeaux Hors ligne réels.
4. Export des journaux : `ActivityResultContracts.CreateDocument("text/plain")`, nom `tmm-journaux-<aaaa-mm-jj>.txt`, contenu des 7 derniers jours de `AppLog`. Masque dans le texte exporté l'adresse e-mail (remplacée par `***@***`) et toute valeur d'en-tête `Authorization`.
5. Tests : un `<Ecran>ViewModelTest` par écran avec dépôts sur base en mémoire et faux Drive ; vérifie chaque liaison de la section 7 sur un jeu de données qui reproduit la maquette (les textes produits doivent être ceux de la maquette).

### E11. Finitions et livraison (`E11: verification finale et apk`)

1. `./gradlew lintDebug` : corrige toutes les erreurs ; liste les avertissements restants dans le rapport.
2. `proguard-rules.pro` : règles pour kotlinx.serialization (celles de la doc officielle de kotlinx.serialization, note l'URL) et pour les modèles protobuf.
3. `./gradlew assembleRelease` sans `keystore.properties` doit réussir (APK non signé) ; `./gradlew assembleDebug` produit `app/build/outputs/apk/debug/app-debug.apk`.
4. `./gradlew signingReport` : note l'empreinte SHA-1 de la clé **debug** dans le rapport (l'utilisateur en a besoin pour Google Cloud).
5. Relance toutes les captures et comparaisons ; complète le rapport ; vérifie la liste de la section 8.

---

## 7. Liaisons écran par écran

Pour chaque texte ou valeur de la maquette : d'où vient la vraie valeur. Ce qui n'est pas cité ici reste un texte fixe. Quand la valeur réelle est absente, applique le comportement indiqué ; à défaut, masque l'élément et signale-le dans le rapport. Tout texte marqué « ajouté » va dans la section 6.3 du rapport.

### 7.1 Accueil (`Main.dc.html`, dossier `home`)

| Élément | Source |
|---|---|
| `pcLabel` + point | heartbeat de moins de 5 min -> « PC en ligne » (point `success` + `pulse`) ; de 5 min à 24 h -> ressource `home_pc_seen` = `PC vu %1$s` avec `relativeFr` (point `#9C9486`) ; plus de 24 h ou absent -> « PC hors ligne » (point `#7A2210`) |
| Bandeau « PC hors ligne depuis 2 jours… » | affiché si « PC hors ligne » avec un heartbeat connu ; la durée vient de `updatedAt` (`sinceFr`). Sans heartbeat : masqué |
| « Vérifié {{ lastCheck }} » | `relativeFr(last_refresh_at)` ; jamais rafraîchi : masqué |
| Bouton Rafraîchir | `RefreshCoordinator.state` ; tourne (`spin`) pendant `running` ; anneau et secondes pendant `cooldownSeconds` |
| `n1` | nombre de chapitres `TO_SEND` (toutes séries), compteur animé 900 ms à l'arrivée sur l'écran |
| « 5 séries · environ 180 Mo » | séries distinctes de ces chapitres ; somme de `sizeBytes` (`sizeFr`) |
| « Envoyer » | `createJobs(translate, tous les TO_SEND, translate = translate_on_arrival)` ; `s1busy` pendant la création, `s1done` après ; toast de la maquette (pluriels) |
| `n2` | nombre de jobs RESULT_READY |
| « 4 séries · vers Mihon/local » | séries distinctes de ces jobs |
| « Récupérer » | `fetch(tous)` ; barre = jobs INSTALLED / jobs lancés ; toast de la maquette à la fin |
| « File (7) » | `queue.active + queue.waiting` du heartbeat ; sans heartbeat : nombre de jobs actifs locaux |
| « 4 chapitres en parallèle sur PC-BUREAU · +3 ensuite » | `activeJobIds.size`, `pcName`, `queue.waiting` ; même découpage en styles que la maquette |
| Lignes de jobs | jobs de `activeJobIds` connus en Room (4 au plus) : titre = `<titre série> · <numéro>`, badge = mode (`translate` Traduction, `verify` Vérification, `retranslate` Retraduction, couleurs `T` du script), pourcentage `translatingPercent`. Aucun : lignes masquées, en-tête gardé |
| « Dernier scan il y a 4 min » | `relativeFr(last_scan_at)` ; la tuile « Scanner Mihon » lance le scan puis `refresh()` |
| Rail « Derniers chapitres traduits » | 10 derniers jobs RESULT_READY ou INSTALLED (tri `finishedAt` décroissant), regroupés par série, 4 groupes au plus : `chapterRangeFr`, `Dans Mihon` si tous INSTALLED sinon `Sur le PC` ; appui -> Fiche série. Aucun : section masquée |
| « Nouvelle traduction » | ouvre la feuille F |
| Toast « Voir » | onglet File |
| État vide E1 | ni `downloaded_chapter` ni `backup_import` : affiche l'écran Vide (7.11) |

### 7.2 Bibliothèque (`Library.dc.html`, dossier `library`)

| Élément | Source |
|---|---|
| sous-titre | hors sélection : `%1$d séries · %2$d catégories Mihon` (pluriels) depuis `series` et `category` ; en sélection : formule du script |
| puces | « Tous » + nombre total de séries, puis chaque catégorie par `order` avec son nombre de séries. `LibraryFilteredRoute(order)` présélectionne la puce |
| filtres | 5.7 |
| tuiles | `SeriesSummary` + couverture (`thumbnailUrl`) + titre ; tri par titre (`java.text.Collator` français) |
| sélection | CdC 9.12, gardée dans `SavedStateHandle` (liste de `seriesKey`, booléen `enteredByButton`) |
| actions | sommes sur les séries cochées : Envoyer = `toSend`, Récupérer = `toFetch`, Vérifier et Retraduire = `verifiable` ; exécution : `createJobs` ou `fetch` sur les chapitres concernés ; toasts du script ; sortie du mode après l'action |
| « Voir la file » | onglet File |
| appui simple hors sélection | `SeriesRoute(seriesKey)` |

### 7.3 Fiche série (`Series.dc.html`, dossier `series`)

| Élément | Source |
|---|---|
| « En cours » | nom de la première catégorie de la série (par `order`) ; aucune : masqué |
| titre | `series.title` |
| « Manga · anglais vers français » | `Manga` + ` · ` + `langLabelFr(sourceLanguage)` + ` vers ` + `langLabelFr(targetLanguage)` depuis `settings.json` ; sans `settings.json` : `Manga` seul |
| compteurs Téléphone / PC original / Traduits | `onPhone`, `onPc`, `frCount` |
| alerte de trou | `missing` non vide : texte avec la liste (`ch. 23` ou `ch. 23, 57`) ; « Ignorer » -> `ignored_gap` pour chaque numéro |
| lignes | chapitres de la série (téléchargés, FR, catalogue, index), tri par numéro décroissant, plus une ligne par manquant ; statut et couleur selon le tableau ci-dessous ; indicateurs téléphone = téléchargé, PC = `index.original`, FR = FR local |
| statistiques | `chapter_quality` du chapitre : pages traduites = `totalPages` moins le nombre de pages distinctes des alertes `source_copy` et `source_text` ; bulles, non traduites, SFX ; `Vérifié le jj/mm` depuis `verifiedAt` |
| « Tout ce qui reste à faire » | coche `TO_SEND` et `TO_FETCH` (sauf `TO_FETCH` connu seulement par l'index) |
| actions et feuille « Toute la série » | mêmes règles que 7.2 sur les chapitres cochés, ou sur toute la série ; `Blue Lock · 214 chapitres` = titre + nombre de lignes non manquantes |

Statuts affichés :

| Statut | Texte | Couleur |
|---|---|---|
| `TO_SEND` | `Sur le téléphone · à envoyer` | `#7A2210` |
| `QUEUED` | `En file sur le PC` (ajouté) | `#16161A` |
| `TRANSLATING` | `Traduction en cours · N %` + barre | `#16161A`, point `blink` |
| `TO_FETCH` | `Traduit · à récupérer` | `#2B4C7E` |
| `UP_TO_DATE` sans alerte | `À jour partout` | `#2F7D4F` |
| `UP_TO_DATE` avec pages en VO | `À jour · N page(s) restée(s) en VO` | `#8A5A00` |
| `FAILED` | `Échec` + ` · page N` si connue (ajouté) | `#7A2210` |
| `NOT_TRANSLATED` | `Original sur le PC · non traduit` (ajouté) | `#5E5A52` |
| `CATALOG_ONLY` | `Dans Mihon, non téléchargé` (ajouté) | `#5E5A52` |
| manquant | `Manquant partout` | `#7A2210` |

Lignes verrouillées : `QUEUED`, `TRANSLATING`, manquant.

### 7.4 Transferts (`Sync.dc.html`, dossier `transfers`)

| Élément | Source |
|---|---|
| `PC-BUREAU` du schéma | `heartbeat.pcName` ; absent : `PC` |
| `upN`, `downN` | `TO_SEND` total ; jobs RESULT_READY |
| « En cours maintenant » | jobs UPLOADING (`envoi N %` = `transferredBytes / transferTotal`) et DOWNLOADING (`réception N %`, même forme) ; aucun : `Aucun transfert en cours` (ajouté) |
| bloc « À envoyer » | 3 séries ayant le plus de chapitres `TO_SEND` : titre, `chapterRangeFr`, `sizeFr` ; `+N autres séries (M ch.)` si plus de 3, sinon masqué |
| case « Traduire dès l'arrivée… » | `translate_on_arrival` |
| bouton d'envoi | libellés du script, avec le nombre réel |
| bloc « À récupérer » | mêmes règles, sur les jobs RESULT_READY ; `Rangé` pour ceux installés pendant l'action ; libellés du script (`Téléchargement x/y...`) |
| carte scan | `Mihon/downloads · scanné ` + `relativeFr(last_scan_at)` ; pendant le scan `Analyse de N séries...` (nombre de dossiers séries) ; « Rescanner » |
| « 2 chapitres manquants partout » | somme des manquants de toutes les séries ; 0 : masqué ; appui -> Fiche série de la première série concernée |

### 7.5 File (`Queue.dc.html`, dossier `queue`)

| Élément | Source |
|---|---|
| compteurs d'onglets | Actives = jobs actifs ; Terminées = jobs RESULT_READY ou INSTALLED des 7 derniers jours ; Échecs = jobs en échec non relancés |
| emplacements `N/4` | `activeJobIds.size` / `slots` ; nombre de points = `slots` |
| carte sombre | job suivi (par défaut le premier de `activeJobIds`) : mode, titre, `Page x sur y · moteur` (`pcPage`, `pcPages`, `pcEngine`), %, étape courante = index de `pcStage` dans `fetch, ocr, translate, lettering, verify` |
| autres jobs actifs | le reste de `activeJobIds` ; appui -> devient le job suivi |
| « Retour auto » | reprends le comportement et le délai du script ; si le script n'en a pas, le suivi revient au premier job 8 s après le dernier appui (à signaler) |
| « Ensuite (N) » | jobs actifs hors `activeJobIds`, tri : PREPARING et UPLOADING d'abord (avec la progression d'envoi, CdC 9.7), puis `pcQueuePosition` croissant, puis `createdAt` ; `pos` à partir de `activeJobIds.size + 1`, 2 chiffres ; `src` = `<mode> · depuis le téléphone` si un `source.cbz` a été envoyé, sinon `<mode> · original déjà sur le PC` ; `Vérification · page ciblée` si `pages` non nul |
| ✕ | `cancel(jobId)` |
| Terminées | titre ; `when` = `Aujourd'hui HH:mm`, `Hier HH:mm`, sinon `jj/mm HH:mm` ; état `À récupérer` ou `Dans Mihon` ; puces de `quality` ; alertes `p. (page+1) · <libellé du script selon kind>` ; « Vérifier la page » -> `verifyPage` puis « Dans la file » si un job actif `verify` avec ces `pages` existe |
| Échecs | titre ; `Échec page N : <pcErrorMessage>` (sans page : `Échec : <message>`) ; `relativeFr(statusUpdatedAt)` + ` · les pages 1 à N-1 sont conservées` si N > 1 ; bouton « Réessayer à partir de la page N » (sans page : `Réessayer`, ajouté) -> `retry` ; une carte par job ; aucun : « Aucun échec en cours. » |
| « Régler » | `TranslationSettingsRoute` |
| icône + | ouvre la feuille F |

### 7.6 Catalogue (`Catalog.dc.html`, dossier `catalog`)

| Élément | Source |
|---|---|
| fichier | `backup_import.name` |
| `Importée … · depuis Mihon/autobackup` | date d'import : `aujourd'hui HH:mm`, `hier HH:mm`, sinon `le jj/mm` |
| progression | étapes réelles de l'import, dans l'ordre des 5 libellés du script ; barre 20 % par étape terminée |
| séries / non téléchargées / catégories | `series` ; séries sans aucun chapitre téléchargé ni FR ; `category` |
| catégories | nom, nombre de séries, barre relative au maximum ; ajoute `Sans catégorie` (ajouté) si des séries n'en ont aucune ; appui -> `LibraryFilteredRoute` |

### 7.7 Feuille F (`NewTranslation.dc.html`, dossier `newtranslation`)

| Élément | Source |
|---|---|
| séries | séries ayant des chapitres `TO_SEND`, tri par nombre décroissant ; `sub` = première catégorie seule (la langue de la série est inconnue en V1, à signaler) |
| chapitres | `TO_SEND` de la série, tous cochés ; `pages` = `pageCount` |
| « Traduire à l'arrivée » | valeur initiale `translate_on_arrival` ; la changer ici ne modifie pas la préférence |
| envoi | `createJobs(translate, cochés, translate = case)` ; `Dépôt sur Google Drive...` pendant la création des jobs en Room, puis « Ajouté à la file · Voir » (onglet File) |

### 7.8 Réglages (`Settings.dc.html`, dossier `settings`)

| Élément | Source |
|---|---|
| carte PC | `pcName`, `TMM-Win <appVersion> · protocole v1`, état en ligne ou hors ligne (règle 7.1), `relativeFr(updatedAt)` ; sans heartbeat : `PC` et `Aucun signal reçu` (ajouté) |
| Compte | `google_email` ; « Changer » -> flux d'autorisation interactif |
| Conservation | `retention` ; « Modifier » fait tourner les 3 valeurs (comme le script) |
| Dossier Mihon | chemin lisible tiré de l'URI SAF (`DocumentsContract.getTreeDocumentId`, partie après `:`, préfixée de `/`) ; « Changer » -> sélecteur de dossier puis `Startup` |
| Dernière sauvegarde lue | date d'import + ` · N séries` ; lien -> Catalogue |
| Refaire l'installation | `OnboardingRoute` (sans effacer les données) |
| Réglages de traduction du PC | `<moteur court> · <source> vers <cible> · N en parallèle` depuis `settings.json` ; moteur court : gemini `Gemini`, openai `OpenAI`, claude `Claude`, deepl `DeepL`, groq `Groq`, openrouter `OpenRouter`, tokenhub `TokenHub`, offline `Hors ligne`, local_llm `LLM local` (vérifie ces clés contre la liste fermée de 7.5 du CdC ; toute clé en plus ou en moins va au rapport) |
| Wi-Fi uniquement | `wifi_only` |
| Journaux | `7 derniers jours · <taille>` ; « Exporter » (E10.4) |
| pied | `TMM Mobile <BuildConfig.VERSION_NAME> · protocole v1` |

### 7.9 Traduction sur le PC (`TranslationSettings.dc.html`, dossier `translationsettings`)

| Élément | Source |
|---|---|
| valeurs | `SettingsSync` (5.18), libellés de la table `O` du script |
| bandeau | `SYNCED` « Synchronisé avec <pcName> » ; `SENDING` « Envoi au PC... » ; `APPLIED` « Appliqué sur <pcName> » ; `WAITING_PC` « En attente du PC » / « Sera appliqué au prochain démarrage du PC » (fond `#8A5A00`, ajouté) |
| clignotement | la ligne modifiée prend `#FBF1D6` pendant 1800 ms (`last` du script) |
| lecture seule | aucun `settings.json` : contrôles désactivés, alpha 0.45, bandeau `WAITING_PC` avec `Les réglages s'afficheront au premier signal du PC` (ajouté) |

### 7.10 Onboarding (`Onboarding.dc.html`, dossier `onboarding`)

| Étape | Source |
|---|---|
| 1 | bouton -> `GoogleAuth` interactif ; `Connexion...` pendant ; puis `ensureLayout` et un `poll` : carte `google_email`, `Dossier TranslateMyManga/ trouvé` ou `Dossier TranslateMyManga/ créé` (ajouté) + ` · PC vu il y a …` si heartbeat, sinon ` · PC pas encore vu` (ajouté) |
| 2 | `OpenDocumentTree` ; `Vérification...` pendant ; chemin lisible ; sous-dossiers (E6.7) |
| 3 | nom du `.tachibk` le plus récent de `autobackup/` ; import ; compteurs animés (20 pas de 60 ms) vers séries, catégories, séries ayant au moins un chapitre téléchargé ; message sur le nombre de `TO_SEND` (0 : masqué). Pas de sauvegarde : compteurs à 0 et texte « Pas encore de sauvegarde Mihon » de l'écran Vide |
| fin | `onboarding_done = true`, navigation vers `HomeRoute` en vidant la pile |

### 7.11 Vide (`Empty.dc.html`)

Sections affichées selon le cas : « Aucun chapitre téléchargé » si aucun `downloaded_chapter` ; « Pas encore de sauvegarde Mihon » si aucun `backup_import` ; « File vide » si aucun job actif. En-tête identique à l'Accueil (pastille PC réelle). « Scanner Mihon » lance scan + import ; libellés du script.

### 7.12 Hors ligne (`Offline.dc.html`)

Bandeau global (5.19). Si pas de réseau **et** aucun `heartbeat_json` : pastille `État du PC inconnu` (texte de la maquette). Le bloc « En attente d'envoi » liste les jobs PREPARING et UPLOADING regroupés par série (`chapterRangeFr`), `why` selon la cause (script) ; le bloc « Toujours disponible » est fixe. Hors ligne avec des données : l'Accueil normal s'affiche sous le bandeau, avec le bloc « En attente d'envoi » en tête s'il y a des jobs en attente.

### 7.13 Permission (`Permission.dc.html`)

Dossier attendu = dernier chemin lisible connu (`mihon_tree_uri`). « Choisir à nouveau le dossier » -> `OpenDocumentTree` ; vérification des 3 sous-dossiers ; succès -> « Accès rétabli » puis « Revenir à l'accueil » (lance `Startup`) ; échec -> reste sur l'écran avec `Ce dossier ne contient pas downloads/.` (ajouté). « Aide et réglages » -> Réglages.

---

## 8. Récapitulatif des tests et commandes

| Commande | Quand | Attendu |
|---|---|---|
| `./gradlew testDebugUnitTest` | avant chaque commit | 0 échec |
| `./gradlew recordRoborazziDebug` | E3, E4, E10, E11 | captures dans `app/src/test/screenshots/` et `docs/rapport_gemini/compare/` |
| `./gradlew verifyRoborazziDebug` | E10, E11 | 0 différence avec les captures commitées |
| `./gradlew lintDebug` | E11 | 0 erreur |
| `./gradlew assembleDebug` | E1, E11 | APK produit |
| `./gradlew assembleRelease` | E11 | réussit sans `keystore.properties` |

Classes de test présentes à la fin (au minimum) : `SmokeTest`, `NoRawColorTest`, `NoMaterialIconsTest`, `PaletteCoverageTest`, `IconCoverageTest`, `ComponentsBehaviorTest`, `ComponentsScreenshotTest`, `MockupTextCoverageTest`, 13 `<Ecran>ScreenshotTest`, 13 `<Ecran>BehaviorTest`, `DaoTest`, `ChapterStatusTest`, `MissingChaptersTest`, `SeriesSummaryTest`, `FormattersTest`, `BackupReaderTest`, `MihonNamingTest`, `DownloadScannerTest`, `MatcherTest`, `CbzPackerTest`, `LocalInstallerTest`, `ProtocolModelsTest`, `DriveTransportTest`, `JobFlowTest`, `JobRepositoryTest`, `RefreshCoordinatorTest`, `PhoneStateTransitionTest`, `SettingsSyncTest`, un `<Ecran>ViewModelTest` par écran.

Règle : aucun test ne dépend du réseau réel ni de l'heure réelle (`Clock` injecté, `mainClock` de Compose, `runTest` avec `TestDispatcher`). Aucun test désactivé (`@Ignore`) sans entrée `BLOQUÉ` dans le rapport.

---

## 9. Rapport `docs/RAPPORT_GEMINI.md`

Crée-le en E0 avec ce squelette exact, puis complète-le à chaque étape (dans le même commit que l'étape). Il sera relu par un autre agent : sois factuel, pas de formule de politesse, pas d'auto-évaluation (« excellent », « parfait »). Chaque affirmation vérifiable est suivie de sa preuve (commande, chemin, extrait de sortie).

````markdown
# Rapport d'exécution TMM Mobile V1

## 1. Résumé
- Branche : feat/v1-android
- Dernier commit : <sha court>
- Étapes terminées : E0..En
- Tests : <N> exécutés, <N> réussis, <N> échoués, <N> ignorés (dernière exécution de testDebugUnitTest)
- Blocages : <nombre> (section 7)

## 2. Étapes
| Étape | Commit (sha court) | État (fait / partiel / bloqué) | Remarque en une ligne |
|---|---|---|---|

## 3. Environnement et versions
| Outil ou artefact | Version | URL consultée |
|---|---|---|
Plus : SHA du commit google/fonts et fichiers de police copiés ; empreinte SHA-1 de la clé debug (E11).

## 4. Documentation externe lue
| Sujet | URL | Ce qui a été retenu (une ligne) |
|---|---|---|

## 5. Mihon
- Dépôt, commit : <sha>
- Fichiers lus : <chemin> -> <ce qui a été repris>
- Règle de nettoyage des noms : <copie de la fonction reprise, avec son chemin>
- Suffixe des téléchargements incomplets : <valeur> (<chemin:ligne>)
- Lien manga / catégories dans la restauration : <explication en 2 lignes, chemin>

## 6. Écarts
### 6.1 Écarts visuels (par écran)
| Écran | Image de comparaison | Différence restante | Cause |
|---|---|---|---|
### 6.2 Écarts au plan ou au CdC
| Référence (section) | Ce que dit le document | Ce qui a été fait | Pourquoi |
|---|---|---|---|
### 6.3 Textes ajoutés (absents des maquettes)
| Ressource | Texte | Écran | Référence du plan |
|---|---|---|---|
### 6.4 Lignes DEMO de mockup_texts.tsv
| Fichier | Texte | Donnée réelle qui le remplace |
|---|---|---|

## 7. Blocages
### BLOQUÉ-n
- Étape :
- Problème :
- Essais :
- Contournement appliqué :
- Ce qu'il faut pour débloquer :

## 8. Fichiers
Sortie de `git ls-files app/src | sort`.

## 9. Tests
- Sortie résumée de `./gradlew testDebugUnitTest` (dernière exécution)
- Classes de test et nombre de tests par classe
- Captures : liste des fichiers de app/src/test/screenshots/ et docs/rapport_gemini/compare/

## 10. Actions pour l'utilisateur
1. Google Cloud : créer un projet (ou réutiliser celui de TMM-Win), activer l'API Google Drive, écran de consentement OAuth de type Externe, publié « En production ».
2. Créer un client OAuth « Android » : nom de paquet com.tmm.mobile, empreinte SHA-1 <valeur de la section 3>. Refaire avec l'empreinte de la clé release quand elle existera.
3. Pour signer en release : créer un keystore, remplir keystore.properties (modèle keystore.properties.example).
4. <autres actions constatées>

## 11. Points ouverts
Au minimum :
- Chapitres FR connus seulement par pc/index.json (sans result.json) : affichés « À récupérer » mais non récupérables par le téléphone. À traiter dans le pont Drive de TMM-Win.
- « Réessayer à partir de la page N » crée un nouveau job ; la reprise à la page N dépend du PC.
- Interrupteur « Traduction automatique » et bouton « Importer » absents des maquettes (plan 5.21).
- Pas de demande de permission de notification (Android 13+) : la notification de transfert peut être invisible.
- Langue de la série inconnue en V1 (feuille F, Fiche série).
- <autres>

## 12. Limites connues
<liste>
````

---

## 10. Ordre de lecture pour démarrer

1. Ce plan en entier.
2. `docs/CAHIER_DES_CHARGES.md` en entier.
3. `maquettes/README.md`, puis chaque `maquettes/*.dc.html` (gabarit et script) avec sa capture `maquettes/captures/<Nom>.png`.
4. Commence E0.
