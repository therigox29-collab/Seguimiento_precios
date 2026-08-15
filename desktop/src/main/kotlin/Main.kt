import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.rodrigo.misprecios.data.Database
import com.rodrigo.misprecios.notifications.NotificationHelper
import com.rodrigo.misprecios.ui.App

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
            App()
        }
    }
}
