package com.rodrigo.misprecios.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rodrigo.misprecios.data.Repository
import com.rodrigo.misprecios.data.SettingsStore
import kotlinx.coroutines.delay

sealed class Screen {
    object Home : Screen()
    object Add : Screen()
    data class Detail(val productId: Long) : Screen()
    object Settings : Screen()
}

/** Se incrementa cada vez que hay que refrescar las listas desde la base de datos. */
var refreshTrigger by mutableStateOf(0)

fun requestRefresh() {
    refreshTrigger++
}

@Composable
fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    // Bucle de fondo: mientras la app esté abierta, revisa los precios cada
    // X minutos (configurable en Ajustes, 1 min por defecto) y refresca la UI.
    LaunchedEffect(Unit) {
        while (true) {
            delay(SettingsStore.intervalMinutes * 60_000L)
            runCatching { Repository.checkAllPrices(SettingsStore.notifyOnlyOnDrop) }
            requestRefresh()
        }
    }

    MisPreciosTheme {
        when (val current = screen) {
            is Screen.Home -> HomeScreen(
                onAddProduct = { screen = Screen.Add },
                onOpenSettings = { screen = Screen.Settings },
                onOpenProduct = { id -> screen = Screen.Detail(id) }
            )
            is Screen.Add -> AddProductScreen(
                onDone = { screen = Screen.Home }
            )
            is Screen.Detail -> DetailScreen(
                productId = current.productId,
                onBack = { screen = Screen.Home }
            )
            is Screen.Settings -> SettingsScreen(
                onBack = { screen = Screen.Home }
            )
        }
    }
}
