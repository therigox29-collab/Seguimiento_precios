package com.rodrigo.misprecios.data

import android.content.Context
import com.rodrigo.misprecios.network.PriceScraper
import com.rodrigo.misprecios.network.ScrapedProduct
import com.rodrigo.misprecios.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ProductRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).productDao()
    private val notificationHelper = NotificationHelper(context)
    private val settingsRepository = SettingsRepository(context)

    fun observeAll(): Flow<List<Product>> = dao.observeAll()

    fun observeById(id: Long): Flow<Product?> = dao.observeById(id)

    fun observeHistory(id: Long): Flow<List<PriceHistoryEntry>> = dao.observeHistory(id)

    /** Detecta los datos de un producto a partir de su URL, sin guardarlo todavía. */
    suspend fun preview(url: String): ScrapedProduct = PriceScraper.fetch(url)

    suspend fun addProduct(url: String, alias: String, scraped: ScrapedProduct): Long {
        val now = System.currentTimeMillis()
        val product = Product(
            url = url,
            alias = alias.ifBlank { scraped.name },
            name = scraped.name,
            imageUrl = scraped.imageUrl,
            storeName = scraped.storeName,
            currentPrice = scraped.price,
            previousPrice = null,
            currencySymbol = scraped.currencySymbol,
            lastCheckedAt = now,
            inStock = scraped.inStock
        )
        val id = dao.insert(product)
        dao.insertHistory(PriceHistoryEntry(productId = id, price = scraped.price, checkedAt = now))
        return id
    }

    suspend fun deleteProduct(product: Product) = dao.delete(product)

    /**
     * Revisa todos los productos guardados: consulta el precio actual, lo compara con el
     * último guardado y, si cambió (precio o disponibilidad), actualiza la base y dispara
     * una notificación. Devuelve la cantidad de productos cuyo precio cambió.
     */
    suspend fun checkAllPrices(): Int {
        val products = dao.getAll()
        val settings = settingsSnapshot()
        var changedCount = 0

        for (product in products) {
            val scraped = runCatching { PriceScraper.fetch(product.url) }.getOrNull() ?: continue
            val now = System.currentTimeMillis()

            val priceChanged = scraped.price != product.currentPrice
            val backInStock = !product.inStock && scraped.inStock
            val stockChanged = product.inStock != scraped.inStock

            if (priceChanged || stockChanged) {
                val updated = product.copy(
                    previousPrice = if (priceChanged) product.currentPrice else product.previousPrice,
                    currentPrice = scraped.price,
                    name = scraped.name,
                    imageUrl = scraped.imageUrl ?: product.imageUrl,
                    inStock = scraped.inStock,
                    lastCheckedAt = now
                )
                dao.update(updated)

                if (priceChanged) {
                    dao.insertHistory(PriceHistoryEntry(productId = product.id, price = scraped.price, checkedAt = now))
                    changedCount++
                }

                if (backInStock) {
                    // Volvió a tener stock: esto es más relevante que un simple cambio de precio,
                    // así que avisamos sin importar el ajuste de "solo avisar si baja".
                    notificationHelper.showBackInStockNotification(
                        productId = product.id,
                        productName = updated.alias,
                        price = scraped.price,
                        currencySymbol = updated.currencySymbol,
                        productUrl = updated.url
                    )
                } else if (priceChanged) {
                    val isDrop = scraped.price < product.currentPrice
                    val shouldNotify = !settings.notifyOnlyOnDrop || isDrop
                    if (shouldNotify) {
                        notificationHelper.showPriceChangeNotification(
                            productId = product.id,
                            productName = updated.alias,
                            oldPrice = product.currentPrice,
                            newPrice = scraped.price,
                            currencySymbol = updated.currencySymbol,
                            productUrl = updated.url
                        )
                    }
                }
            } else {
                dao.update(product.copy(lastCheckedAt = now))
            }
        }
        return changedCount
    }

    private suspend fun settingsSnapshot(): AppSettings = settingsRepository.settingsFlow.first()
}
