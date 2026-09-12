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

    fun setLandscape(b: Boolean) { _spec.value = _spec.value.copy(landscape = b) }
    fun setFormat(f: Int) { _spec.value = _spec.value.copy(format = f.coerceIn(4, 10)) }

    fun goToConfig() { if (_photos.value.isNotEmpty()) _screen.value = AppScreen.CONFIG }
    fun backToSelection() { _screen.value = AppScreen.SELECTION }
    fun backToConfig() { _screen.value = AppScreen.CONFIG }

    fun reset() {
        _photos.value = emptyList()
        _spec.value = SheetSpec()
        _state.value = UiState.Idle
        _screen.value = AppScreen.SELECTION
    }

    fun generate() {
        val photos = _photos.value
        if (photos.isEmpty()) return
        if (_state.value is UiState.Generating) return
        viewModelScope.launch {
            _state.value = UiState.Generating(0, photos.size)
            _screen.value = AppScreen.RESULT
            try {
                val (uri, name, bytes) = withContext(Dispatchers.IO) {
                    val spec = _spec.value
                    val layout = GridCalculator.computeLayout(spec, photos.size)
                    val targetPx = GridCalculator.thumbnailTargetPx(layout)
                    val bytes = ContactSheetPdfWriter(spec, layout).write(
                        photoCount = photos.size,
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = UiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }
}
