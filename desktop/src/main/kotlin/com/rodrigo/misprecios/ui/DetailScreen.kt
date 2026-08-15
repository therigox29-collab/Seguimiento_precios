package com.rodrigo.misprecios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rodrigo.misprecios.data.PriceHistoryEntry
import com.rodrigo.misprecios.data.Product
import com.rodrigo.misprecios.data.Repository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(productId: Long, onBack: () -> Unit) {
    var product by remember { mutableStateOf<Product?>(null) }
    var history by remember { mutableStateOf<List<PriceHistoryEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val format = NumberFormat.getNumberInstance()
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("es")) }

    LaunchedEffect(productId, refreshTrigger) {
        product = Repository.get(productId)
        history = Repository.getHistory(productId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    product?.let { p ->
                        IconButton(onClick = {
                            scope.launch {
                                Repository.deleteProduct(p)
                                requestRefresh()
                                onBack()
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val currentProduct = product ?: return@Scaffold

        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            NetworkImage(
                url = currentProduct.imageUrl,
                modifier = Modifier.fillMaxWidth().height(180.dp),
                cornerRadius = 18
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(currentProduct.name, fontWeight = FontWeight.SemiBold)

            Text(
                "${currentProduct.currencySymbol}${format.format(currentProduct.currentPrice)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )

            val previous = currentProduct.previousPrice
            if (previous != null && previous != currentProduct.currentPrice) {
                val isDrop = currentProduct.currentPrice < previous
                val diff = kotlin.math.abs(currentProduct.currentPrice - previous)
                val pct = if (previous != 0.0) (diff / previous) * 100 else 0.0
                Text(
                    "${if (isDrop) "▼ Bajó" else "▲ Subió"} ${currentProduct.currencySymbol}${format.format(diff)} " +
                        "(${String.format(Locale("es"), "%.0f", pct)}%)",
                    color = if (isDrop) SuccessGreen else ErrorRed,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Historial de precio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PriceHistoryChart(history = history)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column {
                history.reversed().take(15).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(dateFormat.format(Date(entry.checkedAt)), style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${currentProduct.currencySymbol}${format.format(entry.price)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
