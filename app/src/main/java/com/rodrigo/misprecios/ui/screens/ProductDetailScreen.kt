package com.rodrigo.misprecios.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rodrigo.misprecios.ui.AppViewModel
import com.rodrigo.misprecios.ui.components.PriceHistoryChart
import com.rodrigo.misprecios.ui.theme.ErrorRed
import com.rodrigo.misprecios.ui.theme.SuccessGreen
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(viewModel: AppViewModel, productId: Long, onBack: () -> Unit) {
    val product by viewModel.observeProduct(productId).collectAsState(initial = null)
    val history by viewModel.observeHistory(productId).collectAsState(initial = emptyList())
    val format = NumberFormat.getNumberInstance()
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("es")) }
    val context = LocalContext.current

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
                            viewModel.deleteProduct(p)
                            onBack()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val currentProduct = product
        if (currentProduct == null) {
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            AsyncImage(
                model = currentProduct.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(18.dp))
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))

            Text(currentProduct.name, fontWeight = FontWeight.SemiBold)

            Text(
                "${currentProduct.currencySymbol}${format.format(currentProduct.currentPrice)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Button(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(currentProduct.url))
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Abrir en la tienda ↗")
            }

            val previous = currentProduct.previousPrice
            if (previous != null && previous != currentProduct.currentPrice) {
                val isDrop = currentProduct.currentPrice < previous
                val diff = Math.abs(currentProduct.currentPrice - previous)
                val pct = if (previous != 0.0) (diff / previous) * 100 else 0.0
                Text(
                    "${if (isDrop) "▼ Bajó" else "▲ Subió"} ${currentProduct.currencySymbol}${format.format(diff)} " +
                        "(${String.format(Locale("es"), "%.0f", pct)}%)",
                    color = if (isDrop) SuccessGreen else ErrorRed,
                    fontWeight = FontWeight.SemiBold
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(14.dp))

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

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(14.dp))

            Column {
                history.reversed().take(10).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            dateFormat.format(Date(entry.checkedAt)),
                            style = MaterialTheme.typography.bodySmall
                        )
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
