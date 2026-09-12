# Prêt pour l'école - Photos — Design (spec)

**Date :** 2026-09-12
**Statut :** Approuvé par l'utilisateur (v2 : formats An + gabarit de découpe)
**Plateforme :** Android natif (Kotlin)

## Objet

App Android « **Prêt pour l'école - Photos** » (icône PPE : chapeau blanc sur dégradé rose, issue de `~/work/ppe`) permettant de créer des planches contact à partir des photos de la galerie, exportées en **PDF A4** optimisé pour une **impression rapide** : les photos sont redimensionnées avant intégration au PDF.

## Décisions utilisateur

| Sujet | Décision |
|---|---|
| Format de sortie | PDF A4, orientation portrait **ou** paysage au choix |
| Densité | **Formats A4→A10 au choix** : chaque vignette = une carte photo au format An exact, découpable ; la taille réelle (mm) s'affiche en petit sur la puce. La grille s'oriente automatiquement pour maximiser le nombre par page (ex : A6 = 2/page en 148×105 ; A10 = 50/page en 37×26) |
| Légendes | Aucune — remplacées par un **gabarit de découpage** : traits pointillés gris autour de chaque carte (v2 : « le numéro ne sert à rien ») |
| Remplissage | La photo **remplit** la vignette (recadrage centré, comme un tirage) ; rotation 90° quand l'orientation de la photo diffère de celle de la carte |
| En-tête | Supprimé en v2 (marges 4 mm, les cartes occupent la page ; un titre gênerait la découpe) |
| Itération | « Changer le format » depuis l'écran résultat → retour à la configuration, photos conservées |
| Technologie | Kotlin natif (vs React Native/Flutter, écartés) |
| Sélection photos | Sélecteur système (Photo Picker), zéro permission |
| Version Android min. | API 29 (Android 10+, téléphone de l'utilisateur) |

## 1. Architecture & écrans

App native Kotlin, single-activity, **Jetpack Compose** (Material 3), interface en **français**, thème clair/sombre automatique. 3 écrans :

1. **Sélection** — bouton « Choisir des photos » → Photo Picker système (multi-sélection, zéro permission, max 100/session, accumulation possible). Grille des miniatures (Coil), suppression individuelle. L'ordre = ordre de sélection.
2. **Configuration** — orientation de la page (portrait/paysage), 7 puces de format A4→A10 avec taille réelle en mm, **aperçu live du rendu de la page 1** (grille centrée, identique au PDF), résumé « N photo(s) • X photos/page • Y page(s) ».
3. **Résultat** — progression, puis boutons **Ouvrir / Imprimer / Partager / Changer le format / Refaire**.

Pas de base de données, session en mémoire. Réordonnancement drag & drop exclu (v1, YAGNI).

## 2. Pipeline de génération (cœur du besoin)

- Décodage **échantillonné** de chaque photo directement à la taille de sa vignette (`ContentResolver.loadThumbnail`, API 29+) — jamais de bitmap pleine résolution en mémoire, traitement **séquentiel** (pas d'OOM possible).
- Taille cible = **plus long côté** de la carte × **300 DPI** (levier de poids, ajustable à 200).
- Rendu sur canvas `android.graphics.pdf.PdfDocument` : **page A4 toujours**, marges 4 mm, écart 2 mm entre cartes, grille **centrée** dans l'espace restant, photo en recadrage centré remplissant la carte, **traits pointillés gris (0,8 pt, pattern 4/3)** = lignes de découpe sur chaque carte, format A4 = carte pleine page sans marge.
- Photos en excès → **multi-pages automatique**.
- Critère d'acceptation : **50 photos ≈ PDF < 5 Mo** (vs 50-100 Mo sans redimensionnement) → impression quasi instantanée.

**Écart documenté :** la compression JPEG dans le PDF est gérée par le moteur natif (`PdfDocument`/Skia) — pas de contrôle direct de la qualité JPEG ; le levier de poids est la taille des miniatures (DPI). Pas de `inSampleSize` manuel : `loadThumbnail` fait l'échantillonnage nativement.

## 3. Export

- PDF enregistré dans **Downloads/PlancheContact** via MediaStore (API 29+, aucune permission), visible dans l'app Fichiers.
- « Imprimer » → PrintManager Android (toute imprimante configurée, via un `PrintDocumentAdapter` qui recopie les octets du PDF).
- « Ouvrir » / « Partager » → intents `ACTION_VIEW` / `ACTION_SEND` avec grant read sur l'URI MediaStore.

## 4. Erreurs & cas limites

- 0 photo → génération désactivée.
- Photo corrompue/illisible → vignette grise, on continue.
- Erreur de génération → écran d'erreur avec « Recommencer ».

## 5. Build & tests

- JDK 17 (Temurin, installé via brew), SDK Android (plateformes 34, émulateur Pixel_10_Pro API 37 dispo), Gradle 8.7 (cache local).
- Gradle Kotlin DSL, AGP 8.6.1, Kotlin 2.0.21, compileSdk 34, minSdk 29, targetSdk 34.
- Tests unitaires (JUnit) : logique de grille pure (formats An, pagination, tailles de cellules, taille cible des miniatures).
- E2E sur **téléphone réel SM-G991N** (Galaxy S21, Android 15, adb R3CR20ASRJT) : génération, vérification taille PDF, test impression, contrôle visuel des traits de découpe. (Émulateur abandonné : SwiftShader sur Mac Intel, boot bloqué.)
- Livraison : `app-debug.apk` installable sur le téléphone de l'utilisateur (`./gradlew installDebug`).

## 6. Structure des fichiers

```
plancheContact/
├── settings.gradle.kts / build.gradle.kts / gradle.properties / local.properties / .gitignore
├── docs/superpowers/{specs,plans}/
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/AndroidManifest.xml
        ├── main/res/ (icône adaptative, strings, colors)
        ├── main/java/com/tmenard/planchecontact/
        │   ├── MainActivity.kt            (+ PlancheApp, PlancheTheme)
        │   ├── model/ContactSheetViewModel.kt  (PhotoItem, AppScreen, UiState)
        │   ├── pdf/GridCalculator.kt       (SheetSpec, SheetLayout — logique pure)
        │   ├── pdf/ThumbnailDecoder.kt
        │   ├── pdf/ContactSheetPdfWriter.kt
        │   ├── export/PdfExporter.kt       (MediaStore Downloads)
        │   ├── export/PdfPrintAdapter.kt   (+ printPdf)
        │   ├── export/PdfIntents.kt       (openPdf, sharePdf)
        │   └── ui/SelectionScreen.kt, ConfigScreen.kt, ResultScreen.kt
        └── test/java/com/tmenard/planchecontact/pdf/GridCalculatorTest.kt
```
