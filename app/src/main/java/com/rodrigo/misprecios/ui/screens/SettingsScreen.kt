package com.rodrigo.misprecios.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rodrigo.misprecios.data.RefreshMode
import com.rodrigo.misprecios.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()

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
        }
    }
}
