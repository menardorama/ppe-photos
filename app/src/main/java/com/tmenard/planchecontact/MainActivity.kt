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
        android.os.Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        android.os.Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
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
            setLandscape = vm::setLandscape,
            setFormat = vm::setFormat,
            onBack = vm::backToSelection,
            onGenerate = vm::generate
        )
        AppScreen.RESULT -> ResultScreen(
            state = state,
            onOpen = { uri -> openPdf(context, uri) },
            onPrint = { (state as? UiState.Success)?.let { printPdf(context, it.bytes) } },
            onShare = { uri -> sharePdf(context, uri) },
            onBackToConfig = vm::backToConfig,
            onReset = vm::reset
        )
    }
}
