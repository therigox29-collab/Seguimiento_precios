package com.rodrigo.misprecios.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
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
    val notifyOnlyOnDrop: Boolean = false
)
