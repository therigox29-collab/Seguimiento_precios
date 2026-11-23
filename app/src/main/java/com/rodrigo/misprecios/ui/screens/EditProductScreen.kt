package com.rodrigo.misprecios.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rodrigo.misprecios.ui.AppViewModel

/**
 * Permite corregir los datos de un producto ya guardado (URL, alias, frase de "agotado" y
 * selector de precio) sin tener que borrarlo y volver a agregarlo. No vuelve a consultar la
 * página en el momento: los cambios se aplican en la próxima revisión (automática o con
 * "Revisar ahora" desde la pantalla principal).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductScreen(viewModel: AppViewModel, productId: Long, onDone: () -> Unit) {
    val product by viewModel.observeProduct(productId).collectAsState(initial = null)

    var initialized by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var outOfStockKeyword by remember { mutableStateOf("") }
    var priceSelector by remember { mutableStateOf("") }

    LaunchedEffect(product) {
        val current = product
        if (current != null && !initialized) {
            url = current.url
            alias = current.alias
            outOfStockKeyword = current.outOfStockKeyword ?: ""
            priceSelector = current.priceSelector ?: ""
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar producto") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL del producto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text("Alias") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = priceSelector,
                onValueChange = { priceSelector = it },
                label = { Text("Selector CSS del precio (opcional, avanzado)") },
                placeholder = { Text("Ej: .product-info .price-final") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = outOfStockKeyword,
                onValueChange = { outOfStockKeyword = it },
                label = { Text("Frase de \"agotado\" (opcional)") },
                placeholder = { Text("Ej: Fuera de stock") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                "Los cambios se aplican en la próxima revisión de este producto (automática " +
                    "o tocando \"Revisar ahora\" en la pantalla principal). El precio y el " +
                    "historial actuales no se modifican acá.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    viewModel.updateProductFields(productId, url, alias, outOfStockKeyword, priceSelector)
                    onDone()
                },
                enabled = initialized && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("✓ Guardar cambios")
            }
        }
    }
}
