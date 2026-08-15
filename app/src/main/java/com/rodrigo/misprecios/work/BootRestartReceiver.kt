package com.rodrigo.misprecios.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rodrigo.misprecios.data.RefreshMode
import com.rodrigo.misprecios.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Vuelve a programar el chequeo periódico en modo ahorro después de que el teléfono
 * reinicia (WorkManager persiste su cola, pero esto evita casos raros de reinstalación
 * de la app o de restauración). El modo rápido (foreground service) no se reinicia solo:
 * el usuario debe volver a abrir la app si lo tenía activo, por diseño de Android.
 */
class BootRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val settingsRepository = SettingsRepository(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.refreshMode == RefreshMode.BACKGROUND) {
                PriceCheckWorker.schedule(appContext, settings.backgroundIntervalMinutes)
            }
        }
    }
}
