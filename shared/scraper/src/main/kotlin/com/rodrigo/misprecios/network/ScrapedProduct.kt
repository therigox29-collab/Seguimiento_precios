package com.rodrigo.misprecios.network

data class ScrapedProduct(
    val name: String,
    val price: Double,
    val currencySymbol: String,
    val imageUrl: String?,
    val storeName: String,
    val inStock: Boolean = true,
    /** Qué estrategia encontró el precio (útil para entender por qué un precio sale mal). */
    val source: PriceSource = PriceSource.UNKNOWN
)

enum class PriceSource(val label: String) {
    CUSTOM_SELECTOR("selector personalizado"),
    JSON_LD("datos estructurados JSON-LD"),
    SHOPIFY("API de Shopify"),
    VTEX("API de VTEX"),
    OPEN_GRAPH("etiquetas Open Graph"),
    MICRODATA("microdata (itemprop)"),
    HTML_HEURISTIC("elementos de precio del HTML"),
    TEXT_PATTERN("texto de la página (menos confiable)"),
    UNKNOWN("desconocido")
}

class ScrapeException(message: String) : Exception(message)
