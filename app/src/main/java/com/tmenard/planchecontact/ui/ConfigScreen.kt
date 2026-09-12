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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tmenard.planchecontact.pdf.GridCalculator
import com.tmenard.planchecontact.pdf.SheetSpec

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(
    spec: SheetSpec,
    photoCount: Int,
    setLandscape: (Boolean) -> Unit,
    setFormat: (Int) -> Unit,
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
            Text("Orientation", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !spec.landscape,
                    onClick = { setLandscape(false) }, label = { Text("Portrait") })
                FilterChip(selected = spec.landscape,
                    onClick = { setLandscape(true) }, label = { Text("Paysage") })
            }
            Text("Format des vignettes", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (4..10).forEach { f ->
                    val l = GridCalculator.computeLayout(
                        SheetSpec(landscape = spec.landscape, format = f), 1)
                    FilterChip(
                        selected = spec.format == f,
                        onClick = { setFormat(f) },
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("A$f")
                                Text(
                                    "(${(l.cellWidthPt / GridCalculator.MM_TO_PT).toInt()}×" +
                                        "${(l.cellHeightPt / GridCalculator.MM_TO_PT).toInt()} mm)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    )
                }
            }
            Canvas(
                Modifier.fillMaxWidth()
                    .aspectRatio(spec.pageWidthPt / spec.pageHeightPt)
                    .background(Color.White, MaterialTheme.shapes.small)
            ) {
                val scale = size.width / spec.pageWidthPt
                val gap = GridCalculator.GAP_MM * GridCalculator.MM_TO_PT * scale
                val gridW = layout.columns * layout.cellWidthPt * scale +
                    (layout.columns - 1) * gap
                val gridH = layout.rowsPerPage * layout.cellHeightPt * scale +
                    (layout.rowsPerPage - 1) * gap
                val startX = (size.width - gridW) / 2f
                val startY = (size.height - gridH) / 2f
                val cellW = layout.cellWidthPt * scale
                val cellH = layout.cellHeightPt * scale
                for (row in 0 until layout.rowsPerPage) {
                    for (col in 0 until layout.columns) {
                        drawRect(
                            Color(0xFFE0E0E0),
                            topLeft = Offset(startX + col * (cellW + gap),
                                startY + row * (cellH + gap)),
                            size = Size(cellW, cellH)
                        )
                        drawRect(
                            Color.White,
                            topLeft = Offset(startX + col * (cellW + gap),
                                startY + row * (cellH + gap)),
                            size = Size(cellW, cellH),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }
            Text(
                "$photoCount photo(s) • ${layout.photosPerPage} photos/page • " +
                "${layout.pageCount} page(s)",
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
