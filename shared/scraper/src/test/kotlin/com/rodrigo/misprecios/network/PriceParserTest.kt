package com.rodrigo.misprecios.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriceParserTest {

    @Test
    fun puntoDeMilesNoSeConfundeConDecimal() {
        assertEquals(9990.0, PriceParser.parse("9.990"))
        assertEquals(9990.0, PriceParser.parse("$9.990"))
        assertEquals(1299990.0, PriceParser.parse("$ 1.299.990"))
        assertEquals(1299.0, PriceParser.parse("1,299"))
    }

    @Test
    fun decimales() {
        assertEquals(19.99, PriceParser.parse("19,99"))
        assertEquals(19.99, PriceParser.parse("US$ 19.99"))
        assertEquals(1299.99, PriceParser.parse("1,299.99"))
        assertEquals(1299.99, PriceParser.parse("1.299,99"))
        assertEquals(9990.0, PriceParser.parse("9990.00"))
        assertEquals(0.99, PriceParser.parse("0,990"))
    }

    @Test
    fun espaciosComoSeparadorDeMiles() {
        assertEquals(1299.0, PriceParser.parse("1 299,00 €"))
        assertEquals(1299.0, PriceParser.parse("1 299 €"))
    }

    @Test
    fun numerosLargosSinSeparador() {
        assertEquals(12990.0, PriceParser.parse("$12990"))
    }

    @Test
    fun valoresInvalidos() {
        assertNull(PriceParser.parse(""))
        assertNull(PriceParser.parse("Consultar precio"))
        assertNull(PriceParser.parse("0"))
        assertNull(PriceParser.parseAny(null))
        assertNull(PriceParser.parseAny(""))
    }

    @Test
    fun numerosDeJson() {
        assertEquals(9990.0, PriceParser.parseAny(9990))
        assertEquals(19.5, PriceParser.parseAny(19.5))
        assertEquals(9990.0, PriceParser.parseAny("9990"))
    }

    @Test
    fun detectaMoneda() {
        assertEquals("USD", PriceParser.detectCurrencyCode("US$ 19,99"))
        assertEquals("BRL", PriceParser.detectCurrencyCode("R$ 59,90"))
        assertEquals("EUR", PriceParser.detectCurrencyCode("19,99 €"))
        assertNull(PriceParser.detectCurrencyCode("$ 9.990"))
    }

    @Test
    fun guardDeCambiosBruscos() {
        assertTrue(PriceChangeGuard.isSuspicious(9990.0, 9.99))   // lectura de miles mal
        assertTrue(PriceChangeGuard.isSuspicious(100.0, 400.0))
        assertFalse(PriceChangeGuard.isSuspicious(9990.0, 7990.0)) // oferta normal
        assertFalse(PriceChangeGuard.isSuspicious(100.0, 150.0))
    }
}
