package com.rodrigo.misprecios.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products ORDER BY lastCheckedAt DESC")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :productId")
    fun observeById(productId: Long): Flow<Product?>

    @Query("SELECT * FROM products")
    suspend fun getAll(): List<Product>

    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getById(productId: Long): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("SELECT * FROM price_history WHERE productId = :productId ORDER BY checkedAt ASC")
    fun observeHistory(productId: Long): Flow<List<PriceHistoryEntry>>

    @Insert
    suspend fun insertHistory(entry: PriceHistoryEntry)
}
