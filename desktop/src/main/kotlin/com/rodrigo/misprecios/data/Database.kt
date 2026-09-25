package com.rodrigo.misprecios.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

/**
 * Acceso a una base SQLite local, guardada en la carpeta de datos del usuario
 * (por ejemplo %APPDATA%/MisPrecios en Windows). No usa ningún servidor: todo
 * queda en el disco de esta computadora.
 */
object Database {

    private val dbFile: File by lazy {
        val appData = System.getenv("APPDATA")
            ?: (System.getProperty("user.home") + File.separator + ".misprecios")
        val dir = File(appData, "MisPrecios")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "misprecios.db")
    }

    private fun connect(): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    }

    fun init() {
        connect().use { conn ->
            conn.createStatement().use { stmt: Statement ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS products (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        url TEXT NOT NULL,
                        alias TEXT NOT NULL,
                        name TEXT NOT NULL,
                        imageUrl TEXT,
                        storeName TEXT NOT NULL,
                        currentPrice REAL NOT NULL,
                        previousPrice REAL,
                        currencySymbol TEXT NOT NULL,
                        lastCheckedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS price_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        productId INTEGER NOT NULL,
                        price REAL NOT NULL,
                        checkedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                // Migraciones livianas: si la base ya existía de una versión anterior sin estas
                // columnas, se agregan ahora. Si ya están, SQLite tira error y se ignora.
                runCatching {
                    stmt.execute("ALTER TABLE products ADD COLUMN inStock INTEGER NOT NULL DEFAULT 1")
                }
                runCatching {
                    stmt.execute("ALTER TABLE products ADD COLUMN outOfStockKeyword TEXT")
                }
            }
        }
    }

    fun getAllProducts(): List<Product> = connect().use { conn ->
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT * FROM products ORDER BY lastCheckedAt DESC")
            val result = mutableListOf<Product>()
            while (rs.next()) result.add(rs.toProduct())
            result
        }
    }

    fun getProduct(id: Long): Product? = connect().use { conn ->
        conn.prepareStatement("SELECT * FROM products WHERE id = ?").use { stmt ->
            stmt.setLong(1, id)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.toProduct() else null
        }
    }

    fun insertProduct(product: Product): Long = connect().use { conn ->
        conn.prepareStatement(
            "INSERT INTO products (url, alias, name, imageUrl, storeName, currentPrice, previousPrice, currencySymbol, lastCheckedAt, inStock, outOfStockKeyword) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS
        ).use { stmt ->
            stmt.setString(1, product.url)
            stmt.setString(2, product.alias)
            stmt.setString(3, product.name)
            stmt.setString(4, product.imageUrl)
            stmt.setString(5, product.storeName)
            stmt.setDouble(6, product.currentPrice)
            if (product.previousPrice != null) stmt.setDouble(7, product.previousPrice) else stmt.setNull(7, java.sql.Types.REAL)
            stmt.setString(8, product.currencySymbol)
            stmt.setLong(9, product.lastCheckedAt)
            stmt.setInt(10, if (product.inStock) 1 else 0)
            stmt.setString(11, product.outOfStockKeyword)
            stmt.executeUpdate()
            val keys = stmt.generatedKeys
            if (keys.next()) keys.getLong(1) else 0L
        }
    }

    fun updateProduct(product: Product) {
        connect().use { conn ->
            conn.prepareStatement(
                "UPDATE products SET alias=?, name=?, imageUrl=?, storeName=?, currentPrice=?, previousPrice=?, currencySymbol=?, lastCheckedAt=?, inStock=?, outOfStockKeyword=? WHERE id=?"
            ).use { stmt ->
                stmt.setString(1, product.alias)
                stmt.setString(2, product.name)
                stmt.setString(3, product.imageUrl)
                stmt.setString(4, product.storeName)
                stmt.setDouble(5, product.currentPrice)
                if (product.previousPrice != null) stmt.setDouble(6, product.previousPrice) else stmt.setNull(6, java.sql.Types.REAL)
                stmt.setString(7, product.currencySymbol)
                stmt.setLong(8, product.lastCheckedAt)
                stmt.setInt(9, if (product.inStock) 1 else 0)
                stmt.setString(10, product.outOfStockKeyword)
                stmt.setLong(11, product.id)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteProduct(id: Long) {
        connect().use { conn ->
            conn.prepareStatement("DELETE FROM products WHERE id = ?").use { stmt ->
                stmt.setLong(1, id)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM price_history WHERE productId = ?").use { stmt ->
                stmt.setLong(1, id)
                stmt.executeUpdate()
            }
        }
    }

    fun insertHistory(entry: PriceHistoryEntry) {
        connect().use { conn ->
            conn.prepareStatement(
                "INSERT INTO price_history (productId, price, checkedAt) VALUES (?, ?, ?)"
            ).use { stmt ->
                stmt.setLong(1, entry.productId)
                stmt.setDouble(2, entry.price)
                stmt.setLong(3, entry.checkedAt)
                stmt.executeUpdate()
            }
        }
    }

    fun getHistory(productId: Long): List<PriceHistoryEntry> = connect().use { conn ->
        conn.prepareStatement("SELECT * FROM price_history WHERE productId = ? ORDER BY checkedAt ASC").use { stmt ->
            stmt.setLong(1, productId)
            val rs = stmt.executeQuery()
            val result = mutableListOf<PriceHistoryEntry>()
            while (rs.next()) {
                result.add(
                    PriceHistoryEntry(
                        id = rs.getLong("id"),
                        productId = rs.getLong("productId"),
                        price = rs.getDouble("price"),
                        checkedAt = rs.getLong("checkedAt")
                    )
                )
            }
            result
        }
    }

    private fun java.sql.ResultSet.toProduct() = Product(
        id = getLong("id"),
        url = getString("url"),
        alias = getString("alias"),
        name = getString("name"),
        imageUrl = getString("imageUrl"),
        storeName = getString("storeName"),
        currentPrice = getDouble("currentPrice"),
        previousPrice = getObject("previousPrice") as? Double,
        currencySymbol = getString("currencySymbol"),
        lastCheckedAt = getLong("lastCheckedAt"),
        inStock = runCatching { getInt("inStock") != 0 }.getOrDefault(true),
        outOfStockKeyword = runCatching { getString("outOfStockKeyword") }.getOrNull()
    )
}
