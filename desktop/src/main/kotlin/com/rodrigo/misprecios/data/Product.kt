package com.rodrigo.misprecios.data

data class Product(
    val id: Long = 0,
    val url: String,
    val alias: String,
    val name: String,
    val imageUrl: String?,
    val storeName: String,
    val currentPrice: Double,
    val previousPrice: Double?,
    val currencySymbol: String,
    val lastCheckedAt: Long,
    val inStock: Boolean = true,
    val outOfStockKeyword: String? = null,
    /** Selector CSS opcional para tiendas donde la detección automática toma mal el precio. */
    val priceSelector: String? = null,
    /** Precio con un salto demasiado brusco, pendiente de confirmar en la próxima revisión. */
    val suspectPrice: Double? = null
)

data class PriceHistoryEntry(
    val id: Long = 0,
    val productId: Long,
    val price: Double,
    val checkedAt: Long
)
