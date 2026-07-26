package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.receipt.QrPayload
import by.mlastovsky.kosht.data.receipt.ReceiptQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptQrTest {

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
        // A slip may carry a loyalty code, a Wi-Fi password or an advert.
        assertNull(ReceiptQr.classify("WIFI:S=ShopGuest;T=WPA;P=secret;;"))
        assertNull(ReceiptQr.classify("mailto:shop@example.by"))
        assertNull(ReceiptQr.classify("1234567890128"))
        assertNull(ReceiptQr.classify("  "))
        // Key=value pairs alone are not enough without a sum and a time.
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
        // Script and style content must not leak in as fake receipt lines.
        assertTrue(!text.contains("track()"))
        assertTrue(!text.contains("color:red"))
    }
}
