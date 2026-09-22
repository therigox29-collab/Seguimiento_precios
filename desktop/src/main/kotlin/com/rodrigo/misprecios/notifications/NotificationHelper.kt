package com.rodrigo.misprecios.notifications

import java.awt.Color
import java.awt.Desktop
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.net.URI
import java.text.NumberFormat
import java.util.Timer
import java.util.TimerTask
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent
import kotlin.math.abs

/**
 * Notificaciones nativas de Windows: sonido propio (con el beep del sistema como
 * respaldo si el archivo de audio no está disponible) y un ícono de bandeja que
 * parpadea en amarillo y se queda así hasta que se abre la ventana de la app,
 * como aviso de "hay algo para revisar".
 */
object NotificationHelper {

    private var trayIcon: TrayIcon? = null
    private var lastUrl: String? = null
    private var blinkTimer: Timer? = null
    private var showingAlert = false

    private val normalImage: BufferedImage by lazy { buildDot(Color(0x67, 0x50, 0xA4)) }
    private val alertImage: BufferedImage by lazy { buildDot(Color(0xFF, 0xC1, 0x07)) }

    private fun buildDot(color: Color): BufferedImage =
        BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics()
            g.color = color
            g.fillOval(0, 0, 16, 16)
            g.dispose()
        }

    fun init() {
        if (!SystemTray.isSupported()) return
        if (trayIcon != null) return

        val icon = TrayIcon(normalImage, "Mis Precios")
        icon.isImageAutoSize = true
        icon.addActionListener {
            clearAlert()
            lastUrl?.let { openUrl(it) }
        }
        runCatching {
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
        }
    }

    /** Hace parpadear el ícono unos segundos y lo deja fijo en amarillo hasta revisar. */
    private fun markNeedsReview() {
        val icon = trayIcon ?: return
        blinkTimer?.cancel()

        var toggles = 0
        val timer = Timer(true)
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                showingAlert = !showingAlert
                icon.image = if (showingAlert) alertImage else normalImage
                toggles++
                if (toggles >= 8) {
                    icon.image = alertImage
                    showingAlert = true
                    cancel()
                }
            }
        }, 0, 500)
        blinkTimer = timer
    }

    /** Vuelve el ícono a su color normal. Se llama al abrir/enfocar la ventana de la app. */
    fun clearAlert() {
        blinkTimer?.cancel()
        blinkTimer = null
        showingAlert = false
        trayIcon?.image = normalImage
    }

    private fun playChime() {
        val played = runCatching {
            val stream = NotificationHelper::class.java.getResourceAsStream("/price-alert.wav")
                ?: return@runCatching false
            val audioIn = AudioSystem.getAudioInputStream(BufferedInputStream(stream))
            val clip = AudioSystem.getClip()
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) {
                    clip.close()
                    runCatching { audioIn.close() }
                }
            }
            clip.open(audioIn)
            clip.start()
            true
        }.getOrDefault(false)

        if (!played) {
            runCatching { Toolkit.getDefaultToolkit().beep() }
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
        playChime()
        markNeedsReview()

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
        playChime()
        markNeedsReview()

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
