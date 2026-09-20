package com.rodrigo.misprecios.network

data class ScrapedProduct(
    val name: String,
    val price: Double,
    val currencySymbol: String,
    val imageUrl: String?,
    val storeName: String,
    val inStock: Boolean = true
)

class ScrapeException(message: String) : Exception(message)
