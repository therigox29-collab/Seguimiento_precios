package com.rodrigo.misprecios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rodrigo.misprecios.data.Repository
import com.rodrigo.misprecios.data.SettingsStore
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var interval by remember { mutableStateOf(SettingsStore.intervalMinutes) }
    var notifyOnlyOnDrop by remember { mutableStateOf(SettingsStore.notifyOnlyOnDrop) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupMessageIsError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("⏱️ Frecuencia de revisión", fontWeight = FontWeight.Bold)
                    Text(
                        "En la computadora no hay límites de batería como en el celular, así " +
                            "que podés dejarlo revisando cada 1 minuto sin problema.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    Text("Cada $interval min", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = interval.toFloat(),
                        onValueChange = {
                            interval = it.toInt()
                            SettingsStore.intervalMinutes = interval
                        },
                        valueRange = 1f..60f,
                        steps = 58
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🔔 Notificaciones", fontWeight = FontWeight.Bold)
                    Text(
                        "Avisar solo cuando el precio baja, o avisar con cualquier cambio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Solo bajadas de precio", fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = notifyOnlyOnDrop,
                            onCheckedChange = {
                                notifyOnlyOnDrop = it
                                SettingsStore.notifyOnlyOnDrop = it
                            }
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("💾 Copia de seguridad", fontWeight = FontWeight.Bold)
                    Text(
                        "Guardá un archivo con todos tus productos y su historial de precios, " +
                            "para recuperarlos si hace falta (por ejemplo, en otra computadora).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                backupMessage = null
                                val chooser = JFileChooser().apply {
                                    dialogTitle = "Guardar copia de seguridad"
                                    fileFilter = FileNameExtensionFilter("Archivo JSON", "json")
                                    val name = "mis-precios-backup-" +
                                        SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()) +
                                        ".json"
                                    selectedFile = File(name)
                                }
                                val result = chooser.showSaveDialog(null)
                                if (result == JFileChooser.APPROVE_OPTION) {
                                    var file = chooser.selectedFile
                                    if (!file.name.endsWith(".json", ignoreCase = true)) {
                                        file = File(file.parentFile, file.name + ".json")
                                    }
                                    scope.launch {
                                        val ok = runCatching {
                                            file.writeText(Repository.exportBackupJson())
                                        }.isSuccess
                                        backupMessageIsError = !ok
                                        backupMessage = if (ok) "✅ Copia guardada en ${file.name}." else "❌ No pudimos guardar el archivo."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Exportar")
                        }
                        Button(
                            onClick = {
                                backupMessage = null
                                val chooser = JFileChooser().apply {
                                    dialogTitle = "Importar copia de seguridad"
                                    fileFilter = FileNameExtensionFilter("Archivo JSON", "json")
                                }
                                val result = chooser.showOpenDialog(null)
                                if (result == JFileChooser.APPROVE_OPTION) {
                                    val file = chooser.selectedFile
                                    scope.launch {
                                        val json = runCatching { file.readText() }.getOrNull()
                                        if (json == null) {
                                            backupMessageIsError = true
                                            backupMessage = "❌ No pudimos leer ese archivo."
                                        } else {
                                            val importResult = runCatching { Repository.importBackupJson(json) }.getOrNull()
                                            if (importResult == null) {
                                                backupMessageIsError = true
                                                backupMessage = "❌ Ese archivo no tiene el formato esperado."
                                            } else {
                                                backupMessageIsError = false
                                                backupMessage = "✅ Se importaron ${importResult.imported} producto(s)" +
                                                    if (importResult.skipped > 0) {
                                                        " (${importResult.skipped} ya existían y se dejaron igual)."
                                                    } else {
                                                        "."
                                                    }
                                                requestRefresh()
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Importar")
                        }
                    }
                    backupMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (backupMessageIsError) ErrorRed else SuccessGreen,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
