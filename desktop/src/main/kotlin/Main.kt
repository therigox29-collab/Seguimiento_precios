import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.rodrigo.misprecios.data.Database
import com.rodrigo.misprecios.notifications.NotificationHelper
import com.rodrigo.misprecios.ui.App
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

fun main() {
    Database.init()
    NotificationHelper.init()

    application {
        val windowState = rememberWindowState(width = 420.dp, height = 780.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title = "Mis Precios",
            state = windowState
        ) {
            LaunchedEffect(Unit) {
                window.addWindowFocusListener(object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        NotificationHelper.clearAlert()
                    }
                    override fun windowLostFocus(e: WindowEvent?) {}
                })
            }
            App()
        }
    }
}
