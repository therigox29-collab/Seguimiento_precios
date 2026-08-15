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
    val lastCheckedAt: Long
)

data class PriceHistoryEntry(
    val id: Long = 0,
    val productId: Long,
    val price: Double,
    val checkedAt: Long
)
