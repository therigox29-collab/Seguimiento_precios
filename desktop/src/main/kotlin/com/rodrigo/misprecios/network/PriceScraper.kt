package com.rodrigo.misprecios.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Misma lógica que la versión Android: intenta JSON-LD, después Open Graph, después
 * microdata, y como último recurso un patrón de moneda en el texto. Para la disponibilidad,
 * además del dato estructurado, se revisa el texto visible de la página (y, si el producto
 * tiene una frase personalizada configurada, esa frase manda por encima de todo).
 */
object PriceScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Safari/537.36"

    suspend fun fetch(url: String, outOfStockKeyword: String? = null): ScrapedProduct = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .build()

        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ScrapeException("La tienda respondió con error ${response.code}")
            }
            response.body?.string() ?: throw ScrapeException("Respuesta vacía del sitio")
        }

        val doc = Jsoup.parse(html, url)
        val storeName = URI(url).host?.removePrefix("www.") ?: "Tienda"

        val base = fromJsonLd(doc, storeName)
            ?: fromOpenGraph(doc, storeName)
            ?: fromMicrodata(doc, storeName)
            ?: fromRegexFallback(doc, storeName)
            ?: throw ScrapeException(
                "No pudimos detectar el precio en esta página. Probá con otra URL o " +
                    "revisá que el producto esté visible sin iniciar sesión."
            )

        val finalInStock = resolveStockStatus(doc, outOfStockKeyword, base.inStock)
        if (finalInStock == base.inStock) base else base.copy(inStock = finalInStock)
    }

    /**
     * Combina el dato estructurado (JSON-LD/Open Graph/microdata) con una revisión del texto
     * visible de la página. Si se definió una frase personalizada para este producto, esa
     * frase decide todo: si aparece en la página, está agotado; si no aparece, está disponible.
     * Si no hay frase personalizada, se busca una lista de frases comunes de "agotado",
     * cortando la búsqueda antes de cualquier sección de "productos relacionados" para no
     * confundirse con la disponibilidad de OTRO producto que aparezca más abajo en la página.
     */
    private fun resolveStockStatus(doc: Document, customKeyword: String?, structuredInStock: Boolean): Boolean {
        if (!customKeyword.isNullOrBlank()) {
            val text = doc.body()?.text()?.lowercase() ?: return structuredInStock
            return !text.contains(customKeyword.trim().lowercase())
        }

        val text = mainProductTextBeforeRelatedSections(doc)
        if (GENERIC_OUT_OF_STOCK_PHRASES.any { text.contains(it) }) return false

        return structuredInStock
    }

    private fun mainProductTextBeforeRelatedSections(doc: Document): String {
        val fullText = (doc.body()?.text() ?: "").lowercase()
        var cutoff = fullText.length
        for (marker in RELATED_SECTION_MARKERS) {
            val idx = fullText.indexOf(marker)
            if (idx in 0 until cutoff) cutoff = idx
        }
        return fullText.substring(0, cutoff)
    }

    private val GENERIC_OUT_OF_STOCK_PHRASES = listOf(
        "fuera de stock", "sin stock", "sin existencias", "agotado", "agotados",
        "producto no disponible", "no disponible", "no hay stock", "en stock: 0",
        "out of stock", "sold out", "unavailable", "not available",
        "discontinuado", "discontinued"
    )

    private val RELATED_SECTION_MARKERS = listOf(
        "productos relacionados", "también te puede interesar", "tambien te puede interesar",
        "quienes compraron", "productos similares", "recomendados para ti",
        "recomendaciones para ti", "otros productos que te pueden interesar",
        "related products", "you may also like", "customers also bought", "similar products"
    )

    private fun fromJsonLd(doc: Document, storeName: String): ScrapedProduct? {
        for (script in doc.select("script[type=application/ld+json]")) {
            runCatching {
                val raw = script.data().trim()
                val candidates = mutableListOf<JSONObject>()
                if (raw.startsWith("[")) {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) candidates.add(arr.optJSONObject(i) ?: continue)
                } else {
                    candidates.add(JSONObject(raw))
                }
                for (obj in candidates) {
                    val flattened = flattenGraph(obj)
                    for (node in flattened) {
                        val type = node.opt("@type")?.toString() ?: continue
                        if (!type.contains("Product", ignoreCase = true)) continue
                        val name = node.optString("name").ifBlank { null } ?: continue
                        val offers = node.opt("offers")
                        val offer = extractOffer(offers) ?: continue
                        val image = extractImage(node.opt("image"))
                        return ScrapedProduct(
                            name = name,
                            price = offer.price,
                            currencySymbol = offer.currencySymbol,
                            imageUrl = image,
                            storeName = storeName,
                            inStock = offer.inStock
                        )
                    }
                }
            }
        }
        return null
    }

    private fun flattenGraph(obj: JSONObject): List<JSONObject> {
        val result = mutableListOf(obj)
        val graph = obj.optJSONArray("@graph")
        if (graph != null) {
            for (i in 0 until graph.length()) {
                graph.optJSONObject(i)?.let { result.add(it) }
            }
        }
        return result
    }

    private data class OfferInfo(val price: Double, val currencySymbol: String, val inStock: Boolean)

    private fun extractOffer(offersAny: Any?): OfferInfo? {
        val offerObj: JSONObject = when (offersAny) {
            is JSONObject -> offersAny
            is JSONArray -> if (offersAny.length() > 0) offersAny.optJSONObject(0) else null
            else -> null
        } ?: return null

        val priceRaw = offerObj.opt("price") ?: offerObj.opt("lowPrice") ?: return null
        val price = priceRaw.toString().replace(",", ".").toDoubleOrNull() ?: return null
        val currency = offerObj.optString("priceCurrency").ifBlank { "$" }
        val availability = offerObj.optString("availability").ifBlank { null }
        return OfferInfo(price, symbolFor(currency), parseAvailability(availability))
    }

    /**
     * Interpreta el campo "availability" de schema.org (ej: "https://schema.org/OutOfStock").
     * Si no hay dato, asumimos que está disponible para no mostrar "Agotado" de más (después
     * resolveStockStatus() revisa también el texto de la página para pescar lo que esto se
     * pierda).
     */
    private fun parseAvailability(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return true
        val normalized = raw.substringAfterLast("/").lowercase()
        return when {
            normalized.contains("outofstock") -> false
            normalized.contains("soldout") -> false
            normalized.contains("discontinued") -> false
            else -> true
        }
    }

    private fun extractImage(imageAny: Any?): String? = when (imageAny) {
        is String -> imageAny
        is JSONArray -> if (imageAny.length() > 0) imageAny.optString(0) else null
        is JSONObject -> imageAny.optString("url").ifBlank { null }
        else -> null
    }

    private fun fromOpenGraph(doc: Document, storeName: String): ScrapedProduct? {
        val priceText = doc.select("meta[property=product:price:amount]").attr("content")
            .ifBlank { doc.select("meta[property=og:price:amount]").attr("content") }
        val price = priceText.replace(",", ".").toDoubleOrNull() ?: return null

        val name = doc.select("meta[property=og:title]").attr("content")
            .ifBlank { doc.title() }
            .ifBlank { return null }

        val currency = doc.select("meta[property=product:price:currency]").attr("content")
            .ifBlank { doc.select("meta[property=og:price:currency]").attr("content") }
            .ifBlank { "$" }

        val image = doc.select("meta[property=og:image]").attr("content").ifBlank { null }

        val availability = doc.select("meta[property=product:availability]").attr("content")
            .ifBlank { null }

        return ScrapedProduct(name, price, symbolFor(currency), image, storeName, parseAvailability(availability))
    }

    private fun fromMicrodata(doc: Document, storeName: String): ScrapedProduct? {
        val priceText = doc.select("[itemprop=price]").firstOrNull()?.let {
            it.attr("content").ifBlank { it.text() }
        } ?: return null

        val price = extractNumber(priceText) ?: return null

        val name = doc.select("[itemprop=name]").firstOrNull()?.text()
            ?.ifBlank { null }
            ?: doc.title().ifBlank { return null }

        val image = doc.select("[itemprop=image]").firstOrNull()?.let {
            it.attr("src").ifBlank { it.attr("content") }
        }

        val availability = doc.select("[itemprop=availability]").firstOrNull()?.let {
            it.attr("href").ifBlank { it.attr("content").ifBlank { it.text() } }
        }

        return ScrapedProduct(name, price, "$", image, storeName, parseAvailability(availability))
    }

    private fun fromRegexFallback(doc: Document, storeName: String): ScrapedProduct? {
        val text = doc.body()?.text() ?: return null
        val matcher = CURRENCY_PATTERN.matcher(text)
        if (!matcher.find()) return null

        val price = extractNumber(matcher.group()) ?: return null
        val name = doc.select("h1").firstOrNull()?.text()
            ?.ifBlank { null }
            ?: doc.title().ifBlank { return null }

        val image = doc.select("meta[property=og:image]").attr("content").ifBlank {
            doc.select("img").firstOrNull()?.attr("abs:src")
        }

        return ScrapedProduct(name, price, "$", image, storeName)
    }

    private fun extractNumber(text: String): Double? {
        val cleaned = text.replace(Regex("[^0-9.,]"), "")
        if (cleaned.isBlank()) return null
        val normalized = if (cleaned.contains(",") && cleaned.contains(".")) {
            if (cleaned.lastIndexOf(",") > cleaned.lastIndexOf(".")) {
                cleaned.replace(".", "").replace(",", ".")
            } else {
                cleaned.replace(",", "")
            }
        } else if (cleaned.contains(",")) {
            cleaned.replace(",", ".")
        } else {
            cleaned
        }
        return normalized.toDoubleOrNull()
    }

    private fun symbolFor(currencyCode: String): String = when (currencyCode.uppercase()) {
        "USD" -> "US$"
        "EUR" -> "€"
        "ARS" -> "$"
        "MXN" -> "MX$"
        "CLP" -> "CLP$"
        "COP" -> "COL$"
        "GBP" -> "£"
        "$", "€", "£" -> currencyCode
        else -> if (currencyCode.length <= 3) currencyCode else "$"
    }

    private val CURRENCY_PATTERN: Pattern = Pattern.compile(
        "[\\$€£]\\s?\\d{1,3}([.,]\\d{3})*([.,]\\d{2})?"
    )
}
