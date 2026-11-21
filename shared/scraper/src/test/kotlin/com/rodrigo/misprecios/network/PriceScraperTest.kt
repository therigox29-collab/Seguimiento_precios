package com.rodrigo.misprecios.network

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pruebas del scraper con HTML armado a mano que imita casos reales de tiendas.
 * Para agregar un caso de una tienda que falla: guardá el HTML de la página
 * (Ctrl+S en el navegador) y armá un test igual a estos.
 */
class PriceScraperTest {

    private fun scrape(
        html: String,
        url: String = "https://tienda.test/producto/zapatilla-x",
        selector: String? = null,
        api: Map<String, String> = emptyMap()
    ): ScrapedProduct = runBlocking {
        PriceScraper.extract(Jsoup.parse(html, url), url, null, selector) { api[it] }
    }

    @Test
    fun jsonLdConPrecioConPuntoDeMiles() {
        val p = scrape(
            """
            <html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Product","name":"Zapatilla X",
             "offers":{"@type":"Offer","price":"49.990","priceCurrency":"CLP",
                       "availability":"https://schema.org/InStock"}}
            </script></head><body><h1>Zapatilla X</h1></body></html>
            """
        )
        assertEquals(49990.0, p.price)
        assertEquals(PriceSource.JSON_LD, p.source)
    }

    @Test
    fun jsonLdPrecioVacioUsaPriceSpecificationSinPrecioDeLista() {
        val p = scrape(
            """
            <script type="application/ld+json">
            {"@type":"Product","name":"Zapatilla X","offers":{"@type":"Offer","price":"",
              "priceCurrency":"ARS","priceSpecification":[
                {"@type":"UnitPriceSpecification","priceType":"https://schema.org/ListPrice","price":150000},
                {"@type":"UnitPriceSpecification","price":119999}
              ]}}
            </script><h1>Zapatilla X</h1>
            """
        )
        assertEquals(119999.0, p.price)
    }

    @Test
    fun jsonLdAggregateOfferYGraphAnidado() {
        val p = scrape(
            """
            <script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[
              {"@type":"WebPage","mainEntity":{"@type":"Product","name":"Zapatilla X",
                "offers":{"@type":"AggregateOffer","lowPrice":"24.990","highPrice":"29.990","priceCurrency":"CLP"}}}
            ]}
            </script><h1>Zapatilla X</h1>
            """
        )
        assertEquals(24990.0, p.price)
    }

    @Test
    fun jsonLdConComaFinalYSaltosDeLinea() {
        val p = scrape(
            "<script type=\"application/ld+json\">{\"@type\":\"Product\",\"name\":\"Zapatilla\nX\"," +
                "\"offers\":{\"price\":1999.5,\"priceCurrency\":\"USD\",},}</script><h1>Zapatilla X</h1>"
        )
        assertEquals(1999.5, p.price)
        assertEquals("US$", p.currencySymbol)
    }

    @Test
    fun jsonLdIgnoraProductosRelacionados() {
        val p = scrape(
            """
            <script type="application/ld+json">
            {"@type":"ItemList","itemListElement":[
              {"@type":"Product","name":"Zapatilla X","offers":{"price":"39990"}},
              {"@type":"Product","name":"Medias Y","offers":{"price":"2990"}}
            ]}
            </script><h1>Zapatilla X</h1>
            """
        )
        assertEquals(39990.0, p.price)
    }

    @Test
    fun jsonLdProductGroupEligeVarianteDeLaUrl() {
        val p = scrape(
            """
            <script type="application/ld+json">
            {"@type":"ProductGroup","name":"Remera","hasVariant":[
              {"@type":"Product","sku":"111","offers":{"price":"10000"}},
              {"@type":"Product","sku":"222","offers":{"price":"12000"}}
            ]}
            </script><h1>Remera</h1>
            """,
            url = "https://tienda.test/products/remera?variant=222"
        )
        assertEquals(12000.0, p.price)
    }

    /** Caso real: motorola.cl (VTEX). Las dos variantes agotadas dentro de un AggregateOffer. */
    private val motorolaJsonLd = """
        <script type="application/ld+json">{"@context":"https://schema.org/","@type":"Product",
        "@id":"https://www.motorola.cl/moto-buds-loop-swarovski/p","name":"moto buds loop by Swarovski",
        "sku":"00000402","offers":{"@type":"AggregateOffer","lowPrice":99990,"highPrice":249990,
        "priceCurrency":"CLP","offers":[
          {"@type":"Offer","price":99990,"priceCurrency":"CLP","availability":"http://schema.org/OutOfStock","sku":"00000401"},
          {"@type":"Offer","price":249990,"priceCurrency":"CLP","availability":"http://schema.org/OutOfStock","sku":"00000402"}
        ],"offerCount":2}}</script>
        <h1>moto buds loop by Swarovski</h1>
    """

    @Test
    fun aggregateOfferConTodasLasVariantesAgotadas() {
        val p = scrape(motorolaJsonLd, url = "https://www.motorola.cl/moto-buds-loop-swarovski/p")
        assertFalse(p.inStock)
        assertEquals(99990.0, p.price)
    }

    @Test
    fun aggregateOfferUsaLaVarianteDelSkuIdDeLaUrl() {
        val p = scrape(
            motorolaJsonLd,
            url = "https://www.motorola.cl/moto-buds-loop-swarovski/p?skuId=402&srsltid=abc"
        )
        assertFalse(p.inStock)
        assertEquals(249990.0, p.price)
        assertEquals("$", p.currencySymbol)
    }

    @Test
    fun openGraphConNameEnLugarDeProperty() {
        val p = scrape(
            """
            <head>
              <meta name="og:title" content="Zapatilla X">
              <meta property="product:price:amount" content="1.299.990">
              <meta property="product:price:currency" content="CLP">
            </head><body></body>
            """
        )
        assertEquals(1299990.0, p.price)
        assertEquals(PriceSource.OPEN_GRAPH, p.source)
    }

    @Test
    fun heuristicaIgnoraTachadoCuotasYCarrito() {
        val p = scrape(
            """
            <header><div class="minicart-total">$0</div></header>
            <div class="banner">Envío gratis sobre $19.990</div>
            <h1>Zapatilla X</h1>
            <div class="product-prices">
              <span class="price old-price"><del>$59.990</del></span>
              <span class="price price--sale">$44.990</span>
              <span class="price installments-price">12 cuotas de $3.749</span>
            </div>
            <section class="related-products"><span class="price">$9.990</span></section>
            """
        )
        assertEquals(44990.0, p.price)
        assertEquals(PriceSource.HTML_HEURISTIC, p.source)
    }

    @Test
    fun patronDeTextoNoCortaNumerosLargos() {
        val p = scrape(
            """
            <p>Envío gratis desde $19990</p>
            <h1>Zapatilla X</h1>
            <p>Llevala hoy por $12990 en efectivo</p>
            """
        )
        assertEquals(12990.0, p.price)
        assertEquals(PriceSource.TEXT_PATTERN, p.source)
    }

    @Test
    fun selectorPersonalizadoManda() {
        val p = scrape(
            """
            <script type="application/ld+json">
            {"@type":"Product","name":"Zapatilla X","offers":{"price":"59990"}}
            </script>
            <h1>Zapatilla X</h1><div class="precio-tarjeta">$ 52.990</div>
            """,
            selector = ".precio-tarjeta"
        )
        assertEquals(52990.0, p.price)
        assertEquals("Zapatilla X", p.name)
        assertEquals(PriceSource.CUSTOM_SELECTOR, p.source)
    }

    @Test
    fun selectorQueNoEncuentraNadaDaError() {
        assertFailsWith<ScrapeException> {
            scrape("<h1>Zapatilla X</h1><p>$ 1.000</p>", selector = ".no-existe")
        }
    }

    @Test
    fun shopifyUsaLaApiDeProducto() {
        val p = scrape(
            """
            <script>window.Shopify = {}; Shopify.shop = "tienda.myshopify.com";
            Shopify.currency = {"active":"CLP","rate":"1.0"};</script>
            <h1>Remera</h1>
            """,
            url = "https://tienda.test/products/remera?variant=2",
            api = mapOf(
                "https://tienda.test/products/remera.js" to
                    """{"title":"Remera","available":true,"price":1000000,
                        "variants":[{"id":1,"price":1000000,"available":true},
                                    {"id":2,"price":1299000,"available":false}]}"""
            )
        )
        assertEquals(12990.0, p.price)
        assertFalse(p.inStock)
        assertEquals(PriceSource.SHOPIFY, p.source)
    }

    @Test
    fun vtexUsaLaApiDeCatalogo() {
        val p = scrape(
            """<link rel="stylesheet" href="https://tienda.vteximg.com.br/x.css"><h1>Notebook</h1>""",
            url = "https://tienda.test/notebook-15-pulgadas/p",
            api = mapOf(
                "https://tienda.test/api/catalog_system/pub/products/search/notebook-15-pulgadas/p" to
                    """[{"productName":"Notebook 15","items":[{"itemId":"1","images":[{"imageUrl":"https://img/1.jpg"}],
                        "sellers":[{"commertialOffer":{"Price":0,"AvailableQuantity":0}},
                                   {"commertialOffer":{"Price":549990.0,"AvailableQuantity":3}}]}]}]"""
            )
        )
        assertEquals(549990.0, p.price)
        assertEquals("Notebook 15", p.name)
        assertEquals(PriceSource.VTEX, p.source)
    }

    @Test
    fun paginaDeAntiBotDaMensajeClaro() {
        val e = assertFailsWith<ScrapeException> {
            scrape("<html><head><title>Just a moment...</title></head><body>Checking your browser</body></html>")
        }
        assertTrue(e.message!!.contains("anti-bot"))
    }
}
