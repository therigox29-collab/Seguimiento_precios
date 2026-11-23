package com.rodrigo.misprecios.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rodrigo.misprecios.data.RefreshMode
import com.rodrigo.misprecios.ui.AppViewModel
import com.rodrigo.misprecios.ui.theme.ErrorRed
import com.rodrigo.misprecios.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupMessageIsError by remember { mutableStateOf(false) }

    // Cuando el usuario vuelve de la pantalla de Android donde acepta (o no) la excepción
    // de batería, refrescamos el estado para mostrar el cartel correcto sin que tenga que
    // salir y volver a entrar a Ajustes.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportBackup { json ->
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            backupMessageIsError = !ok
            backupMessage = if (ok) "✅ Copia guardada." else "❌ No pudimos guardar el archivo."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        if (json == null) {
            backupMessageIsError = true
            backupMessage = "❌ No pudimos leer ese archivo."
        } else {
            viewModel.importBackup(json) { result ->
                backupMessageIsError = false
                backupMessage = "✅ Se importaron ${result.imported} producto(s)" +
                    if (result.skipped > 0) " (${result.skipped} ya existían y se dejaron igual)." else "."
            }
        }
    }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Modo rápido
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("⚡ Modo rápido", fontWeight = FontWeight.Bold)
                    Text(
                        "Revisa precios con más frecuencia. Muestra una notificación fija " +
                            "mientras está activo. Mayor consumo de batería.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Activado", fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = settings.refreshMode == RefreshMode.FAST,
                            onCheckedChange = { checked ->
                                viewModel.setRefreshMode(if (checked) RefreshMode.FAST else RefreshMode.BACKGROUND)
                            }
                        )
                    }
                    if (settings.refreshMode == RefreshMode.FAST) {
                        Text(
                            "Intervalo: cada ${settings.fastIntervalMinutes} min",
                            modifier = Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = settings.fastIntervalMinutes.toFloat(),
                            onValueChange = { viewModel.setFastInterval(it.toInt()) },
                            valueRange = 1f..14f,
                            steps = 12
                        )
                        Text(
                            "Recomendado: 5 min. Podés bajar a 1 min para probar, pero " +
                                "consume mucha más batería.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Modo ahorro
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🔋 Modo ahorro", fontWeight = FontWeight.Bold)
                    Text(
                        "Revisa precios en segundo plano aunque la app esté cerrada, con " +
                            "un intervalo mínimo de 15 min (límite del sistema Android). " +
                            "Mucho más eficiente en batería.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Activado", fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = settings.refreshMode == RefreshMode.BACKGROUND,
                            onCheckedChange = { checked ->
                                viewModel.setRefreshMode(if (checked) RefreshMode.BACKGROUND else RefreshMode.FAST)
                            }
                        )
                    }
                    if (settings.refreshMode == RefreshMode.BACKGROUND) {
                        Text(
                            "Intervalo: cada ${settings.backgroundIntervalMinutes} min",
                            modifier = Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = settings.backgroundIntervalMinutes.toFloat(),
                            onValueChange = { viewModel.setBackgroundInterval(it.toInt()) },
                            valueRange = 15f..120f,
                            steps = 6
                        )
                    }
                }
            }

            // Optimización de batería
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🔋 Optimización de batería", fontWeight = FontWeight.Bold)
                    Text(
                        "Muchos celulares (Xiaomi, Samsung, Huawei y otros) frenan las apps en " +
                            "segundo plano para ahorrar batería, y eso puede hacer que el chequeo " +
                            "de precios deje de correr sin avisarte. Este botón le pide a Android " +
                            "que no restrinja Mis Precios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    if (batteryExempt) {
                        Text("✅ Ya está permitido", fontWeight = FontWeight.SemiBold, color = SuccessGreen)
                    } else {
                        Button(
                            onClick = { requestIgnoreBatteryOptimizations(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Permitir que corra en segundo plano")
                        }
                    }
                }
            }

            // Notificaciones
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
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Solo bajadas de precio", fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = settings.notifyOnlyOnDrop,
                            onCheckedChange = { viewModel.setNotifyOnlyOnDrop(it) }
                        )
                    }
                }
            }

            // Copia de seguridad
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("💾 Copia de seguridad", fontWeight = FontWeight.Bold)
                    Text(
                        "Guardá un archivo con todos tus productos y su historial de precios. " +
                            "Si alguna vez tenés que desinstalar la app (por ejemplo al actualizar " +
                            "una nueva versión instalada manualmente), podés usar este archivo para " +
                            "recuperar todo sin cargar nada de nuevo.",
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
                                val name = "mis-precios-backup-" +
                                    SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()) +
                                    ".json"
                                exportLauncher.launch(name)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Exportar")
                        }
                        Button(
                            onClick = {
                                backupMessage = null
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
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

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    runCatching { context.startActivity(intent) }
}
