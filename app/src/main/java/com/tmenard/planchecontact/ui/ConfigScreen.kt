package com.tmenard.planchecontact.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tmenard.planchecontact.pdf.GridCalculator
import com.tmenard.planchecontact.pdf.SheetSpec

@OptIn(ExperimentalMaterial3Api::class)
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
                                top + row * (cellH + (GridCalculator.CAPTION_PT + gap) * scale)),
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
