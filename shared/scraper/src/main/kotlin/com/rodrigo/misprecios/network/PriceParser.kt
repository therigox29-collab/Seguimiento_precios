package com.rodrigo.misprecios.network

/**
 * Convierte textos de precio a número, entendiendo los formatos de distintos países:
 *
 *  "9.990"        -> 9990      (punto de miles, típico de Chile / Argentina)
 *  "1.299.990"    -> 1299990
 *  "1,299.99"     -> 1299.99   (formato EE.UU.)
 *  "1.299,99"     -> 1299.99   (formato europeo / latino)
 *  "19,99"        -> 19.99
 *  "$ 12990"      -> 12990
 *  "1 299,00 €"   -> 1299.0    (espacio como separador de miles)
 *
 * Regla para un único separador: si le siguen exactamente 3 dígitos es de miles
 * (ningún precio real tiene 3 decimales); si le siguen 1 o 2, es decimal.
 */
object PriceParser {

    // Primero intenta números con espacios como separador de miles ("1 299,00"), después
    // cualquier secuencia de dígitos con puntos y comas.
    private val NUMBER_TOKEN = Regex(
        "\\d{1,3}(?:[ \\u00A0\\u202F']\\d{3})+(?:[.,]\\d{1,2})?(?!\\d)|\\d[\\d.,]*"
    )

    /** Devuelve el primer precio válido (> 0) que encuentre en el texto, o null. */
    fun parse(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        for (match in NUMBER_TOKEN.findAll(text)) {
            val value = normalize(match.value) ?: continue
            if (value > 0) return value
        }
        return null
    }

    /** Igual que [parse] pero acepta valores que vienen como número desde un JSON. */
    fun parseAny(value: Any?): Double? = when (value) {
        null -> null
        is Number -> value.toDouble().takeIf { it > 0 && !it.isNaN() && !it.isInfinite() }
        else -> parse(value.toString())
    }

    private fun normalize(rawToken: String): Double? {
        val token = rawToken
            .replace(" ", "").replace(" ", "").replace(" ", "").replace("'", "")
            .trimEnd('.', ',')
        if (token.isEmpty()) return null

        val lastDot = token.lastIndexOf('.')
        val lastComma = token.lastIndexOf(',')

        val normalized = when {
            lastDot >= 0 && lastComma >= 0 -> {
                // Usa los dos: el que aparece último es el decimal.
                if (lastComma > lastDot) token.replace(".", "").replace(",", ".")
                else token.replace(",", "")
            }
            lastDot >= 0 -> normalizeSingleSeparator(token, '.')
            lastComma >= 0 -> normalizeSingleSeparator(token, ',')
            else -> token
        }
        return normalized.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    }

    private fun normalizeSingleSeparator(token: String, sep: Char): String {
        val occurrences = token.count { it == sep }
        if (occurrences > 1) return token.replace(sep.toString(), "")

        val idx = token.indexOf(sep)
        val integerPart = token.substring(0, idx)
        val decimals = token.length - idx - 1
        val isThousands = decimals == 3 && integerPart != "0" && integerPart.isNotEmpty()
        return if (isThousands) token.replace(sep.toString(), "")
        else token.replace(sep, '.')
    }

    // ---------------------------------------------------------------------------------
    // Monedas
    // ---------------------------------------------------------------------------------

    /** Símbolos/códigos que se reconocen en el texto, del más específico al más genérico. */
    private val TEXT_CURRENCY_MARKERS = listOf(
        "US$" to "USD", "U\$S" to "USD", "USD" to "USD",
        "R$" to "BRL", "BRL" to "BRL",
        "S/" to "PEN", "PEN" to "PEN",
        "CLP" to "CLP", "ARS" to "ARS", "MXN" to "MXN", "MX$" to "MXN",
        "COP" to "COP", "UYU" to "UYU", "\$U" to "UYU",
        "EUR" to "EUR", "€" to "EUR", "GBP" to "GBP", "£" to "GBP"
    )

    /** Intenta adivinar el código de moneda a partir de un texto de precio ("US$ 19,99"). */
    fun detectCurrencyCode(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val upper = text.uppercase()
        return TEXT_CURRENCY_MARKERS.firstOrNull { (marker, _) -> upper.contains(marker.uppercase()) }?.second
    }

    fun symbolFor(currencyCode: String?): String {
        if (currencyCode.isNullOrBlank()) return "$"
        return when (currencyCode.trim().uppercase()) {
            "USD" -> "US$"
            "EUR" -> "€"
            "ARS" -> "$"
            "MXN" -> "MX$"
            "CLP" -> "$"
            "COP" -> "$"
            "UYU" -> "\$U"
            "BRL" -> "R$"
            "PEN" -> "S/"
            "GBP" -> "£"
            "$", "€", "£" -> currencyCode.trim()
            else -> if (currencyCode.trim().length <= 3) currencyCode.trim() else "$"
        }
    }
}

/**
 * Decide si un cambio de precio es tan brusco que probablemente sea un error de lectura
 * (por ejemplo, la página mostró el precio de una cuota o de otro producto). En ese caso
 * conviene esperar a que el mismo precio se repita en la siguiente revisión antes de
 * aceptarlo y notificar.
 */
object PriceChangeGuard {
    /** Baja de más del 70 % o subida de más del 200 %. */
    fun isSuspicious(oldPrice: Double, newPrice: Double): Boolean {
        if (oldPrice <= 0 || newPrice <= 0) return false
        return newPrice < oldPrice * 0.30 || newPrice > oldPrice * 3.0
    }
}
