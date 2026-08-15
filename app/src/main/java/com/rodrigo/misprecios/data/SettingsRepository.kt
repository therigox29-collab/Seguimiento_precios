package com.rodrigo.misprecios.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "misprecios_settings")

/**
 * Modo de actualización de la app.
 * FAST = servicio en primer plano, intervalo configurable en minutos (mínimo 1).
 * BACKGROUND = WorkManager periódico, intervalo mínimo real de Android es 15 minutos.
 */
enum class RefreshMode { FAST, BACKGROUND }

data class AppSettings(
    val refreshMode: RefreshMode = RefreshMode.BACKGROUND,
    val fastIntervalMinutes: Int = 5,
    val backgroundIntervalMinutes: Int = 15,
    val notifyOnlyOnDrop: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val REFRESH_MODE = intPreferencesKey("refresh_mode")
        val FAST_INTERVAL = intPreferencesKey("fast_interval_minutes")
        val BACKGROUND_INTERVAL = intPreferencesKey("background_interval_minutes")
        val NOTIFY_ONLY_ON_DROP = booleanPreferencesKey("notify_only_on_drop")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            refreshMode = if (prefs[Keys.REFRESH_MODE] == 1) RefreshMode.FAST else RefreshMode.BACKGROUND,
            fastIntervalMinutes = prefs[Keys.FAST_INTERVAL] ?: 5,
            backgroundIntervalMinutes = prefs[Keys.BACKGROUND_INTERVAL] ?: 15,
            notifyOnlyOnDrop = prefs[Keys.NOTIFY_ONLY_ON_DROP] ?: true
        )
    }

    suspend fun setRefreshMode(mode: RefreshMode) {
        context.dataStore.edit { it[Keys.REFRESH_MODE] = if (mode == RefreshMode.FAST) 1 else 0 }
    }

    suspend fun setFastIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.FAST_INTERVAL] = minutes.coerceIn(1, 14) }
    }

    suspend fun setBackgroundIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.BACKGROUND_INTERVAL] = minutes.coerceIn(15, 180) }
    }

    suspend fun setNotifyOnlyOnDrop(value: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ONLY_ON_DROP] = value }
    }
}
