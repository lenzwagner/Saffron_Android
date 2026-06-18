package com.zephron.app.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun UpdateDialog(info: UpdateInfo, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update verfügbar") },
        text = { Text("Version ${info.versionName} ist verfügbar. Jetzt aktualisieren?") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Aktualisieren") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Später") }
        }
    )
}
