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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tmenard.planchecontact.model.PhotoItem

@OptIn(ExperimentalMaterial3Api::class)
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
