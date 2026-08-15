package com.rodrigo.misprecios.notifications

import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.text.NumberFormat
import kotlin.math.abs

/**
 * Notificaciones nativas de Windows a través del ícono de la bandeja del sistema
 * (system tray). Si el sistema no soporta bandeja (raro en Windows), no rompe nada,
 * simplemente no se muestra el globo de aviso.
 */
object NotificationHelper {

    private var trayIcon: TrayIcon? = null

    fun init() {
        if (!SystemTray.isSupported()) return
        if (trayIcon != null) return

        val image: BufferedImage = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics()
            g.color = java.awt.Color(0x67, 0x50, 0xA4)
            g.fillOval(0, 0, 16, 16)
            g.dispose()
        }

        val icon = TrayIcon(image, "Mis Precios")
        icon.isImageAutoSize = true
        runCatching {
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
        }
    }

    fun showPriceChangeNotification(
        productName: String,
        oldPrice: Double,
        newPrice: Double,
        currencySymbol: String
    ) {
        val icon = trayIcon ?: return
        val isDrop = newPrice < oldPrice
        val format = NumberFormat.getNumberInstance()
        val diff = abs(newPrice - oldPrice)
        val title = if (isDrop) "💰 ¡Bajó de precio!" else "📈 Subió de precio"
        val body = "$productName: $currencySymbol${format.format(oldPrice)} → " +
            "$currencySymbol${format.format(newPrice)} (${if (isDrop) "-" else "+"}$currencySymbol${format.format(diff)})"

        icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
    }
}
