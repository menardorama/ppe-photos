# Planche Contact — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** App Android native (Kotlin/Compose) qui génère des planches contact PDF A4 imprimables rapidement à partir des photos de la galerie (photos redimensionnées avant intégration au PDF).

**Architecture :** Single-activity Jetpack Compose, 3 écrans (Sélection → Configuration → Résultat) pilotés par un ViewModel. Pipeline : décodage miniatures séquentiel (`loadThumbnail`) → rendu canvas `PdfDocument` → sauvegarde MediaStore Downloads + impression/partage. Logique de grille pure Kotlin testée unitairement (TDD).

**Tech Stack :** Kotlin 2.0.21, AGP 8.6.1, Gradle 8.7, compileSdk 34, minSdk 29, Compose BOM + Material 3, Coil 2.7.0, JUnit 4.

**Écart au design approuvé (documenté) :** la compression JPEG dans le PDF est gérée par le moteur natif (`PdfDocument`) — le levier de poids est la taille des miniatures (300 DPI, ajustable). Pas de `inSampleSize` manuel : `loadThumbnail` (API 29+) fait l'échantillonnage nativement.

**Environnement vérifié :** JDK 17 Temurin (`/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`), SDK Android `/Users/tmenard/Library/Android/sdk` (plateformes 34/36.1, émulateur + AVD `Pixel_10_Pro`), Gradle 8.7 en cache (`~/.gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle`).

**Structure des fichiers :**
```
plancheContact/
├── settings.gradle.kts / build.gradle.kts / gradle.properties / .gitignore / local.properties
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/AndroidManifest.xml
        ├── main/res/ (mipmap icône adaptative, values/strings.xml, values/colors.xml, drawable/)
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

---

### Tâche 0 — Docs + git

- [ ] Écrire `docs/superpowers/specs/2026-09-12-planche-contact-design.md` (design approuvé)
- [ ] Écrire ce plan dans `docs/superpowers/plans/2026-09-12-planche-contact.md`
- [ ] `git init && git add docs && git commit -m "docs: spec et plan de l'app planche contact"`

### Tâche 1 — Scaffold Gradle + app vide compilable

**Fichiers :** tous les fichiers racine + manifest + icône + MainActivity minimal.

- [ ] `local.properties` : `sdk.dir=/Users/tmenard/Library/Android/sdk`
- [ ] `gradle.properties` :
```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.java.home=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```
- [ ] `settings.gradle.kts` :
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "PlancheContact"
include(":app")
```
- [ ] `build.gradle.kts` (racine) :
```kotlin
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```
- [ ] `app/build.gradle.kts` :
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tmenard.planchecontact"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tmenard.planchecontact"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
}
```
- [ ] `.gitignore` :
```
*.iml
.gradle
/local.properties
.idea
.DS_Store
/build
/app/build
/captures
```
- [ ] Wrapper via le Gradle 8.7 caché : `~/.gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle wrapper --gradle-version 8.7` (distribution déjà en cache)
- [ ] `app/src/main/AndroidManifest.xml` :
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```
- [ ] `res/values/strings.xml` : `<string name="app_name">Planche Contact</string>`
- [ ] `res/values/colors.xml` : `<color name="ic_launcher_background">#3F51B5</color>`
- [ ] `res/mipmap-anydpi-v26/ic_launcher.xml` :
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```
- [ ] `res/drawable/ic_launcher_foreground.xml` (grille de vignettes blanches) :
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFF"
        android:pathData="M30,36h20v20h-20z M58,36h20v20h-20z M30,64h20v20h-20z M58,64h20v20h-20z" />
</vector>
```
- [ ] `MainActivity.kt` minimal :
```kotlin
package com.tmenard.planchecontact

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("Planche Contact") } }
    }
}
```
- [ ] Build : `./gradlew assembleDebug` → **BUILD SUCCESSFUL** (télécharge AGP/dépendances au 1er run)
- [ ] `git add -A && git commit -m "chore: scaffold du projet Android (AGP 8.6.1, Kotlin 2.0.21, Compose)"`

### Tâche 2 — GridCalculator (TDD)

**Fichiers :** `app/src/test/java/com/tmenard/planchecontact/pdf/GridCalculatorTest.kt`, `app/src/main/java/com/tmenard/planchecontact/pdf/GridCalculator.kt`

- [ ] Écrire le test d'abord :
```kotlin
package com.tmenard.planchecontact.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class GridCalculatorTest {

    private val portrait = SheetSpec(landscape = false, columns = 5, title = "Test")

    @Test
    fun `portrait 5 colonnes`() {
        val l = GridCalculator.computeLayout(portrait, 47)
        assertEquals(99.056f, l.cellWidthPt, 0.01f)   // (595.28-68-4*8)/5
        assertEquals(66.03f, l.cellHeightPt, 0.01f)  // cellW * 2/3
        assertEquals(7, l.rowsPerPage)                // floor(717.89 / 90.037)
        assertEquals(35, l.photosPerPage)
        assertEquals(2, l.pageCount)
    }

    @Test
    fun `paysage 6 colonnes`() {
        val l = GridCalculator.computeLayout(SheetSpec(landscape = true, columns = 6), 60)
        assertEquals(122.31f, l.cellWidthPt, 0.01f)   // (841.89-68-5*8)/6
        assertEquals(4, l.rowsPerPage)
        assertEquals(24, l.photosPerPage)
        assertEquals(3, l.pageCount)
    }

    @Test
    fun `zero photo = une page`() {
        assertEquals(1, GridCalculator.computeLayout(portrait, 0).pageCount)
    }

    @Test
    fun `taille cible miniature en pixels`() {
        val l = GridCalculator.computeLayout(portrait, 10)
        assertEquals(412, GridCalculator.thumbnailTargetPx(l)) // 99.056/72*300
    }
}
```
- [ ] `./gradlew :app:testDebugUnitTest --tests "com.tmenard.planchecontact.pdf.GridCalculatorTest"` → **FAIL** (unresolved reference)
- [ ] Implémentation :
```kotlin
package com.tmenard.planchecontact.pdf

import kotlin.math.ceil
import kotlin.math.floor

data class SheetSpec(
    val landscape: Boolean = false,
    val columns: Int = 5,
    val title: String = ""
) {
    val pageWidthPt: Float get() = if (landscape) 841.89f else 595.28f
    val pageHeightPt: Float get() = if (landscape) 595.28f else 841.89f
}

data class SheetLayout(
    val columns: Int,
    val rowsPerPage: Int,
    val photosPerPage: Int,
    val cellWidthPt: Float,
    val cellHeightPt: Float,
    val pageCount: Int
)

object GridCalculator {
    const val MARGIN_PT = 34f      // ~12 mm
    const val HEADER_PT = 40f
    const val FOOTER_PT = 24f
    const val GAP_PT = 8f
    const val CAPTION_PT = 16f
    const val CELL_ASPECT = 2f / 3f  // cellule 3:2
    const val PRINT_DPI = 300

    fun computeLayout(spec: SheetSpec, photoCount: Int): SheetLayout {
        val contentW = spec.pageWidthPt - 2 * MARGIN_PT
        val contentH = spec.pageHeightPt - 2 * MARGIN_PT - HEADER_PT - FOOTER_PT
        val cellW = (contentW - (spec.columns - 1) * GAP_PT) / spec.columns
        val cellH = cellW * CELL_ASPECT
        val rows = floor((contentH + GAP_PT) / (cellH + CAPTION_PT + GAP_PT)).toInt()
        val perPage = spec.columns * rows
        val pages = if (photoCount == 0) 1 else ceil(photoCount.toDouble() / perPage).toInt()
        return SheetLayout(spec.columns, rows, perPage, cellW, cellH, pages)
    }

    fun thumbnailTargetPx(layout: SheetLayout): Int =
        (layout.cellWidthPt / 72f * PRINT_DPI).toInt()
}
```
- [ ] Relancer le test → **PASS**
- [ ] `git commit -m "feat: calcul de grille des planches (colonnes, pagination, taille miniatures)"`

### Tâche 3 — ThumbnailDecoder

**Fichier :** `app/src/main/java/com/tmenard/planchecontact/pdf/ThumbnailDecoder.kt`

- [ ] Écrire :
```kotlin
package com.tmenard.planchecontact.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size

class ThumbnailDecoder(private val context: Context) {

    /** Décode une miniature échantillonnée (jamais la pleine résolution). null si illisible. */
    fun decode(uri: Uri, targetPx: Int): Bitmap? = try {
        context.contentResolver.loadThumbnail(uri, Size(targetPx, targetPx), null)
    } catch (e: Exception) {
        null
    }
}
```
(Pas de test unitaire — API Android ; vérifié en E2E tâche 10.)
- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] `git commit -m "feat: décodage de miniatures échantillonnées"`

### Tâche 4 — ContactSheetPdfWriter

**Fichier :** `app/src/main/java/com/tmenard/planchecontact/pdf/ContactSheetPdfWriter.kt`

- [ ] Écrire :
```kotlin
package com.tmenard.planchecontact.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

class ContactSheetPdfWriter(
    private val spec: SheetSpec,
    private val layout: SheetLayout
) {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val emptyPaint = Paint().apply { color = Color.LTGRAY }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 0.8f; color = Color.GRAY; style = Paint.Style.STROKE
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; color = Color.DKGRAY; textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f; isFakeBoldText = true; color = Color.BLACK
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f; color = Color.DKGRAY; textAlign = Paint.Align.RIGHT
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f; color = Color.GRAY; textAlign = Paint.Align.CENTER
    }

    fun write(
        photoCount: Int,
        dateText: String,
        thumbnail: (index: Int) -> Bitmap?,
        onProgress: (done: Int, total: Int) -> Unit
    ): ByteArray {
        val doc = PdfDocument()
        for (page in 0 until layout.pageCount) {
            val info = PdfDocument.PageInfo.Builder(
                spec.pageWidthPt.toInt(), spec.pageHeightPt.toInt(), page + 1
            ).create()
            val sheet = doc.startPage(info)
            val canvas = sheet.canvas
            drawHeader(canvas, dateText)
            drawFooter(canvas, page)
            for (i in 0 until layout.photosPerPage) {
                val index = page * layout.photosPerPage + i
                if (index >= photoCount) break
                drawCell(canvas, i, index, thumbnail(index))
                onProgress(index + 1, photoCount)
            }
            doc.finishPage(sheet)
        }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun drawHeader(canvas: Canvas, dateText: String) {
        val title = spec.title.ifBlank { "Planche contact" }
        canvas.drawText(title, GridCalculator.MARGIN_PT,
            GridCalculator.MARGIN_PT + 20f, titlePaint)
        canvas.drawText(dateText, spec.pageWidthPt - GridCalculator.MARGIN_PT,
            GridCalculator.MARGIN_PT + 16f, datePaint)
    }

    private fun drawFooter(canvas: Canvas, page: Int) {
        canvas.drawText("Page ${page + 1} / ${layout.pageCount}",
            spec.pageWidthPt / 2f,
            spec.pageHeightPt - GridCalculator.MARGIN_PT - 9f, footerPaint)
    }

    private fun drawCell(canvas: Canvas, slot: Int, index: Int, bitmap: Bitmap?) {
        val col = slot % layout.columns
        val row = slot / layout.columns
        val x = GridCalculator.MARGIN_PT + col * (layout.cellWidthPt + GridCalculator.GAP_PT)
        val y = GridCalculator.MARGIN_PT + GridCalculator.HEADER_PT +
            row * (layout.cellHeightPt + GridCalculator.CAPTION_PT + GridCalculator.GAP_PT)
        val cell = RectF(x, y, x + layout.cellWidthPt, y + layout.cellHeightPt)
        canvas.drawRect(cell, borderPaint)
        val bmp = bitmap
        if (bmp != null) {
            val scale = minOf(cell.width() / bmp.width, cell.height() / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dx = cell.left + (cell.width() - dw) / 2f
            val dy = cell.top + (cell.height() - dh) / 2f
            canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), bitmapPaint)
        } else {
            canvas.drawRect(cell, emptyPaint)
        }
        canvas.drawText("${index + 1}", cell.centerX(), cell.bottom + 11f, captionPaint)
    }
}
```
- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] `git commit -m "feat: rendu de la planche contact en PDF natif"`

### Tâche 5 — Export (MediaStore, impression, partage)

**Fichiers :** `export/PdfExporter.kt`, `export/PdfPrintAdapter.kt`, `export/PdfIntents.kt`

- [ ] `PdfExporter.kt` :
```kotlin
package com.tmenard.planchecontact.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun fileName(): String = "planche-contact-" +
        SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.FRANCE).format(Date()) + ".pdf"

    fun saveToDownloads(context: Context, bytes: ByteArray, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/PlancheContact")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Impossible de créer le fichier PDF")
        (resolver.openOutputStream(uri) as OutputStream).use { it.write(bytes) }
        return uri
    }
}
```
- [ ] `PdfPrintAdapter.kt` :
```kotlin
package com.tmenard.planchecontact.export

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileOutputStream

class PdfPrintAdapter(private val bytes: ByteArray, private val name: String) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder(name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build(),
            true
        )
    }

    override fun onWrite(
        pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?, callback: WriteResultCallback
    ) {
        try {
            FileOutputStream(destination.fileDescriptor).use { it.write(bytes) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}

fun printPdf(context: Context, bytes: ByteArray) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print("Planche contact",
        PdfPrintAdapter(bytes, "planche-contact.pdf"), PrintAttributes.Builder().build())
}
```
- [ ] `PdfIntents.kt` :
```kotlin
package com.tmenard.planchecontact.export

import android.content.Context
import android.content.Intent
import android.net.Uri

fun openPdf(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Ouvrir le PDF"))
}

fun sharePdf(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Partager la planche contact"))
}
```
- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] `git commit -m "feat: export PDF (Downloads, impression, partage)"`

### Tâche 6 — ViewModel

**Fichier :** `model/ContactSheetViewModel.kt`

- [ ] Écrire :
```kotlin
package com.tmenard.planchecontact.model

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tmenard.planchecontact.export.PdfExporter
import com.tmenard.planchecontact.pdf.ContactSheetPdfWriter
import com.tmenard.planchecontact.pdf.GridCalculator
import com.tmenard.planchecontact.pdf.SheetSpec
import com.tmenard.planchecontact.pdf.ThumbnailDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PhotoItem(val uri: Uri)

enum class AppScreen { SELECTION, CONFIG, RESULT }

sealed interface UiState {
    data object Idle : UiState
    data class Generating(val done: Int, val total: Int) : UiState
    data class Success(val uri: Uri, val fileName: String, val bytes: ByteArray) : UiState
    data class Error(val message: String) : UiState
}

class ContactSheetViewModel(app: Application) : AndroidViewModel(app) {

    private val decoder = ThumbnailDecoder(app)

    private val _photos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val photos: StateFlow<List<PhotoItem>> = _photos.asStateFlow()

    private val _spec = MutableStateFlow(SheetSpec())
    val spec: StateFlow<SheetSpec> = _spec.asStateFlow()

    private val _screen = MutableStateFlow(AppScreen.SELECTION)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun addPhotos(uris: List<Uri>) {
        val existing = _photos.value.map { it.uri.toString() }.toSet()
        _photos.value = _photos.value + uris
            .filter { it.toString() !in existing }.map { PhotoItem(it) }
    }

    fun removePhoto(item: PhotoItem) { _photos.value = _photos.value - item }

    fun setTitle(t: String) { _spec.value = _spec.value.copy(title = t) }
    fun setLandscape(b: Boolean) { _spec.value = _spec.value.copy(landscape = b) }
    fun setColumns(n: Int) { _spec.value = _spec.value.copy(columns = n.coerceIn(3, 8)) }

    fun goToConfig() { if (_photos.value.isNotEmpty()) _screen.value = AppScreen.CONFIG }
    fun backToSelection() { _screen.value = AppScreen.SELECTION }

    fun reset() {
        _photos.value = emptyList()
        _spec.value = SheetSpec()
        _state.value = UiState.Idle
        _screen.value = AppScreen.SELECTION
    }

    fun generate() {
        val photos = _photos.value
        if (photos.isEmpty()) return
        viewModelScope.launch {
            _state.value = UiState.Generating(0, photos.size)
            _screen.value = AppScreen.RESULT
            try {
                val (uri, name, bytes) = withContext(Dispatchers.IO) {
                    val spec = _spec.value
                    val layout = GridCalculator.computeLayout(spec, photos.size)
                    val targetPx = GridCalculator.thumbnailTargetPx(layout)
                    val dateText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
                        .format(Date())
                    val bytes = ContactSheetPdfWriter(spec, layout).write(
                        photoCount = photos.size,
                        dateText = dateText,
                        thumbnail = { i -> decoder.decode(photos[i].uri, targetPx) },
                        onProgress = { done, total ->
                            _state.value = UiState.Generating(done, total)
                        }
                    )
                    val name = PdfExporter.fileName()
                    Triple(PdfExporter.saveToDownloads(getApplication(), bytes, name), name, bytes)
                }
                _state.value = UiState.Success(uri, name, bytes)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }
}
```
- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] `git commit -m "feat: viewmodel du flux de génération"`

### Tâche 7 — Écran Sélection

**Fichier :** `ui/SelectionScreen.kt`

- [ ] Écrire :
```kotlin
package com.tmenard.planchecontact.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tmenard.planchecontact.model.PhotoItem

@Composable
fun SelectionScreen(
    photos: List<PhotoItem>,
    onAdd: (List<android.net.Uri>) -> Unit,
    onRemove: (PhotoItem) -> Unit,
    onNext: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris -> if (uris.isNotEmpty()) onAdd(uris) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Planche contact — ${photos.size} photo(s)") }) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        launcher.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Choisir des photos") }
                Button(onClick = onNext, modifier = Modifier.weight(1f),
                    enabled = photos.isNotEmpty()) { Text("Continuer") }
            }
        }
    ) { padding ->
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Aucune photo sélectionnée.\nTouchez « Choisir des photos ».",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
                    .padding(horizontal = 4.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(photos, key = { it.uri.toString() }) { photo ->
                    Box(Modifier.padding(4.dp).clip(RoundedCornerShape(8.dp))) {
                        AsyncImage(
                            model = photo.uri, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        )
                        IconButton(
                            onClick = { onRemove(photo) },
                            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) { Icon(Icons.Default.Close, "Retirer", tint = Color.White) }
                    }
                }
            }
        }
    }
}
```
- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] `git commit -m "feat: écran de sélection des photos"`

### Tâche 8 — Écran Configuration (avec aperçu live)

**Fichier :** `ui/ConfigScreen.kt`

- [ ] Écrire :
```kotlin
package com.tmenard.planchecontact.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tmenard.planchecontact.pdf.GridCalculator
import com.tmenard.planchecontact.pdf.SheetSpec

@Composable
fun ConfigScreen(
    spec: SheetSpec,
    photoCount: Int,
    setTitle: (String) -> Unit,
    setLandscape: (Boolean) -> Unit,
    setColumns: (Int) -> Unit,
    onBack: () -> Unit,
    onGenerate: () -> Unit
) {
    val layout = GridCalculator.computeLayout(spec, photoCount)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = spec.title,
                onValueChange = setTitle,
                label = { Text("Titre (défaut : Planche contact)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Orientation", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !spec.landscape,
                    onClick = { setLandscape(false) }, label = { Text("Portrait") })
                FilterChip(selected = spec.landscape,
                    onClick = { setLandscape(true) }, label = { Text("Paysage") })
            }
            Text("Colonnes : ${spec.columns}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = spec.columns.toFloat(),
                onValueChange = { setColumns(it.toInt()) },
                valueRange = 3f..8f,
                steps = 4
            )
            // Aperçu live de la page 1
            Canvas(
                Modifier.fillMaxWidth()
                    .aspectRatio(spec.pageWidthPt / spec.pageHeightPt)
                    .background(Color.White, MaterialTheme.shapes.small)
            ) {
                val scale = size.width / spec.pageWidthPt
                val m = GridCalculator.MARGIN_PT * scale
                val cellW = layout.cellWidthPt * scale
                val cellH = layout.cellHeightPt * scale
                val gap = GridCalculator.GAP_PT * scale
                val top = (GridCalculator.MARGIN_PT + GridCalculator.HEADER_PT) * scale
                val gray = Color(0xFF9E9E9E)
                drawLine(gray, Offset(m, m * 1.6f), Offset(m + cellW * 2, m * 1.6f), 2f)
                for (row in 0 until layout.rowsPerPage) {
                    for (col in 0 until layout.columns) {
                        drawRect(
                            Color(0xFFE0E0E0),
                            topLeft = Offset(m + col * (cellW + gap),
                                top + row * (cellH + (GridCalculator.CAPTION_PT + GridCalculator.GAP_PT) * scale)),
                            size = Size(cellW, cellH)
                        )
                    }
                }
            }
            Text(
                "$photoCount photo(s) • ${layout.photosPerPage} photos/page • " +
                "${layout.pageCount} page(s) • vignette ≈ " +
                "${(layout.cellWidthPt * 25.4f / 72f).toInt()} mm",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                enabled = photoCount > 0
            ) { Text("Générer la planche contact") }
        }
    }
}
```
- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] `git commit -m "feat: écran de configuration avec aperçu de la grille"`

### Tâche 9 — Écran Résultat + câblage

**Fichiers :** `ui/ResultScreen.kt`, `MainActivity.kt` (remplacement)

- [ ] `ResultScreen.kt` :
```kotlin
package com.tmenard.planchecontact.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmenard.planchecontact.model.UiState

@Composable
fun ResultScreen(
    state: UiState,
    onOpen: (android.net.Uri) -> Unit,
    onPrint: () -> Unit,
    onShare: (android.net.Uri) -> Unit,
    onReset: () -> Unit
) {
    when (state) {
        is UiState.Generating -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Génération… ${state.done}/${state.total}")
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f
                            else state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth()
            )
        }
        is UiState.Success -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.CheckCircle, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(8.dp))
            Text("Planche contact créée", style = MaterialTheme.typography.titleLarge)
            Text(state.fileName, style = MaterialTheme.typography.bodyMedium)
            Text("Enregistrée dans Téléchargements/PlancheContact",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { onOpen(state.uri) },
                modifier = Modifier.fillMaxWidth()) { Text("Ouvrir") }
            OutlinedButton(onClick = onPrint,
                modifier = Modifier.fillMaxWidth()) { Text("Imprimer") }
            OutlinedButton(onClick = { onShare(state.uri) },
                modifier = Modifier.fillMaxWidth()) { Text("Partager") }
            Button(onClick = onReset,
                modifier = Modifier.fillMaxWidth()) { Text("Nouvelle planche") }
        }
        is UiState.Error -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Erreur : ${state.message}")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onReset) { Text("Recommencer") }
        }
        UiState.Idle -> {}
    }
}
```
- [ ] `MainActivity.kt` (final) :
```kotlin
package com.tmenard.planchecontact

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.Build
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmenard.planchecontact.export.openPdf
import com.tmenard.planchecontact.export.printPdf
import com.tmenard.planchecontact.export.sharePdf
import com.tmenard.planchecontact.model.AppScreen
import com.tmenard.planchecontact.model.ContactSheetViewModel
import com.tmenard.planchecontact.model.UiState
import com.tmenard.planchecontact.ui.ConfigScreen
import com.tmenard.planchecontact.ui.ResultScreen
import com.tmenard.planchecontact.ui.SelectionScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlancheTheme { PlancheApp() } }
    }
}

@Composable
fun PlancheTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun PlancheApp(vm: ContactSheetViewModel = viewModel()) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val spec by vm.spec.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (screen) {
        AppScreen.SELECTION -> SelectionScreen(
            photos = photos,
            onAdd = vm::addPhotos,
            onRemove = vm::removePhoto,
            onNext = vm::goToConfig
        )
        AppScreen.CONFIG -> ConfigScreen(
            spec = spec,
            photoCount = photos.size,
            setTitle = vm::setTitle,
            setLandscape = vm::setLandscape,
            setColumns = vm::setColumns,
            onBack = vm::backToSelection,
            onGenerate = vm::generate
        )
        AppScreen.RESULT -> ResultScreen(
            state = state,
            onOpen = { uri -> openPdf(context, uri) },
            onPrint = { (state as? UiState.Success)?.let { printPdf(context, it.bytes) } },
            onShare = { uri -> sharePdf(context, uri) },
            onReset = vm::reset
        )
    }
}
```
- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] `git commit -m "feat: écran résultat et câblage complet de l'app"`

### Tâche 10 — E2E sur émulateur Pixel_10_Pro (critère d'acceptation)

- [ ] Générer 50 photos de test sur Mac (~4000px, stress du pipeline) :
```bash
mkdir -p /tmp/photos-test
for i in $(seq 1 50); do
  screencapture -x /tmp/photos-test/tmp.png
  sips -s format jpeg -s formatOptions 90 --resampleWidth 4000 \
    /tmp/photos-test/tmp.png --out /tmp/photos-test/photo_$i.jpg >/dev/null
done
rm /tmp/photos-test/tmp.png
```
- [ ] Démarrer l'émulateur (arrière-plan) : `~/Library/Android/sdk/emulator/emulator -avd Pixel_10_Pro`
- [ ] `adb wait-for-device` puis attendre `adb shell getprop sys.boot_completed` = `1`
- [ ] Pousser les photos : `adb push /tmp/photos-test /sdcard/DCIM/` puis `adb reboot` (scan média au boot), re-attendre le boot
- [ ] `./gradlew installDebug` et lancer : `adb shell am start -n com.tmenard.planchecontact/.MainActivity`
- [ ] **Étape manuelle (utilisateur)** : « Choisir des photos » → sélectionner les 50 → « Continuer » → config par défaut → « Générer »
- [ ] Vérifier : `adb shell ls -l /sdcard/Download/PlancheContact/` → **taille < 5 Mo pour 50 photos**
- [ ] `adb pull` du PDF + `open` sur Mac → contrôle visuel dans Preview (grille, numéros, en-tête, Page X/Y)
- [ ] Tester « Imprimer » → la boîte d'impression Android s'ouvre (option « Enregistrer au format PDF »)
- [ ] Contingence si PDF > 5 Mo : `PRINT_DPI` à `200` dans `GridCalculator`, mettre à jour le test (99.056/72×200 = 275), relancer tests + regénérer + re-vérifier
- [ ] Corriger les éventuels problèmes (ex. rotation EXIF → `ExifInterface` dans `ThumbnailDecoder`), `./gradlew :app:testDebugUnitTest && ./gradlew assembleDebug`
- [ ] `git commit -m "test: e2e validée sur émulateur"`

### Tâche 11 — Finalisation

- [ ] `./gradlew :app:testDebugUnitTest && ./gradlew assembleDebug` → tout vert
- [ ] `git add -A && git commit -m "chore: finalisation v1"` si modifs pendantes
- [ ] Livraison : APK `app/build/outputs/apk/debug/app-debug.apk` — installation téléphone : USB + `./gradlew installDebug`
