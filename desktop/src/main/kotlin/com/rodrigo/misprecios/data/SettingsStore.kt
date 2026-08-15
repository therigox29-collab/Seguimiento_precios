package com.rodrigo.misprecios.data

import java.util.prefs.Preferences

/**
 * Guarda los ajustes en el registro de preferencias del sistema operativo
 * (no hace falta ningún archivo de configuración manual).
 */
object SettingsStore {
    private val prefs = Preferences.userRoot().node("com/rodrigo/misprecios")

    var intervalMinutes: Int
        get() = prefs.getInt("interval_minutes", 1)
        set(value) = prefs.putInt("interval_minutes", value.coerceIn(1, 120))

    var notifyOnlyOnDrop: Boolean
        get() = prefs.getBoolean("notify_only_on_drop", true)
        set(value) = prefs.putBoolean("notify_only_on_drop", value)
}
