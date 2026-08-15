package com.rodrigo.misprecios

import android.app.Application
import com.rodrigo.misprecios.data.RefreshMode
import com.rodrigo.misprecios.data.SettingsRepository
import com.rodrigo.misprecios.work.PriceCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MisPreciosApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Al arrancar la app, si el modo configurado es "ahorro" nos aseguramos de que
        // el trabajo periódico de WorkManager esté programado con el intervalo actual.
        CoroutineScope(Dispatchers.IO).launch {
            val settings = SettingsRepository(applicationContext).settingsFlow.first()
            if (settings.refreshMode == RefreshMode.BACKGROUND) {
                PriceCheckWorker.schedule(applicationContext, settings.backgroundIntervalMinutes)
            }
        }
    }
}
