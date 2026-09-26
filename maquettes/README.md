# Maquettes TMM Mobile

Référence visuelle et comportementale de l'app (textes, tailles, couleurs, animations, états). Format téléphone 390 × 844 px.

## Voir les maquettes

Les fichiers `.dc.html` doivent être servis en HTTP (ouverts en `file://`, ils affichent des `{{ trous }}` bruts) :

```bash
cd maquettes
python -m http.server 8765
```

Puis ouvrir `http://localhost:8765/index.html`. Les polices viennent de Google Fonts (connexion requise pour un rendu exact).

## Contenu

| Fichier | Écran (CdC section 9) |
|---|---|
| `Onboarding.dc.html` | 1. Onboarding |
| `Main.dc.html` | 2. Accueil |
| `Library.dc.html` | 3. Bibliothèque (bouton « Sélectionner » pour voir le mode sélection) |
| `Series.dc.html` | 4. Fiche série |
| `Sync.dc.html` | 5. Transferts |
| `Queue.dc.html` | 6. File |
| `Catalog.dc.html` | 7. Catalogue |
| `Settings.dc.html` | 8. Réglages |
| `TranslationSettings.dc.html` | 9. Traduction sur le PC |
| `Permission.dc.html` | 10. Permission Mihon perdue |
| `NewTranslation.dc.html` | F. Feuille « Nouvelle traduction » |
| `Empty.dc.html` | E1. État vide |
| `Offline.dc.html` | E2. Hors ligne (variante « jeton expiré » : propriété `cause` = `token` dans le script) |
| `captures/*.png` | Capture de chaque écran à l'état initial, 780 × 1688 (densité 2) |
| `support.js` | Moteur d'exécution des `.dc.html` (ne pas modifier) |
| `canvas.json` | Disposition du canevas de conception d'origine |
| `v0/` | Premières maquettes, archivées. `v0/Pairing.html` sert pour la V2 |

## Lire un fichier `.dc.html`

- Le gabarit est dans `<x-dc>` : HTML avec styles en ligne. Les tailles en `px` des maquettes se lisent comme des `dp` Android (et des `sp` pour les polices).
- `{{ nom }}` : valeur calculée par `renderVals()` dans le `<script>` en bas du fichier.
- `<sc-if value="{{ x }}">` : bloc affiché si `x` est vrai. `<sc-for list="{{ l }}" as="i">` : bloc répété pour chaque élément.
- `<helmet>` : styles communs, dont les animations (`rise`, `pop`, `sheet`, `toast`, `spin`, `pulse`, `grow`...).
- Le `<script>` (`class Component extends DCLogic`) contient les données factices, les états et les transitions (durées, délais, textes dynamiques).
