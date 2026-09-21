package com.rodrigo.misprecios.notifications

import java.awt.Desktop
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.net.URI
import java.text.NumberFormat
import kotlin.math.abs

/**
 * Notificaciones nativas de Windows a través del ícono de la bandeja del sistema
 * (system tray), más un sonido de sistema que suena siempre. El globo visual
 * depende de que Windows decida mostrarlo (el "Enfoque asistido" o los permisos
 * de notificación pueden bloquearlo sin avisar), así que el sonido no depende
 * de eso: usa el beep del sistema directamente.
 */
object NotificationHelper {

    private var trayIcon: TrayIcon? = null

    /** Guarda el link del último cambio de precio para poder abrirlo al hacer clic en el ícono. */
    private var lastUrl: String? = null

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
        icon.addActionListener {
            lastUrl?.let { openUrl(it) }
        }
        runCatching {
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
        }
    }

    fun showPriceChangeNotification(
        productName: String,
        oldPrice: Double,
        newPrice: Double,
        currencySymbol: String,
        productUrl: String
    ) {
        lastUrl = productUrl
        runCatching { Toolkit.getDefaultToolkit().beep() }

        val icon = trayIcon ?: return
        val isDrop = newPrice < oldPrice
        val format = NumberFormat.getNumberInstance()
        val diff = abs(newPrice - oldPrice)
        val title = if (isDrop) "💰 ¡Bajó de precio!" else "📈 Subió de precio"
        val body = "$productName: $currencySymbol${format.format(oldPrice)} → " +
            "$currencySymbol${format.format(newPrice)} (${if (isDrop) "-" else "+"}$currencySymbol${format.format(diff)})\n" +
            "(Doble clic en el ícono de la bandeja para abrir la tienda)"

        runCatching { icon.displayMessage(title, body, TrayIcon.MessageType.INFO) }
    }

    fun showBackInStockNotification(
        productName: String,
        price: Double,
        currencySymbol: String,
        productUrl: String
    ) {
        lastUrl = productUrl
        runCatching { Toolkit.getDefaultToolkit().beep() }

        val icon = trayIcon ?: return
        val format = NumberFormat.getNumberInstance()
        val title = "✅ ¡Volvió a tener stock!"
        val body = "$productName ya está disponible: $currencySymbol${format.format(price)}\n" +
            "(Doble clic en el ícono de la bandeja para abrir la tienda)"

        runCatching { icon.displayMessage(title, body, TrayIcon.MessageType.INFO) }
    }

    fun openUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }
}
