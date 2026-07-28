package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.receipt.PageRender
import by.mlastovsky.kosht.data.receipt.QrPayload
import by.mlastovsky.kosht.data.receipt.ReceiptParser
import by.mlastovsky.kosht.data.receipt.ReceiptQr
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptQrTest {

    @Test
    fun `a page that carries its receipt in a script is read anyway`() {
        val html = """
            <html><head><title>Электронный чек</title></head>
            <body><div id="app">Загрузка…</div>
            <script type="application/json">
            {"shop":{"name":"Магазин \"Маяк\""},
             "total":"21.84",
             "items":[{"name":"Хлеб Нарочанский","sum":"1.89","quantity":1},
                      {"name":"Молоко Савушкин 3,2% 1л","sum":"2.45","quantity":1},
                      {"name":"Сыр Тильзитер 45% 200г","sum":"17.50","quantity":1}]}
            </script></body></html>
        """.trimIndent()

        val parsed = ReceiptParser.parse(ReceiptQr.linesFromHtml(html))

        assertEquals(2184L, parsed.amountMinor)
        assertEquals(3, parsed.items.size)
        assertTrue(parsed.items.any { it.name.contains("Нарочанский") })
        assertEquals(2184L, parsed.items.sumOf { it.amountMinor })
    }

    @Test
    fun `a date on a page is no reason to stop reading its script`() {
        val html = """
            <html><body>
            <div>Электронный чек от 26.07.2026</div>
            <div id="app">Загрузка…</div>
            <script type="application/json">
            {"seller":{"name":"ООО \"Санта Ритейл\""},
             "topay":"9.30",
             "items":[{"name":"Кефир Бабушкина крынка 1%","sum":"1.95"},
                      {"name":"Печенье Слодыч 450г","sum":"7.35"}]}
            </script></body></html>
        """.trimIndent()

        val parsed = ReceiptParser.parse(ReceiptQr.linesFromHtml(html))

        assertEquals(930L, parsed.amountMinor)
        assertEquals(2, parsed.items.size)
        assertEquals("Санта", parsed.merchant)
    }

    @Test
    fun `a page shows its receipt only when real figures are on it`() {
        assertTrue(ReceiptQr.showsReceipt("<p>Хлеб 1,89</p><p>ИТОГО 4,34</p>"))
        assertTrue(!ReceiptQr.showsReceipt("<p>Чек от 26.07.2026 19:42</p><div>Загрузка…</div>"))
    }

    @Test
    fun `a kept page carries no script and remembers where it came from`() {
        val html = "<html><head><title>Чек</title></head>" +
            "<body><p>Хлеб 1,89</p><script>track()</script></body></html>"

        val kept = PageRender.keepable(html, "https://echeck.example.by/r/8812ab")

        assertTrue(kept.contains("""<base href="https://echeck.example.by/r/8812ab">"""))
        assertTrue(!kept.contains("track()"))
        assertTrue(kept.contains("Хлеб 1,89"))
    }

    @Test
    fun `a page that shows its figures is read from what it shows`() {
        val html = """
            <html><body>
            <h1>Магазин "Маяк"</h1>
            <table>
              <tr><td>Хлеб Нарочанский</td><td>1,89</td></tr>
              <tr><td>Молоко Савушкин</td><td>2,45</td></tr>
            </table>
            <p>Итого к оплате 4,34</p>
            <script>window.junk = {"total":"999.99"}</script>
            </body></html>
        """.trimIndent()

        val parsed = ReceiptParser.parse(ReceiptQr.linesFromHtml(html))

        assertEquals(434L, parsed.amountMinor)
        assertEquals("Маяк", parsed.merchant)
    }

    @Test
    fun `a link on a receipt is something to follow`() {
        val payload = ReceiptQr.classify("https://echeck.example.by/r/8812ab")
        assertEquals(QrPayload.Link("https://echeck.example.by/r/8812ab"), payload)
    }

    @Test
    fun `fiscal fields are read straight out of the code`() {
        val payload = ReceiptQr.classify("t=20260726T1930&s=12.50&fn=99&i=1234&fp=567&n=1")
        assertTrue(payload is QrPayload.Fields)
        val parsed = ReceiptQr.fromFields((payload as QrPayload.Fields).values)!!
        assertEquals(1250L, parsed.amountMinor)
        assertEquals(LocalDate.of(2026, 7, 26), parsed.date)
    }

    @Test
    fun `amounts arrive with one decimal, two, or none at all`() {
        fun minorOf(sum: String): Long? {
            val payload = ReceiptQr.classify("t=20260726T1930&s=$sum") as QrPayload.Fields
            return ReceiptQr.fromFields(payload.values)?.amountMinor
        }
        assertEquals(1250L, minorOf("12.50"))
        assertEquals(1250L, minorOf("12,5"))
        assertEquals(125000L, minorOf("1250"))
    }

    @Test
    fun `codes that are not receipts are left alone`() {

        assertNull(ReceiptQr.classify("WIFI:S=ShopGuest;T=WPA;P=secret;;"))
        assertNull(ReceiptQr.classify("mailto:shop@example.by"))
        assertNull(ReceiptQr.classify("1234567890128"))
        assertNull(ReceiptQr.classify("  "))

        assertNull(ReceiptQr.classify("card=771231&bonus=40"))
    }

    @Test
    fun `a fetched receipt page is flattened into parseable lines`() {
        val html = """
            <html><head><style>b{color:red}</style></head>
            <body><div>ООО &quot;Евроопт&quot;</div>
            <table><tr><td>Хлеб</td><td>1,89</td></tr>
            <tr><td>ИТОГО</td><td>15,40</td></tr></table>
            <script>track()</script></body></html>
        """.trimIndent()
        val text = ReceiptQr.textFromHtml(html)

        assertTrue(text.contains("ООО \"Евроопт\""))
        assertTrue(text.contains("ИТОГО"))

        assertTrue(!text.contains("track()"))
        assertTrue(!text.contains("color:red"))
    }
}
