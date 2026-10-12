# Maquettes TMM Mobile

Référence visuelle et comportementale de l'app (textes, tailles, couleurs, états). Format téléphone 390 × 844 px ; les `px` des maquettes se lisent comme des `dp` Android (et des `sp` pour les polices).

## Quelle maquette fait foi

| Dossier | Statut | Écrans |
|---|---|---|
| `v2-simplifiee/index.html` | **Référence actuelle** | Accueil, Bibliothèque (et ses Filtres), Fiche série, Sélection et « Plus d'actions », Suivre une série, Feuille « Traduire », Activité, Réglages, Réglages de traduction du PC |
| `*.dc.html` à la racine | Toujours en vigueur (inchangés par la v2) | Onboarding, Permission Mihon perdue, État vide, Hors ligne |
| `v1/` | Archivée | Ancienne interface à 5 onglets (Catalogue, File, Transferts, Nouvelle traduction...). Ne plus s'en servir pour coder |
| `v0/` | Archivée | Premières maquettes. `v0/Pairing.html` reste la base de l'appairage direct |

La v2 tient en un seul fichier HTML statique : il s'ouvre directement dans un navigateur, sans serveur. La fin du fichier liste où est passée chaque action de la v1 et les points restés à décider.

## Captures

| Dossier | Contenu |
|---|---|
| `v2-simplifiee/captures/*.png` | Rendu de l'app (Roborazzi) pour chaque écran v2, 780 × 1688 (densité 2) |
| `captures/*.png` | Capture des écrans communs à l'état initial, 780 × 1688 |
| `v1/captures/*.png` | Captures de la v1, archivées |

Les captures v2 sont produites par les tests Roborazzi (`app/src/test/java/com/tmm/mobile/**/*ScreenshotTest.kt`, qualificatif `w390dp-h844dp-xhdpi`). Il n'y a pas de comparaison automatique avec une référence : on enregistre, puis on compare à l'œil avec la maquette.

```bash
./gradlew testDebugUnitTest --tests "*ScreenshotTest" "-Proborazzi.test.record=true"
```

Les images sortent dans `app/build/outputs/roborazzi/` (non versionné) ; copier celles qui changent dans `v2-simplifiee/captures/`.

## Voir les `.dc.html`

Les fichiers `.dc.html` (racine et `v1/`) doivent être servis en HTTP (ouverts en `file://`, ils affichent des `{{ trous }}` bruts) :

```bash
cd maquettes
python -m http.server 8765
```

Puis ouvrir `http://localhost:8765/index.html`. Les polices viennent de Google Fonts (connexion requise pour un rendu exact).

- Le gabarit est dans `<x-dc>` : HTML avec styles en ligne.
- `{{ nom }}` : valeur calculée par `renderVals()` dans le `<script>` en bas du fichier.
- `<sc-if value="{{ x }}">` : bloc affiché si `x` est vrai. `<sc-for list="{{ l }}" as="i">` : bloc répété pour chaque élément.
- `<helmet>` : styles communs, dont les animations (`rise`, `pop`, `sheet`, `toast`, `spin`, `pulse`, `grow`...).
- Le `<script>` (`class Component extends DCLogic`) contient les données factices, les états et les transitions.
- `support.js` : moteur d'exécution des `.dc.html` (ne pas modifier). `canvas.json` : disposition du canevas de conception d'origine.
