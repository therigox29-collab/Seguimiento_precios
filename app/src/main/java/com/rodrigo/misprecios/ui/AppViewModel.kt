package com.rodrigo.misprecios.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigo.misprecios.data.AppSettings
import com.rodrigo.misprecios.data.PriceHistoryEntry
import com.rodrigo.misprecios.data.Product
import com.rodrigo.misprecios.data.ProductRepository
import com.rodrigo.misprecios.data.RefreshMode
import com.rodrigo.misprecios.data.SettingsRepository
import com.rodrigo.misprecios.network.ScrapedProduct
import com.rodrigo.misprecios.service.PriceCheckForegroundService
import com.rodrigo.misprecios.work.PriceCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val products: StateFlow<List<Product>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _previewState = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val previewState: StateFlow<PreviewState> = _previewState

    fun observeHistory(productId: Long) = repository.observeHistory(productId)
    fun observeProduct(productId: Long) = repository.observeById(productId)

    fun previewUrl(url: String) {
        _previewState.value = PreviewState.Loading
        viewModelScope.launch {
            _previewState.value = try {
                PreviewState.Success(repository.preview(url))
            } catch (e: Exception) {
                PreviewState.Error(e.message ?: "No pudimos leer esa página")
            }
        }
    }

    fun resetPreview() {
        _previewState.value = PreviewState.Idle
    }

    fun saveProduct(url: String, alias: String, scraped: ScrapedProduct) {
        viewModelScope.launch {
            repository.addProduct(url, alias, scraped)
            _previewState.value = PreviewState.Idle
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch { repository.deleteProduct(product) }
    }

    fun refreshNow() {
        viewModelScope.launch { repository.checkAllPrices() }
    }

    fun setRefreshMode(mode: RefreshMode) {
        viewModelScope.launch {
            settingsRepository.setRefreshMode(mode)
            applyRefreshMode(mode, settings.value.fastIntervalMinutes, settings.value.backgroundIntervalMinutes)
        }
    }

    fun setFastInterval(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setFastIntervalMinutes(minutes)
            if (settings.value.refreshMode == RefreshMode.FAST) {
                PriceCheckForegroundService.start(getApplication(), minutes)
            }
        }
    }

    fun setBackgroundInterval(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setBackgroundIntervalMinutes(minutes)
            if (settings.value.refreshMode == RefreshMode.BACKGROUND) {
                PriceCheckWorker.schedule(getApplication(), minutes)
            }
        }
    }

    fun setNotifyOnlyOnDrop(value: Boolean) {
        viewModelScope.launch { settingsRepository.setNotifyOnlyOnDrop(value) }
    }

    private fun applyRefreshMode(mode: RefreshMode, fastMinutes: Int, backgroundMinutes: Int) {
        val app = getApplication<Application>()
        when (mode) {
            RefreshMode.FAST -> {
                PriceCheckWorker.cancel(app)
                PriceCheckForegroundService.start(app, fastMinutes)
            }
            RefreshMode.BACKGROUND -> {
                PriceCheckForegroundService.stop(app)
                PriceCheckWorker.schedule(app, backgroundMinutes)
            }
        }
    }

    sealed class PreviewState {
        object Idle : PreviewState()
        object Loading : PreviewState()
        data class Success(val scraped: ScrapedProduct) : PreviewState()
        data class Error(val message: String) : PreviewState()
    }
}
