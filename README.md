# PPE - Photos

Générateur de **planches contact** pour Android : sélectionnez des photos de votre galerie et obtenez un **PDF A4 imprimable** où chaque photo est une **carte photo au format exact A4 à A10**, prête à découper le long des traits de coupe.

Conçu pour l'impression rapide : les photos sont redimensionnées **avant** intégration au PDF (300 DPI, JPEG embarqué), ce qui donne des planches de quelques mégaoctets seulement.

![Licence](https://img.shields.io/badge/licence-GPLv3-blue)

## Fonctionnalités

- **Sélection** des photos via le sélecteur système (aucune permission requise, jusqu'à 100 photos par session, accumulation possible)
- **7 formats de cartes** : A4, A5, A6, A7, A8, A9, A10 — la taille réelle en mm s'affiche sur chaque puce
- **Remplissage maximal** : chaque format An remplit exactement la page A4 avec 2^(n−4) cartes (A5 = 2, A6 = 4, A7 = 8, A8 = 16, A9 = 32, A10 = 64), dans les deux orientations de page
- **Traits de découpe** en pointillés autour de chaque carte (aucune légende, pas de marge : cartes bord à bord)
- **Recadrage centré** : la photo remplit sa carte comme un tirage photo ; rotation automatique de 90° si l'orientation de la photo diffère de celle de la carte
- **Aperçu live** de la grille avant génération
- **Multi-pages automatique** pour les photos en excès
- **Export** : enregistrement dans `Downloads/PlancheContact`, ouverture, partage et impression (PrintManager)
- **Changer le format** après génération, sans re-sélectionner les photos
- Interface en français, thème clair/sombre automatique

## Utilisation

1. **Choisir des photos** — le sélecteur système s'ouvre
2. **Configurer** — orientation de la page (portrait/paysage) et format des cartes (A4→A10), avec aperçu de la grille
3. **Générer** — la planche est enregistrée et s'ouvre depuis l'écran résultat : Ouvrir, Imprimer, Partager ou Changer le format

## Compilation

Prérequis : JDK 17, Android SDK (plateforme 34), appareil Android 10+ (API 29+) pour l'installation.

```bash
# Compiler l'APK
./gradlew assembleDebug

# Installer sur l'appareil connecté (USB)
./gradlew installDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/app-debug.apk`.

## Tests

```bash
# Tests unitaires JVM (logique de grille + générateur PDF)
./gradlew :app:testDebugUnitTest

# Tests instrumentés (appareil connecté)
./gradlew :app:connectedDebugAndroidTest
```

## Architecture

```
app/src/main/java/com/tmenard/planchecontact/
├── MainActivity.kt                 # PlancheApp + thème Compose
├── model/
│   └── ContactSheetViewModel.kt    # état de l'app (photos, spec, écran, UiState)
├── pdf/
│   ├── GridCalculator.kt           # disposition pure des cartes (formats An, pagination)
│   ├── ThumbnailDecoder.kt         # décodage échantillonné des miniatures
│   ├── ContactSheetPdfWriter.kt    # recadrage/rotation/JPEG + orchestration
│   └── PdfBuilder.kt               # générateur PDF (Kotlin pur, testé unitairement)
├── export/
│   ├── PdfExporter.kt              # enregistrement MediaStore (Downloads)
│   ├── PdfPrintAdapter.kt          # impression via PrintManager
│   └── PdfIntents.kt               # ouverture / partage
└── ui/
    ├── SelectionScreen.kt          # grille de sélection des photos
    ├── ConfigScreen.kt             # format, orientation, aperçu
    └── ResultScreen.kt             # progression, ouvrir/imprimer/partager
```

Point notable : le `PdfDocument` natif de Skia encode toujours les images en Flate sans perte (vérifié par test instrumenté sur appareil). L'app utilise donc un **générateur PDF maison** (`PdfBuilder`) qui embarque les photos en JPEG — planches 5 à 10 fois plus légères, sans perte de qualité d'impression.

## Licence

Ce projet est distribué sous licence **GNU GPL v3** — voir le fichier [LICENSE](LICENSE).
