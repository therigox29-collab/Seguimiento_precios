package com.rodrigo.misprecios.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Extrae nombre, precio, imagen, moneda y disponibilidad de la página de un producto.
 *
 * Código compartido entre la app Android y la de escritorio (carpeta shared/scraper).
 *
 * Estrategias, en orden de confiabilidad:
 * 0) Selector CSS personalizado del producto (si el usuario lo configuró, manda sobre todo).
 * 1) JSON-LD (schema.org/Product, ProductGroup, AggregateOffer, priceSpecification...).
 * 2) APIs públicas de la plataforma: Shopify (/products/x.js) y VTEX (catalog_system).
 *    Sirven para tiendas que arman la página con JavaScript.
 * 3) Open Graph / meta tags (product:price:amount, product:sale_price:amount).
 * 4) Microdata (itemprop="price") dentro del bloque del producto.
 * 5) Elementos del HTML con clases de precio ("price", "precio"...), descartando precios
 *    tachados, cuotas, envío y secciones de productos relacionados.
 * 6) Patrón de moneda en el texto, empezando desde el título del producto.
 */
object PriceScraper {

    // ---------------------------------------------------------------------------------
    // Descarga
    // ---------------------------------------------------------------------------------

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36"

    private const val MAX_RETRIES = 2
    private val RETRYABLE_CODES = setOf(408, 425, 429, 500, 502, 503, 504)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(MemoryCookieJar)
        .build()

    private class Downloaded(val bytes: ByteArray, val charset: String?) {
        fun asText(): String = String(bytes, runCatching { Charset.forName(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8))
    }

    /**
     * @param outOfStockKeyword frase que, si aparece en la página, marca el producto como agotado.
     * @param priceSelector selector CSS opcional (ej: ".product-info .price-final") para
     *        tiendas donde la detección automática toma un precio equivocado.
     */
    suspend fun fetch(
        url: String,
        outOfStockKeyword: String? = null,
        priceSelector: String? = null
    ): ScrapedProduct = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url)
        val page = download(normalizedUrl, html = true)
        val doc = Jsoup.parse(ByteArrayInputStream(page.bytes), page.charset, normalizedUrl)
        extract(doc, normalizedUrl, outOfStockKeyword, priceSelector) { apiUrl ->
            runCatching { download(apiUrl, html = false).asText() }.getOrNull()
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) throw ScrapeException("La URL está vacía.")
        return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed
        else "https://$trimmed"
    }

    private fun headersFor(html: Boolean): Headers {
        val builder = Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .add("Accept-Language", "es-419,es;q=0.9,en;q=0.8")
            .add("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
            .add("sec-ch-ua-mobile", "?0")
            .add("sec-ch-ua-platform", "\"Windows\"")
        if (html) {
            builder
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .add("Upgrade-Insecure-Requests", "1")
                .add("Sec-Fetch-Dest", "document")
                .add("Sec-Fetch-Mode", "navigate")
                .add("Sec-Fetch-Site", "none")
                .add("Sec-Fetch-User", "?1")
        } else {
            builder
                .add("Accept", "application/json, text/javascript, */*;q=0.01")
                .add("Sec-Fetch-Dest", "empty")
                .add("Sec-Fetch-Mode", "cors")
                .add("Sec-Fetch-Site", "same-origin")
        }
        // No se agrega Accept-Encoding a mano: OkHttp pide gzip y lo descomprime solo.
        return builder.build()
    }

    /** Descarga con reintentos ante errores temporales (429, 503, cortes de red...). */
    private suspend fun download(url: String, html: Boolean): Downloaded {
        val request = try {
            Request.Builder().url(url).headers(headersFor(html)).build()
        } catch (e: IllegalArgumentException) {
            throw ScrapeException("La URL no es válida: $url")
        }

        var lastProblem = "sin respuesta"
        var waitMs = 1_500L
        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                delay(waitMs)
                waitMs *= 2
            }
            try {
                client.newCall(request).execute().use { response ->
                    val code = response.code
                    when {
                        response.isSuccessful -> {
                            val body = response.body ?: throw ScrapeException("Respuesta vacía del sitio")
                            return Downloaded(body.bytes(), body.contentType()?.charset()?.name())
                        }
                        code in RETRYABLE_CODES -> {
                            lastProblem = "error $code"
                            response.header("Retry-After")?.trim()?.toLongOrNull()?.let { seconds ->
                                waitMs = (seconds * 1000).coerceIn(1_000L, 10_000L)
                            }
                        }
                        code == 401 || code == 403 -> throw ScrapeException(
                            "La tienda rechazó la consulta (error $code). Probablemente tiene " +
                                "protección anti-bot que bloquea apps como esta."
                        )
                        code == 404 || code == 410 -> throw ScrapeException(
                            "La página no existe (error $code). Revisá la URL del producto."
                        )
                        else -> throw ScrapeException("La tienda respondió con error $code")
                    }
                }
            } catch (e: ScrapeException) {
                throw e
            } catch (e: IOException) {
                lastProblem = e.message ?: e.javaClass.simpleName
            }
        }
        throw ScrapeException("No pudimos conectarnos con la tienda ($lastProblem). Probá de nuevo más tarde.")
    }

    /** Guarda cookies en memoria: algunas tiendas redirigen para setear una cookie y fallan sin ella. */
    private object MemoryCookieJar : CookieJar {
        private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = store.getOrPut(url.host) { Collections.synchronizedList(mutableListOf()) }
            synchronized(list) {
                list.removeAll { old -> cookies.any { it.name == old.name } }
                list.addAll(cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return store.values.flatMap { list ->
                synchronized(list) { list.filter { it.expiresAt > now && it.matches(url) } }
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Extracción (separada de la descarga para poder probarla con HTML guardado)
    // ---------------------------------------------------------------------------------

    internal suspend fun extract(
        doc: Document,
        url: String,
        outOfStockKeyword: String? = null,
        priceSelector: String? = null,
        fetchText: suspend (String) -> String? = { null }
    ): ScrapedProduct {
        val storeName = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: "Tienda"
        val customSelector = priceSelector?.trim()?.ifBlank { null }

        val base: ScrapedProduct = if (customSelector != null) {
            val custom = fromCustomSelector(doc, customSelector)
                ?: throw ScrapeException(
                    "El selector \"$customSelector\" no encontró ningún precio en la página. " +
                        "Revisalo o dejalo vacío para usar la detección automática."
                )
            // Nombre e imagen se siguen sacando con las estrategias automáticas (sin red).
            val auto = fromJsonLd(doc, url, storeName)
                ?: fromOpenGraph(doc, storeName)
                ?: fromMicrodata(doc, storeName)
            val currency = custom.second?.let { PriceParser.symbolFor(it) }
                ?: auto?.currencySymbol ?: "$"
            (auto ?: ScrapedProduct(
                name = fallbackName(doc) ?: storeName,
                price = custom.first,
                currencySymbol = currency,
                imageUrl = ogImage(doc),
                storeName = storeName
            )).copy(price = custom.first, currencySymbol = currency, source = PriceSource.CUSTOM_SELECTOR)
        } else {
            fromJsonLd(doc, url, storeName)
                ?: fromShopify(doc, url, storeName, fetchText)
                ?: fromVtex(doc, url, storeName, fetchText)
                ?: fromOpenGraph(doc, storeName)
                ?: fromMicrodata(doc, storeName)
                ?: fromHtmlHeuristic(doc, storeName)
                ?: fromTextPattern(doc, storeName)
                ?: throw if (looksLikeBotChallenge(doc)) {
                    ScrapeException(
                        "La tienda mostró una verificación anti-bot (captcha / \"un momento...\") " +
                            "en lugar del producto, así que no se puede leer el precio automáticamente."
                    )
                } else {
                    ScrapeException(
                        "No pudimos detectar el precio en esta página. Probá con otra URL, revisá que " +
                            "el producto esté visible sin iniciar sesión, o configurá un selector CSS de precio."
                    )
                }
        }

        val finalInStock = resolveStockStatus(doc, outOfStockKeyword, base.inStock)
        return if (finalInStock == base.inStock) base else base.copy(inStock = finalInStock)
    }

    // ---------------------------------------------------------------------------------
    // 0) Selector personalizado
    // ---------------------------------------------------------------------------------

    /** Devuelve (precio, código de moneda detectado) o null. */
    private fun fromCustomSelector(doc: Document, selector: String): Pair<Double, String?>? {
        val elements = try {
            doc.select(selector)
        } catch (e: Exception) {
            throw ScrapeException("El selector CSS \"$selector\" no es válido.")
        }
        for (el in elements) {
            val text = listOf(el.attr("content"), el.attr("data-price"), el.attr("value"), el.text())
                .firstOrNull { PriceParser.parse(it) != null } ?: continue
            val price = PriceParser.parse(text) ?: continue
            return price to PriceParser.detectCurrencyCode(text)
        }
        return null
    }

    // ---------------------------------------------------------------------------------
    // 1) JSON-LD
    // ---------------------------------------------------------------------------------

    private data class OfferInfo(val price: Double, val currency: String?, val inStock: Boolean, val sku: String? = null)

    private fun fromJsonLd(doc: Document, pageUrl: String, storeName: String): ScrapedProduct? {
        val products = mutableListOf<JSONObject>()
        for (script in doc.select("script[type=application/ld+json]")) {
            val parsed = parseJsonLenient(script.data()) ?: continue
            collectProductNodes(parsed, products, 0)
        }
        if (products.isEmpty()) return null

        // Si la página tiene varios productos (relacionados, listados), primero el que
        // corresponde a esta URL o cuyo nombre coincide con el título. sortedByDescending es
        // estable: a igual puntaje se mantiene el orden del documento.
        val h1 = doc.selectFirst("h1")?.text()?.let { cleanText(it) }?.lowercase()?.ifBlank { null }
        fun score(node: JSONObject): Int {
            var total = 0
            if (nodeMatchesUrl(node, pageUrl)) total += 2
            val nodeName = cleanText(node.optString("name")).lowercase()
            if (h1 != null && nodeName.isNotEmpty() && (h1.contains(nodeName) || nodeName.contains(h1))) total += 1
            return total
        }
        val first = products.first()
        val ordered = products.sortedByDescending { score(it) }
        for (node in ordered) {
            // Con varios productos, uno que no es el primero ni coincide con URL/título es
            // casi seguro un "relacionado": no tomamos su precio.
            if (products.size > 1 && node !== first && score(node) == 0) continue
            val offer = bestOffer(node, pageUrl) ?: continue
            val name = node.optString("name").ifBlank { null }?.let { cleanText(it) }
                ?: fallbackName(doc) ?: continue
            val image = extractImage(node.opt("image")) ?: ogImage(doc)
            return ScrapedProduct(
                name = name,
                price = offer.price,
                currencySymbol = PriceParser.symbolFor(offer.currency ?: metaCurrency(doc)),
                imageUrl = absolutize(image, pageUrl),
                storeName = storeName,
                inStock = offer.inStock,
                source = PriceSource.JSON_LD
            )
        }
        return null
    }

    /** Parsea JSON tolerando errores comunes de las tiendas (comas de más, saltos de línea, comentarios). */
    private fun parseJsonLenient(raw: String): Any? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // JSONTokener devuelve un String si el contenido no es JSON: solo aceptamos objetos/listas.
        runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()
            ?.takeIf { it is JSONObject || it is JSONArray }?.let { return it }

        val cleaned = trimmed
            .removePrefix("<!--").removeSuffix("-->")
            .replace("//<![CDATA[", "").replace("//]]>", "")
            .replace("<![CDATA[", "").replace("]]>", "")
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex(",\\s*([}\\]])"), "$1")
            .trim()
        return runCatching { JSONTokener(cleaned).nextValue() }.getOrNull()
            ?.takeIf { it is JSONObject || it is JSONArray }
    }

    private val PRODUCT_TYPES = setOf("product", "productgroup", "individualproduct", "productmodel", "vehicle", "car")

    private fun isProductNode(obj: JSONObject): Boolean {
        val types = when (val t = obj.opt("@type")) {
            is String -> listOf(t)
            is JSONArray -> (0 until t.length()).map { t.optString(it) }
            else -> emptyList()
        }
        return types.any { it.substringAfterLast('/').lowercase() in PRODUCT_TYPES }
    }

    /** Recorre todo el árbol: el producto puede estar en @graph, mainEntity, itemListElement... */
    private fun collectProductNodes(value: Any?, out: MutableList<JSONObject>, depth: Int) {
        if (depth > 12) return
        when (value) {
            is JSONObject -> {
                if (isProductNode(value)) out.add(value)
                val keys = value.keys()
                while (keys.hasNext()) collectProductNodes(value.opt(keys.next()), out, depth + 1)
            }
            is JSONArray -> for (i in 0 until value.length()) collectProductNodes(value.opt(i), out, depth + 1)
        }
    }

    private fun nodeMatchesUrl(node: JSONObject, pageUrl: String): Boolean {
        val path = runCatching { URI(pageUrl).path }.getOrNull()?.trimEnd('/')
        if (path.isNullOrEmpty()) return false
        val candidates = mutableListOf(node.optString("url"), node.optString("@id"))
        asObjects(node.opt("offers")).forEach { candidates.add(it.optString("url")) }
        return candidates.any { it.isNotBlank() && it.contains(path) }
    }

    private fun asObjects(value: Any?): List<JSONObject> = when (value) {
        is JSONObject -> listOf(value)
        is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
        else -> emptyList()
    }

    private fun bestOffer(node: JSONObject, pageUrl: String): OfferInfo? {
        val variants = asObjects(node.opt("hasVariant"))
        val wantedVariant = variantIdFrom(pageUrl)

        // Si la URL apunta a una variante concreta (?variant=123), usar esa.
        if (wantedVariant != null && variants.isNotEmpty()) {
            val selected = variants.firstOrNull { v ->
                val ids = mutableListOf(v.optString("sku"), v.optString("@id"), v.optString("url"), v.optString("productID"))
                asObjects(v.opt("offers")).forEach { ids.add(it.optString("url")); ids.add(it.optString("sku")) }
                ids.any { it.isNotBlank() && it.contains(wantedVariant) }
            }
            if (selected != null) {
                val selectedOffers = mutableListOf<OfferInfo>()
                collectOffers(selected.opt("offers"), selectedOffers, 0)
                pickBest(selectedOffers)?.let { return it }
            }
        }

        val offers = mutableListOf<OfferInfo>()
        collectOffers(node.opt("offers"), offers, 0)
        if (offers.isEmpty()) variants.forEach { collectOffers(it.opt("offers"), offers, 0) }

        // Un mismo producto con varias ofertas, una por variante (ej. VTEX: ?skuId=402 y
        // ofertas con "sku":"00000402"): quedarse con la de la variante de la URL.
        if (wantedVariant != null) {
            val matching = offers.filter { skuMatches(it.sku, wantedVariant) }
            if (matching.isNotEmpty()) return pickBest(matching)
        }
        return pickBest(offers)
    }

    /** Compara SKUs ignorando ceros a la izquierda ("00000402" == "402"). */
    private fun skuMatches(sku: String?, wanted: String): Boolean {
        if (sku.isNullOrBlank()) return false
        val a = sku.trim().trimStart('0')
        val b = wanted.trim().trimStart('0')
        return a.isNotEmpty() && a.equals(b, ignoreCase = true)
    }

    private fun collectOffers(value: Any?, out: MutableList<OfferInfo>, depth: Int) {
        if (depth > 4) return
        when (value) {
            is JSONArray -> for (i in 0 until value.length()) collectOffers(value.opt(i), out, depth + 1)
            is JSONObject -> {
                // AggregateOffer con la lista de ofertas adentro: las ofertas individuales
                // tienen precio Y disponibilidad reales. El resumen (lowPrice) no trae
                // disponibilidad, así que si se lo usara parecería "con stock" aunque todas
                // las variantes estén agotadas.
                if (value.has("offers")) {
                    val before = out.size
                    collectOffers(value.opt("offers"), out, depth + 1)
                    if (out.size > before) return
                }
                val spec = priceFromSpecification(value.opt("priceSpecification"))
                // Ojo: "price" puede venir como "" -> en ese caso se prueba el resto.
                val price = PriceParser.parseAny(value.opt("price"))
                    ?: spec?.first
                    ?: PriceParser.parseAny(value.opt("lowPrice"))
                val currency = value.optString("priceCurrency").ifBlank { null } ?: spec?.second
                if (price != null) {
                    val sku = value.optString("sku").ifBlank { value.optJSONObject("itemOffered")?.optString("sku") }
                        ?.ifBlank { null }
                    out.add(OfferInfo(price, currency, parseAvailability(value.optString("availability")), sku))
                }
            }
        }
    }

    /** Lee priceSpecification ignorando el "precio de lista" (tachado). */
    private fun priceFromSpecification(value: Any?): Pair<Double, String?>? {
        val specs = asObjects(value)
        val usable = specs.filterNot {
            val type = it.optString("priceType").lowercase()
            type.contains("listprice") || type.contains("strikethrough") || type.contains("msrp")
        }.ifEmpty { specs }
        return usable.mapNotNull { spec ->
            PriceParser.parseAny(spec.opt("price"))?.let { it to spec.optString("priceCurrency").ifBlank { null } }
        }.minByOrNull { it.first }
    }

    /** Entre varias ofertas, la más barata con stock; si ninguna tiene stock, la más barata. */
    private fun pickBest(offers: List<OfferInfo>): OfferInfo? =
        offers.filter { it.inStock }.minByOrNull { it.price } ?: offers.minByOrNull { it.price }

    /**
     * Interpreta el campo "availability" de schema.org (ej: "https://schema.org/OutOfStock").
     * Si no hay dato, asumimos que está disponible (resolveStockStatus() revisa además el texto).
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
        is String -> imageAny.ifBlank { null }
        is JSONArray -> (0 until imageAny.length()).firstNotNullOfOrNull { extractImage(imageAny.opt(it)) }
        is JSONObject -> imageAny.optString("url").ifBlank { imageAny.optString("contentUrl") }.ifBlank { null }
        else -> null
    }

    // ---------------------------------------------------------------------------------
    // 2) APIs de plataforma (para tiendas que arman el precio con JavaScript)
    // ---------------------------------------------------------------------------------

    private suspend fun fromShopify(
        doc: Document,
        url: String,
        storeName: String,
        fetchText: suspend (String) -> String?
    ): ScrapedProduct? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val path = uri.path?.trimEnd('/') ?: return null
        if (!path.contains("/products/")) return null
        val scripts = scriptsText(doc)
        val isShopify = scripts.contains("Shopify.shop") || scripts.contains("cdn.shopify.com") ||
            doc.select("link[href*=cdn.shopify.com], script[src*=cdn.shopify.com]").isNotEmpty()
        if (!isShopify) return null

        val body = fetchText("${uri.scheme}://${uri.host}$path.js") ?: return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val variants = asObjects(json.opt("variants"))
        val wanted = variantIdFrom(url)
        val chosen = variants.firstOrNull { wanted != null && it.opt("id")?.toString() == wanted }
            ?: variants.firstOrNull { it.optBoolean("available", false) }
            ?: variants.firstOrNull()

        // Shopify devuelve los precios en centavos, también para monedas sin decimales.
        val cents = PriceParser.parseAny(chosen?.opt("price") ?: json.opt("price")) ?: return null
        val inStock = chosen?.optBoolean("available", json.optBoolean("available", true))
            ?: json.optBoolean("available", true)
        val currency = Regex("Shopify\\.currency\\s*=\\s*\\{\\s*\"active\"\\s*:\\s*\"([A-Za-z]{3})\"")
            .find(scripts)?.groupValues?.get(1) ?: metaCurrency(doc)
        val image = json.optString("featured_image").ifBlank { null } ?: ogImage(doc)

        return ScrapedProduct(
            name = cleanText(json.optString("title")).ifBlank { fallbackName(doc) ?: storeName },
            price = cents / 100.0,
            currencySymbol = PriceParser.symbolFor(currency),
            imageUrl = absolutize(image, url),
            storeName = storeName,
            inStock = inStock,
            source = PriceSource.SHOPIFY
        )
    }

    private suspend fun fromVtex(
        doc: Document,
        url: String,
        storeName: String,
        fetchText: suspend (String) -> String?
    ): ScrapedProduct? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val path = uri.path?.trimEnd('/') ?: return null
        if (!path.endsWith("/p")) return null
        val scripts = scriptsText(doc)
        val isVtex = scripts.contains("vtex", ignoreCase = true) ||
            doc.select("[src*=vteximg], [href*=vteximg], [src*=vtexassets], [href*=vtexassets]").isNotEmpty()
        if (!isVtex) return null

        val slug = path.removeSuffix("/p").substringAfterLast('/').ifBlank { return null }
        val body = fetchText("${uri.scheme}://${uri.host}/api/catalog_system/pub/products/search/$slug/p")
            ?: return null
        val product = runCatching { JSONArray(body).optJSONObject(0) }.getOrNull() ?: return null

        val wantedSku = queryParam(url, "skuId") ?: queryParam(url, "sku")
        val allItems = asObjects(product.opt("items"))
        val items = allItems.filter { wantedSku != null && it.optString("itemId") == wantedSku }.ifEmpty { allItems }

        val offers = mutableListOf<OfferInfo>()
        var image: String? = null
        for (item in items) {
            if (image == null) image = asObjects(item.opt("images")).firstOrNull()?.optString("imageUrl")?.ifBlank { null }
            for (seller in asObjects(item.opt("sellers"))) {
                val offer = seller.optJSONObject("commertialOffer") ?: continue
                val price = PriceParser.parseAny(offer.opt("Price")) ?: continue // 0 = sin precio
                offers.add(OfferInfo(price, null, offer.optInt("AvailableQuantity", 0) > 0))
            }
        }
        val best = pickBest(offers) ?: return null
        val currency = Regex("\"currencyCode\"\\s*:\\s*\"([A-Za-z]{3})\"").find(scripts)?.groupValues?.get(1)
            ?: metaCurrency(doc)

        return ScrapedProduct(
            name = cleanText(product.optString("productName")).ifBlank { fallbackName(doc) ?: storeName },
            price = best.price,
            currencySymbol = PriceParser.symbolFor(currency),
            imageUrl = absolutize(image ?: ogImage(doc), url),
            storeName = storeName,
            inStock = best.inStock,
            source = PriceSource.VTEX
        )
    }

    // ---------------------------------------------------------------------------------
    // 3) Open Graph / meta tags
    // ---------------------------------------------------------------------------------

    private fun meta(doc: Document, vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        doc.selectFirst("meta[property=\"$key\"], meta[name=\"$key\"], meta[itemprop=\"$key\"]")
            ?.attr("content")?.trim()?.ifBlank { null }
    }

    private fun metaCurrency(doc: Document): String? =
        meta(doc, "product:price:currency", "og:price:currency", "priceCurrency")

    private fun ogImage(doc: Document): String? = meta(doc, "og:image", "og:image:url", "twitter:image")

    private fun fromOpenGraph(doc: Document, storeName: String): ScrapedProduct? {
        val sale = PriceParser.parse(meta(doc, "product:sale_price:amount", "og:sale_price:amount"))
        val regular = PriceParser.parse(meta(doc, "product:price:amount", "og:price:amount", "product:price"))
        val price = listOfNotNull(sale, regular).minOrNull() ?: return null

        val name = meta(doc, "og:title")?.let { cleanText(it) } ?: fallbackName(doc) ?: return null
        val availability = meta(doc, "product:availability", "og:availability")

        return ScrapedProduct(
            name = name,
            price = price,
            currencySymbol = PriceParser.symbolFor(metaCurrency(doc)),
            imageUrl = absolutize(ogImage(doc), doc.location()),
            storeName = storeName,
            inStock = parseAvailability(availability),
            source = PriceSource.OPEN_GRAPH
        )
    }

    // ---------------------------------------------------------------------------------
    // 4) Microdata
    // ---------------------------------------------------------------------------------

    private fun fromMicrodata(doc: Document, storeName: String): ScrapedProduct? {
        val scope = doc.selectFirst("[itemscope][itemtype*=\"schema.org/Product\"]")
        val root: Element = scope ?: doc

        val priceEl = root.select("[itemprop=price], [itemprop=lowPrice]").firstOrNull {
            PriceParser.parse(it.attr("content").ifBlank { it.text() }) != null
        } ?: return null
        val priceText = priceEl.attr("content").ifBlank { priceEl.text() }
        val price = PriceParser.parse(priceText) ?: return null

        val currency = root.selectFirst("[itemprop=priceCurrency]")?.let { it.attr("content").ifBlank { it.text() } }
            ?.ifBlank { null } ?: PriceParser.detectCurrencyCode(priceEl.text())

        // El primer itemprop=name puede ser el de la marca: buscamos el que pertenece al producto.
        val name = root.select("[itemprop=name]").firstOrNull { el ->
            scope == null || el.parents().firstOrNull { it.hasAttr("itemscope") } == scope
        }?.let { it.attr("content").ifBlank { it.text() } }?.ifBlank { null }?.let { cleanText(it) }
            ?: fallbackName(doc) ?: return null

        val image = root.selectFirst("[itemprop=image]")?.let {
            it.attr("abs:src").ifBlank { it.attr("abs:content").ifBlank { it.attr("content") } }
        }?.ifBlank { null } ?: ogImage(doc)

        val availability = root.selectFirst("[itemprop=availability]")?.let {
            it.attr("href").ifBlank { it.attr("content").ifBlank { it.text() } }
        }

        return ScrapedProduct(
            name = name,
            price = price,
            currencySymbol = PriceParser.symbolFor(currency),
            imageUrl = absolutize(image, doc.location()),
            storeName = storeName,
            inStock = parseAvailability(availability),
            source = PriceSource.MICRODATA
        )
    }

    // ---------------------------------------------------------------------------------
    // 5) Heurística sobre elementos con clase de precio
    // ---------------------------------------------------------------------------------

    private const val PRICE_CANDIDATES =
        "[class*=price], [class*=precio], [id*=price], [id*=precio], [data-price], [data-product-price], [itemprop=price]"

    /** Pistas en class/id de que NO es el precio actual (tachado, cuotas, envío...). */
    private val EXCLUDED_HINTS = listOf(
        "old-price", "oldprice", "old_price", "price-old", "price--old", "price_old",
        "price-before", "before-price", "price--before", "precio-antes", "precio-anterior", "precioanterior",
        "price-was", "was-price", "compare", "list-price", "listprice", "list_price", "price--list",
        "original", "strike", "tachado", "crossed", "cuota", "installment", "shipping", "envio",
        "saving", "ahorro", "you-save", "descuento", "discount-percent", "percent", "price-per",
        "unit-price", "price-range", "price-filter", "price-slider"
    )

    /** Pistas de que es el precio de venta actual. */
    private val PREFERRED_HINTS = listOf(
        "sale", "special", "current", "final", "offer", "oferta", "actual", "selling", "promo", "best", "now"
    )

    private const val NOISE =
        "script, style, noscript, template, svg, iframe, del, s, strike, select, option, " +
            "[aria-hidden=true], [hidden], [style*=display:none], [style*=display: none]"

    private const val LAYOUT_NOISE =
        "header, footer, nav, [role=navigation], [role=banner], [role=contentinfo], " +
            "[class*=related], [id*=related], [class*=recommend], [id*=recommend], [class*=similar], " +
            "[class*=upsell], [class*=cross-sell], [class*=crosssell], [class*=recently], [class*=minicart], " +
            "[class*=mini-cart], [class*=cart-drawer], [class*=breadcrumb]"

    /** Copia del body sin ruido: tachados, ocultos, menú, pie, carrito y productos relacionados. */
    private fun cleanedBody(doc: Document): Element? {
        val body = doc.body()?.clone() ?: return null
        body.select(NOISE).remove()
        // No borrar contenedores que tengan el título del producto adentro.
        body.select(LAYOUT_NOISE).filter { it.selectFirst("h1") == null }.forEach { it.remove() }
        return body
    }

    private fun fromHtmlHeuristic(doc: Document, storeName: String): ScrapedProduct? {
        val body = cleanedBody(doc) ?: return null
        val all = body.allElements
        val h1Index = body.selectFirst("h1")?.let { all.indexOf(it) } ?: -1

        var bestPrice: Double? = null
        var bestText = ""
        var bestScore = Int.MIN_VALUE

        for (el in body.select(PRICE_CANDIDATES)) {
            val hints = (el.className() + " " + el.id()).lowercase()
            if (EXCLUDED_HINTS.any { hints.contains(it) }) continue
            if (el.parents().any { p -> EXCLUDED_HINTS.any { (p.className() + " " + p.id()).lowercase().contains(it) } }) continue

            val text = el.attr("data-price").ifBlank { el.attr("content") }.ifBlank { el.text() }.trim()
            if (text.isEmpty() || text.length > 40 || text.none { it.isDigit() }) continue
            val price = PriceParser.parse(text) ?: continue

            var score = 0
            if (PREFERRED_HINTS.any { hints.contains(it) }) score += 3
            if (PriceParser.detectCurrencyCode(text) != null || text.contains('$')) score += 2
            if (el.hasAttr("data-price") || el.attr("itemprop") == "price") score += 2
            if (h1Index >= 0) {
                val idx = all.indexOf(el)
                // Los precios del producto principal suelen estar cerca y después del título.
                if (idx > h1Index) score += 2
                if (idx > h1Index && idx - h1Index < 150) score += 1
            }
            if (score > bestScore) {
                bestScore = score
                bestPrice = price
                bestText = text
            }
        }

        val price = bestPrice ?: return null
        return ScrapedProduct(
            name = fallbackName(doc) ?: return null,
            price = price,
            currencySymbol = PriceParser.symbolFor(PriceParser.detectCurrencyCode(bestText) ?: metaCurrency(doc)),
            imageUrl = absolutize(ogImage(doc) ?: firstContentImage(doc), doc.location()),
            storeName = storeName,
            source = PriceSource.HTML_HEURISTIC
        )
    }

    // ---------------------------------------------------------------------------------
    // 6) Patrón de moneda en el texto (último recurso)
    // ---------------------------------------------------------------------------------

    private const val NUM = "(\\d{1,3}(?:[.,\\u00A0 ]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)"
    private const val PREFIX = "(?:US\\\$|U\\\$S|R\\\$|S/\\.?|CLP\\\$?|COP\\\$?|MX\\\$|\\\$U|\\\$|€|£)"
    private val CURRENCY_PATTERN = Regex("$PREFIX\\s?$NUM|$NUM\\s?(?:€|EUR|USD|CLP|ARS|MXN|COP|PEN|BRL)(?![A-Za-z])")

    /** Si el texto justo antes del precio tiene alguna de estas palabras, no es el precio del producto. */
    private val SKIP_CONTEXT = listOf(
        "cuota", "envío", "envio", "ahorr", "antes", "desde", "por mes", "x mes", "/mes",
        "shipping", "save", "was", "normal", "mínimo", "minimo", "sobre"
    )

    private fun fromTextPattern(doc: Document, storeName: String): ScrapedProduct? {
        val text = cleanedBody(doc)?.text() ?: return null
        val h1Text = doc.selectFirst("h1")?.text()?.trim()?.ifBlank { null }
        val start = h1Text?.let { text.indexOf(it) }?.takeIf { it >= 0 } ?: 0

        val starts = if (start > 0) listOf(start, 0) else listOf(0)
        for (from in starts) {
            for (match in CURRENCY_PATTERN.findAll(text, from)) {
                val before = text.substring(maxOf(0, match.range.first - 30), match.range.first).lowercase()
                if (SKIP_CONTEXT.any { before.contains(it) }) continue
                val number = match.groups[1]?.value ?: match.groups[2]?.value ?: continue
                val price = PriceParser.parse(number) ?: continue
                return ScrapedProduct(
                    name = fallbackName(doc) ?: return null,
                    price = price,
                    currencySymbol = PriceParser.symbolFor(PriceParser.detectCurrencyCode(match.value) ?: metaCurrency(doc)),
                    imageUrl = absolutize(ogImage(doc) ?: firstContentImage(doc), doc.location()),
                    storeName = storeName,
                    source = PriceSource.TEXT_PATTERN
                )
            }
        }
        return null
    }

    // ---------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------

    private fun scriptsText(doc: Document): String =
        doc.select("script").joinToString("\n") { it.data() }

    private fun fallbackName(doc: Document): String? =
        doc.selectFirst("h1")?.text()?.let { cleanText(it) }?.ifBlank { null }
            ?: meta(doc, "og:title", "twitter:title")?.let { cleanText(it) }
            ?: doc.title().let { cleanText(it) }.ifBlank { null }

    private fun firstContentImage(doc: Document): String? =
        doc.select("main img[src], article img[src], img[src]").firstOrNull { img ->
            val src = img.attr("src").lowercase()
            !src.startsWith("data:") && !src.contains("logo") && !src.contains("icon") && !src.endsWith(".svg")
        }?.attr("abs:src")?.ifBlank { null }

    private fun cleanText(text: String): String =
        Parser.unescapeEntities(text, false).replace(Regex("\\s+"), " ").trim()

    private fun absolutize(image: String?, pageUrl: String): String? {
        if (image.isNullOrBlank()) return null
        val img = image.trim()
        if (img.startsWith("//")) return "https:$img"
        if (img.startsWith("http://", true) || img.startsWith("https://", true)) return img
        return runCatching { URI(pageUrl).resolve(img).toString() }.getOrDefault(img)
    }

    private fun queryParam(url: String, name: String): String? {
        val query = runCatching { URI(url).rawQuery }.getOrNull() ?: return null
        return query.split('&').firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            if (key.equals(name, ignoreCase = true)) {
                runCatching { URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrNull()?.ifBlank { null }
            } else null
        }
    }

    private fun variantIdFrom(url: String): String? =
        queryParam(url, "variant") ?: queryParam(url, "skuId") ?: queryParam(url, "sku")

    private val BOT_TITLE_MARKERS = listOf(
        "just a moment", "un momento", "attention required", "access denied", "acceso denegado",
        "are you a robot", "are you a human", "captcha", "robot or human", "pardon our interruption",
        "verificando", "security check"
    )
    private val BOT_HTML_MARKERS = listOf(
        "cf-browser-verification", "challenge-platform", "cf-chl-", "px-captcha", "_incapsula_resource",
        "captcha-delivery.com", "geo.captcha", "perimeterx", "akamai-challenge"
    )

    private fun looksLikeBotChallenge(doc: Document): Boolean {
        val title = doc.title().lowercase()
        if (BOT_TITLE_MARKERS.any { title.contains(it) }) return true
        val html = doc.outerHtml().lowercase()
        return BOT_HTML_MARKERS.any { html.contains(it) }
    }

    // ---------------------------------------------------------------------------------
    // Disponibilidad
    // ---------------------------------------------------------------------------------

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
}
