package com.rodrigo.misprecios.data

import com.rodrigo.misprecios.network.PriceChangeGuard
import com.rodrigo.misprecios.network.PriceScraper
import com.rodrigo.misprecios.network.ScrapedProduct
import com.rodrigo.misprecios.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Repository {

    suspend fun preview(url: String, priceSelector: String? = null): ScrapedProduct =
        PriceScraper.fetch(url, priceSelector = priceSelector)

    suspend fun getAll(): List<Product> = withContext(Dispatchers.IO) { Database.getAllProducts() }

    suspend fun get(id: Long): Product? = withContext(Dispatchers.IO) { Database.getProduct(id) }

    suspend fun getHistory(id: Long): List<PriceHistoryEntry> = withContext(Dispatchers.IO) { Database.getHistory(id) }

    suspend fun addProduct(
        url: String,
        alias: String,
        scraped: ScrapedProduct,
        outOfStockKeyword: String? = null,
        priceSelector: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val product = Product(
            url = url.trim(),
            alias = alias.ifBlank { scraped.name },
            name = scraped.name,
            imageUrl = scraped.imageUrl,
            storeName = scraped.storeName,
            currentPrice = scraped.price,
            previousPrice = null,
            currencySymbol = scraped.currencySymbol,
            lastCheckedAt = now,
            inStock = scraped.inStock,
            outOfStockKeyword = outOfStockKeyword?.trim()?.ifBlank { null },
            priceSelector = priceSelector?.trim()?.ifBlank { null }
        )
        val id = Database.insertProduct(product)
        Database.insertHistory(PriceHistoryEntry(productId = id, price = scraped.price, checkedAt = now))
        id
    }

    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        Database.deleteProduct(product.id)
    }

    /**
     * Revisa todos los productos, actualiza los que cambiaron de precio o disponibilidad
     * y dispara notificaciones. Devuelve la cantidad de productos que cambiaron de precio.
     */
    suspend fun checkAllPrices(notifyOnlyOnDrop: Boolean): Int = withContext(Dispatchers.IO) {
        val products = Database.getAllProducts()
        var changedCount = 0

        for (product in products) {
            val scraped = runCatching {
                PriceScraper.fetch(product.url, product.outOfStockKeyword, product.priceSelector)
            }.getOrNull() ?: continue
            val now = System.currentTimeMillis()

            val priceChanged = scraped.price != product.currentPrice
            if (priceChanged &&
                PriceChangeGuard.isSuspicious(product.currentPrice, scraped.price) &&
                product.suspectPrice != scraped.price
            ) {
                // Salto muy brusco visto por primera vez: esperar a que la próxima revisión
                // lo confirme antes de aceptarlo y notificar (evita alertas por lecturas erróneas).
                Database.updateProduct(product.copy(suspectPrice = scraped.price, lastCheckedAt = now))
                continue
            }
            val backInStock = !product.inStock && scraped.inStock

            if (priceChanged || product.inStock != scraped.inStock) {
                val updated = product.copy(
                    previousPrice = if (priceChanged) product.currentPrice else product.previousPrice,
                    currentPrice = scraped.price,
                    name = scraped.name,
                    imageUrl = scraped.imageUrl ?: product.imageUrl,
                    currencySymbol = scraped.currencySymbol,
                    inStock = scraped.inStock,
                    lastCheckedAt = now,
                    suspectPrice = null
                )
                Database.updateProduct(updated)

                if (priceChanged) {
                    Database.insertHistory(PriceHistoryEntry(productId = product.id, price = scraped.price, checkedAt = now))
                    changedCount++
                }

                if (backInStock) {
                    // Volvió a tener stock: esto es más relevante que un simple cambio de precio,
                    // así que avisamos sin importar el ajuste de "solo avisar si baja".
                    NotificationHelper.showBackInStockNotification(
                        productName = updated.alias,
                        price = scraped.price,
                        currencySymbol = updated.currencySymbol,
                        productUrl = updated.url
                    )
                } else if (priceChanged) {
                    val isDrop = scraped.price < product.currentPrice
                    if (!notifyOnlyOnDrop || isDrop) {
                        NotificationHelper.showPriceChangeNotification(
                            productName = updated.alias,
                            oldPrice = product.currentPrice,
                            newPrice = scraped.price,
                            currencySymbol = updated.currencySymbol,
                            productUrl = updated.url
                        )
                    }
                }
            } else {
                Database.updateProduct(product.copy(lastCheckedAt = now, suspectPrice = null))
            }
        }
        changedCount
    }
}
