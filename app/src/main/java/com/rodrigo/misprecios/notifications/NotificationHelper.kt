package com.rodrigo.misprecios.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rodrigo.misprecios.R
import java.text.NumberFormat
import kotlin.math.abs

class NotificationHelper(private val context: Context) {

    companion object {
        const val PRICE_CHANNEL_ID = "price_changes"
        const val SERVICE_CHANNEL_ID = "price_check_service"
        const val SERVICE_NOTIFICATION_ID = 1001
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val priceChannel = NotificationChannel(
                PRICE_CHANNEL_ID,
                "Cambios de precio",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos cuando un producto que seguís cambia de precio"
            }

            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Seguimiento activo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación fija mientras el modo rápido está revisando precios"
            }

            manager?.createNotificationChannel(priceChannel)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    fun showPriceChangeNotification(
        productId: Long,
        productName: String,
        oldPrice: Double,
        newPrice: Double,
        currencySymbol: String
    ) {
        val isDrop = newPrice < oldPrice
        val format = NumberFormat.getNumberInstance()
        val diff = abs(newPrice - oldPrice)
        val title = if (isDrop) "💰 ¡Bajó de precio!" else "📈 Subió de precio"
        val body = "$productName: $currencySymbol${format.format(oldPrice)} → " +
            "$currencySymbol${format.format(newPrice)} (${if (isDrop) "-" else "+"}$currencySymbol${format.format(diff)})"

        val notification = NotificationCompat.Builder(context, PRICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(context).notify(productId.toInt(), notification)
        }
    }

    fun buildServiceNotification(intervalMinutes: Int): android.app.Notification {
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mis Precios activo")
            .setContentText("Revisando precios cada $intervalMinutes min")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
