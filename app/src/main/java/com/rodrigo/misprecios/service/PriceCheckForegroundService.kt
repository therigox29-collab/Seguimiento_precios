package com.rodrigo.misprecios.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import com.rodrigo.misprecios.data.ProductRepository
import com.rodrigo.misprecios.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modo rápido: mientras este servicio está vivo, revisa los precios cada
 * [EXTRA_INTERVAL_MINUTES] minutos (mínimo 1). Requiere notificación fija por
 * requisito de Android para foreground services.
 */
class PriceCheckForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var loopJob: Job? = null
    private lateinit var repository: ProductRepository
    private lateinit var notificationHelper: NotificationHelper
    private var intervalMinutes: Int = 5

    override fun onCreate() {
        super.onCreate()
        repository = ProductRepository(applicationContext)
        notificationHelper = NotificationHelper(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intervalMinutes = intent?.getIntExtra(EXTRA_INTERVAL_MINUTES, 5) ?: 5

        val notification = notificationHelper.buildServiceNotification(intervalMinutes)
        ServiceCompat.startForeground(
            this,
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        // Si ya había un ciclo corriendo (por ejemplo, el usuario cambió el intervalo),
        // lo cancelamos antes de arrancar uno nuevo para no duplicar chequeos.
        loopJob?.cancel()
        loopJob = scope.launch {
            while (true) {
                runCatching { repository.checkAllPrices() }
                delay(intervalMinutes * 60_000L)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_INTERVAL_MINUTES = "interval_minutes"

        fun start(context: Context, intervalMinutes: Int) {
            val intent = Intent(context, PriceCheckForegroundService::class.java)
                .putExtra(EXTRA_INTERVAL_MINUTES, intervalMinutes)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PriceCheckForegroundService::class.java))
        }
    }
}
