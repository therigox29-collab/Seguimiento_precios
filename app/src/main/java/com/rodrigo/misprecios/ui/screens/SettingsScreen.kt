package com.rodrigo.misprecios.data

import com.rodrigo.misprecios.network.PriceChangeGuard
import com.rodrigo.misprecios.network.PriceScraper
import com.rodrigo.misprecios.network.ScrapedProduct
import com.rodrigo.misprecios.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object Repository {

    /** Resultado de importar una copia de seguridad: cuántos productos se agregaron y cuántos
     * ya existían (mismo URL) y por lo tanto se dejaron como estaban, para no duplicar. */
    data class BackupResult(val imported: Int, val skipped: Int)

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

    /**
     * Actualiza solo los datos editables de un producto ya guardado (URL, alias, frase de
     * "agotado" y selector de precio), sin tocar el precio actual ni el historial. Los cambios
     * se aplican recién en la próxima revisión.
     */
    suspend fun updateProductFields(
        productId: Long,
        url: String,
        alias: String,
        outOfStockKeyword: String,
        priceSelector: String
    ): Unit = withContext(Dispatchers.IO) {
        val existing = Database.getProduct(productId) ?: return@withContext
        val updated = existing.copy(
            url = url.trim().ifBlank { existing.url },
            alias = alias.trim().ifBlank { existing.alias },
            outOfStockKeyword = outOfStockKeyword.trim().ifBlank { null },
            priceSelector = priceSelector.trim().ifBlank { null }
        )
        Database.updateProduct(updated)
    }

    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        Database.deleteProduct(product.id)
    }

    /**
     * Arma un archivo de respaldo en formato JSON con todos los productos guardados y su
     * historial de precios completo, para guardarlo donde se quiera y poder recuperarlo si
     * hace falta.
     */
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val products = Database.getAllProducts()
        val root = JSONObject()
        root.put("app", "MisPrecios")
        root.put("backupVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val productsArray = JSONArray()
        for (p in products) {
            val obj = JSONObject()
            obj.put("url", p.url)
            obj.put("alias", p.alias)
            obj.put("name", p.name)
            obj.put("imageUrl", p.imageUrl)
            obj.put("storeName", p.storeName)
            obj.put("currentPrice", p.currentPrice)
            obj.put("previousPrice", p.previousPrice)
            obj.put("currencySymbol", p.currencySymbol)
            obj.put("lastCheckedAt", p.lastCheckedAt)
            obj.put("inStock", p.inStock)
            obj.put("outOfStockKeyword", p.outOfStockKeyword)
            obj.put("priceSelector", p.priceSelector)

            val historyArray = JSONArray()
            for (h in Database.getHistory(p.id)) {
                val historyObj = JSONObject()
                historyObj.put("price", h.price)
                historyObj.put("checkedAt", h.checkedAt)
                historyArray.put(historyObj)
            }
            obj.put("history", historyArray)

            productsArray.put(obj)
        }
        root.put("products", productsArray)
        root.toString(2)
    }

    /**
     * Carga productos desde un archivo de respaldo generado por [exportBackupJson]. Los
     * productos cuyo URL ya existe se omiten (para no duplicar); el resto se agrega como
     * nuevo, con su historial de precios completo.
     */
    suspend fun importBackupJson(json: String): BackupResult = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        val productsArray = root.optJSONArray("products") ?: JSONArray()
        var imported = 0
        var skipped = 0
        val existingUrls = Database.getAllProducts().map { it.url }.toMutableSet()

        for (i in 0 until productsArray.length()) {
            val obj = productsArray.optJSONObject(i) ?: continue
            val url = obj.optString("url").ifBlank { continue }
            if (url in existingUrls) {
                skipped++
                continue
            }

            val product = Product(
                url = url,
                alias = obj.optString("alias", url),
                name = obj.optString("name", url),
                imageUrl = if (obj.isNull("imageUrl")) null else obj.optString("imageUrl").ifBlank { null },
                storeName = obj.optString("storeName", "Tienda"),
                currentPrice = obj.optDouble("currentPrice", 0.0),
                previousPrice = if (obj.isNull("previousPrice")) null else obj.optDouble("previousPrice"),
                currencySymbol = obj.optString("currencySymbol", "$"),
                lastCheckedAt = obj.optLong("lastCheckedAt", System.currentTimeMillis()),
                inStock = obj.optBoolean("inStock", true),
                outOfStockKeyword = if (obj.isNull("outOfStockKeyword")) null else obj.optString("outOfStockKeyword").ifBlank { null },
                priceSelector = if (obj.isNull("priceSelector")) null else obj.optString("priceSelector").ifBlank { null }
            )
            val newId = Database.insertProduct(product)
            existingUrls.add(url)

            val historyArray = obj.optJSONArray("history") ?: JSONArray()
            for (j in 0 until historyArray.length()) {
                val historyObj = historyArray.optJSONObject(j) ?: continue
                Database.insertHistory(
                    PriceHistoryEntry(
                        productId = newId,
                        price = historyObj.optDouble("price", 0.0),
                        checkedAt = historyObj.optLong("checkedAt", System.currentTimeMillis())
                    )
                )
            }
            imported++
        }
        BackupResult(imported = imported, skipped = skipped)
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
