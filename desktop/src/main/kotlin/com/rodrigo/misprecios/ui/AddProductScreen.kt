package com.rodrigo.misprecios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.rodrigo.misprecios.network.ScrapedProduct
import kotlinx.coroutines.launch
import java.text.NumberFormat

private sealed class PreviewState {
    object Idle : PreviewState()
    object Loading : PreviewState()
    data class Success(val scraped: ScrapedProduct) : PreviewState()
    data class Error(val message: String) : PreviewState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(onDone: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var previewState by remember { mutableStateOf<PreviewState>(PreviewState.Idle) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo producto") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL del producto") },
                placeholder = { Text("https://www.tienda.com/producto/...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    previewState = PreviewState.Loading
                    scope.launch {
                        previewState = try {
                            PreviewState.Success(Repository.preview(url))
                        } catch (e: Exception) {
                            PreviewState.Error(e.message ?: "No pudimos leer esa página")
                        }
                    }
                },
                enabled = url.isNotBlank() && previewState !is PreviewState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔍 Detectar precio")
            }

            when (val state = previewState) {
                is PreviewState.Loading -> {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator()
                    }
                }
                is PreviewState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                is PreviewState.Success -> {
                    val scraped = state.scraped
                    val format = NumberFormat.getNumberInstance()

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NetworkImage(url = scraped.imageUrl, modifier = Modifier.size(56.dp))
                            Column {
                                Text(scraped.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text(
                                    "${scraped.currencySymbol}${format.format(scraped.price)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text("Alias (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                Repository.addProduct(url, alias, scraped)
                                requestRefresh()
                                onDone()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✓ Guardar y empezar a seguir")
                    }
                }
                PreviewState.Idle -> {}
            }
        }
    }
}
