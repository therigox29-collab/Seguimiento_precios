package com.rodrigo.misprecios.data

import android.content.Context
import com.rodrigo.misprecios.network.PriceChangeGuard
import com.rodrigo.misprecios.network.PriceScraper
import com.rodrigo.misprecios.network.ScrapedProduct
import com.rodrigo.misprecios.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class ProductRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).productDao()
    private val notificationHelper = NotificationHelper(context)
    private val settingsRepository = SettingsRepository(context)

    /** Resultado de importar una copia de seguridad: cuántos productos se agregaron y cuántos
     * ya existían (mismo URL) y por lo tanto se dejaron como estaban, para no duplicar. */
    data class BackupResult(val imported: Int, val skipped: Int)

    fun observeAll(): Flow<List<Product>> = dao.observeAll()

    fun observeById(id: Long): Flow<Product?> = dao.observeById(id)

    fun observeHistory(id: Long): Flow<List<PriceHistoryEntry>> = dao.observeHistory(id)

    /** Detecta los datos de un producto a partir de su URL, sin guardarlo todavía. */
    suspend fun preview(url: String, priceSelector: String? = null): ScrapedProduct =
        PriceScraper.fetch(url, priceSelector = priceSelector)

    suspend fun addProduct(
        url: String,
        alias: String,
        scraped: ScrapedProduct,
        outOfStockKeyword: String? = null,
        priceSelector: String? = null
    ): Long {
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
        val id = dao.insert(product)
        dao.insertHistory(PriceHistoryEntry(productId = id, price = scraped.price, checkedAt = now))
        return id
    }

    /**
     * Actualiza solo los datos editables de un producto ya guardado (URL, alias, frase de
     * "agotado" y selector de precio), sin tocar el precio actual ni el historial. Los cambios
     * se aplican recién en la próxima revisión (automática o con "Revisar ahora").
     */
    suspend fun updateProductFields(
        productId: Long,
        url: String,
        alias: String,
        outOfStockKeyword: String,
        priceSelector: String
    ) {
        val existing = dao.getById(productId) ?: return
        val updated = existing.copy(
            url = url.trim().ifBlank { existing.url },
            alias = alias.trim().ifBlank { existing.alias },
            outOfStockKeyword = outOfStockKeyword.trim().ifBlank { null },
            priceSelector = priceSelector.trim().ifBlank { null }
        )
        dao.update(updated)
    }

    suspend fun deleteProduct(product: Product) = dao.delete(product)

    /**
     * Arma un archivo de respaldo en formato JSON con todos los productos guardados y su
     * historial de precios completo, para que el usuario lo guarde donde quiera (Drive, el
     * propio celular, etc.) y pueda recuperarlo si alguna vez tiene que reinstalar la app.
     */
    suspend fun exportBackupJson(): String {
        val products = dao.getAll()
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
            for (h in dao.getHistoryOnce(p.id)) {
                val historyObj = JSONObject()
                historyObj.put("price", h.price)
                historyObj.put("checkedAt", h.checkedAt)
                historyArray.put(historyObj)
            }
            obj.put("history", historyArray)

            productsArray.put(obj)
        }
        root.put("products", productsArray)
        return root.toString(2)
    }

    /**
     * Carga productos desde un archivo de respaldo generado por [exportBackupJson]. Los
     * productos cuyo URL ya existe en la base se omiten (para no duplicar si el usuario
     * importa el mismo archivo más de una vez); el resto se agrega como nuevo, con su
     * historial de precios completo.
     */
    suspend fun importBackupJson(json: String): BackupResult {
        val root = JSONObject(json)
        val productsArray = root.optJSONArray("products") ?: JSONArray()
        var imported = 0
        var skipped = 0

        for (i in 0 until productsArray.length()) {
            val obj = productsArray.optJSONObject(i) ?: continue
            val url = obj.optString("url").ifBlank { continue }
            if (dao.getByUrl(url) != null) {
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
            val newId = dao.insert(product)

            val historyArray = obj.optJSONArray("history") ?: JSONArray()
            for (j in 0 until historyArray.length()) {
                val historyObj = historyArray.optJSONObject(j) ?: continue
                dao.insertHistory(
                    PriceHistoryEntry(
                        productId = newId,
                        price = historyObj.optDouble("price", 0.0),
                        checkedAt = historyObj.optLong("checkedAt", System.currentTimeMillis())
                    )
                )
            }
            imported++
        }
        return BackupResult(imported = imported, skipped = skipped)
    }

    /**
     * Revisa todos los productos guardados: consulta el precio actual, lo compara con el
     * último guardado y, si cambió (precio o disponibilidad), actualiza la base y dispara
     * una notificación. Devuelve la cantidad de productos cuyo precio cambió.
     *
     * Si el precio cambia de forma muy brusca (ver [PriceChangeGuard]) no se acepta en el
     * momento: se guarda como "sospechoso" y solo se confirma si la revisión siguiente
     * devuelve el mismo valor. Así una lectura equivocada no genera alertas falsas.
     */
    suspend fun checkAllPrices(): Int {
        val products = dao.getAll()
        val settings = settingsSnapshot()
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
                // Primera vez que vemos este salto: esperar confirmación.
                dao.update(product.copy(suspectPrice = scraped.price, lastCheckedAt = now))
                continue
            }
            val backInStock = !product.inStock && scraped.inStock
            val stockChanged = product.inStock != scraped.inStock

            if (priceChanged || stockChanged) {
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
                dao.update(product.copy(lastCheckedAt = now, suspectPrice = null))
            }
        }
        return changedCount
    }

    private suspend fun settingsSnapshot(): AppSettings = settingsRepository.settingsFlow.first()
}
