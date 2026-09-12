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
    Surface(Modifier.fillMaxSize()) {
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
}
