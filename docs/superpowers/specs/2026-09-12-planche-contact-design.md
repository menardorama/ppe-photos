# Planche Contact — Design (spec)

**Date :** 2026-09-12
**Statut :** Approuvé par l'utilisateur
**Plateforme :** Android natif (Kotlin)

## Objet

App Android permettant de créer des planches contact (grilles de vignettes numérotées) à partir des photos de la galerie, exportées en **PDF A4** optimisé pour une **impression rapide** : les photos sont redimensionnées avant intégration au PDF.

## Décisions utilisateur

| Sujet | Décision |
|---|---|
| Format de sortie | PDF A4, orientation portrait **ou** paysage au choix |
| Densité | Gabarits au choix, il en faut beaucoup → slider colonnes 3-8 × 2 orientations |
| Légendes | Numéro sous chaque vignette |
| Technologie | Kotlin natif (vs React Native/Flutter, écartés) |
| Sélection photos | Sélecteur système (Photo Picker), zéro permission |
| Version Android min. | API 29 (Android 10+, téléphone de l'utilisateur) |

## 1. Architecture & écrans

App native Kotlin, single-activity, **Jetpack Compose** (Material 3), interface en **français**, thème clair/sombre automatique. 3 écrans :

1. **Sélection** — bouton « Choisir des photos » → Photo Picker système (multi-sélection, zéro permission, max 100/session, accumulation possible). Grille des miniatures (Coil), suppression individuelle. L'ordre = ordre de sélection.
2. **Configuration** — orientation (portrait/paysage), densité (slider colonnes 3→8, lignes auto-calculées), **aperçu live du rendu de la page 1**, titre d'en-tête éditable (défaut : « Planche contact » + date auto).
3. **Résultat** — progression, puis boutons **Ouvrir / Imprimer / Partager / Refaire**.

Pas de base de données, session en mémoire. Réordonnancement drag & drop exclu (v1, YAGNI).

## 2. Pipeline de génération (cœur du besoin)

- Décodage **échantillonné** de chaque photo directement à la taille de sa vignette (`ContentResolver.loadThumbnail`, API 29+) — jamais de bitmap pleine résolution en mémoire, traitement **séquentiel** (pas d'OOM possible).
- Taille cible = dimension papier de la vignette × **300 DPI** (levier de poids, ajustable à 200).
- Rendu sur canvas `android.graphics.pdf.PdfDocument` : marges A4 (~12 mm), vignettes à ratio conservé centrées dans des cellules 3:2 uniformes, numéro sous chaque vignette, en-tête (titre + date) + « Page X/Y » en pied.
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
- Tests unitaires (JUnit) : logique de grille pure (colonnes, pagination, tailles de cellules, taille cible des miniatures).
- E2E sur émulateur : 50 photos poussées, génération, vérification taille PDF < 5 Mo, test impression (« Enregistrer au format PDF »), contrôle visuel du PDF sur macOS (Preview).
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
