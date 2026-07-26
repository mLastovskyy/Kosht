package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.receipt.ReceiptLine
import by.mlastovsky.kosht.data.receipt.ReceiptParser
import by.mlastovsky.kosht.data.receipt.ReceiptQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {

    @Test
    fun `finds total by keyword on the same line`() {
        val text = """
            ЕВРООПТ
            Хлеб 2,10
            Молоко 3,45
            ИТОГО: 5,55
        """.trimIndent()
        assertEquals(555L, ReceiptParser.parse(text).amountMinor)
    }

    @Test
    fun `finds total on the next line after keyword`() {
        val text = """
            МАГАЗИН
            К ОПЛАТЕ
            123,40
        """.trimIndent()
        assertEquals(12340L, ReceiptParser.parse(text).amountMinor)
    }

    @Test
    fun `prefers keyword total over larger item price`() {
        val text = """
            Телевизор 999,99
            Скидка 900,00
            ИТОГО 99,99
        """.trimIndent()
        assertEquals(9999L, ReceiptParser.parse(text).amountMinor)
    }

    @Test
    fun `falls back to largest amount without keywords`() {
        val text = """
            Кофе 4,50
            Круассан 3,20
            7,70
        """.trimIndent()
        assertEquals(770L, ReceiptParser.parse(text).amountMinor)
    }

    @Test
    fun `parses date in dd_mm_yyyy format`() {
        val text = "ИТОГО 10,00\n15.03.2026 12:45"
        assertEquals(LocalDate.of(2026, 3, 15), ReceiptParser.parse(text).date)
    }

    @Test
    fun `ignores future dates`() {
        val text = "ИТОГО 10,00\n15.03.2099"
        assertNull(ReceiptParser.parse(text).date)
    }

    @Test
    fun `merchant drops the legal form and keeps the trade name`() {
        val text = """
            ООО «Евроторг»
            ИТОГО 5,00
        """.trimIndent()
        assertEquals("Евроторг", ReceiptParser.parse(text).merchant)
    }

    @Test
    fun `no amounts means null total`() {
        assertNull(ReceiptParser.parse("просто текст без цифр").amountMinor)
    }

    @Test
    fun `a date is not an amount`() {
        // 26.07.2026 must not be read as 26 rubles 07 kopecks.
        val text = """
            Магазин
            26.07.2026 19:30
            Хлеб 1,89
        """.trimIndent()
        assertEquals(189L, ReceiptParser.parse(text).amountMinor)
    }

    @Test
    fun `the VAT share on the total line is not the total`() {
        val text = """
            Продукты
            ИТОГО К ОПЛАТЕ 15,40 в т.ч. НДС 20% 2,57
        """.trimIndent()
        assertEquals(1540L, ReceiptParser.parse(text).amountMinor)
    }

    @Test
    fun `cash tendered and change are never the total`() {
        val text = """
            Товар 17,30
            ИТОГО 17,30
            НАЛИЧНЫМИ 50,00
            СДАЧА 32,70
        """.trimIndent()
        assertEquals(1730L, ReceiptParser.parse(text).amountMinor)
    }

    @Test
    fun `document headers are not mistaken for the shop`() {
        val text = """
            КАССОВЫЙ ЧЕК
            УНП 190237046
            г. Минск, ул. Притыцкого 156
            ООО "Виталюр"
            ИТОГО 8,20
        """.trimIndent()
        assertEquals("Виталюр", ReceiptParser.parse(text).merchant)
    }

    @Test
    fun `a known chain is recognised however the header is printed`() {
        val text = """
            КАССОВЫЙ ЧЕК
            ЧТУП "Евроторг" магазин Евроопт №312
            УНП 191234567
            ИТОГО 24,15
        """.trimIndent()
        val parsed = ReceiptParser.parse(text)
        assertEquals("Евроопт", parsed.merchant)
        assertEquals(2415L, parsed.amountMinor)
    }

    @Test
    fun `thousands separated by a space stay one amount`() {
        val text = "ИТОГО К ОПЛАТЕ 1 234,56"
        assertEquals(123456L, ReceiptParser.parse(text).amountMinor)
    }

    @Test
    fun `the largest print wins over the lines above it`() {
        // The name is rarely the first thing printed, but it is the biggest.
        val lines = listOf(
            ReceiptLine("Торговый центр Замок"),
            ReceiptLine("Хмельная лавка", emphasis = 2.2f),
            ReceiptLine("ИТОГО 12,40")
        )
        assertEquals("Хмельная лавка", ReceiptParser.parse(lines).merchant)
    }

    @Test
    fun `the name beside the tax number beats a plain header line`() {
        val lines = listOf(
            ReceiptLine("Добрый день"),
            ReceiptLine("УНП 191234567"),
            ReceiptLine("ЧТУП Смачны кут")
        )
        assertEquals("Смачны кут", ReceiptParser.parse(lines).merchant)
    }

    @Test
    fun `a footer never becomes the shop`() {
        val text = """
            СПАСИБО ЗА ПОКУПКУ
            Оплата картой BELKART
            www.example.by
            ИТОГО 4,00
        """.trimIndent()
        assertNull(ReceiptParser.parse(text).merchant)
    }

    @Test
    fun `punctuation soup from a bad scan is not a name`() {
        val text = """
            *** ~~ .:. ***
            |||| ---- ||||
            ИТОГО 9,10
        """.trimIndent()
        assertNull(ReceiptParser.parse(text).merchant)
    }

    @Test
    fun `a word that only means shop is not a name`() {
        val text = """
            МАГАЗИН
            К ОПЛАТЕ 6,50
        """.trimIndent()
        assertNull(ReceiptParser.parse(text).merchant)
    }

    @Test
    fun `a shouted name is written the way a note reads`() {
        val text = """
            ЧУП "СМАЧНАЯ ХАТА"
            ИТОГО 7,20
        """.trimIndent()
        assertEquals("Смачная Хата", ReceiptParser.parse(text).merchant)
    }

    @Test
    fun `an address is never the shop`() {
        val text = """
            г. Минск, ул. Немига 5
            ИП Ковалевич
            ИТОГО 3,15
        """.trimIndent()
        assertEquals("Ковалевич", ReceiptParser.parse(text).merchant)
    }

    @Test
    fun `a heading in an electronic receipt names the shop`() {
        val html = """
            <html><body>
            <div>Электронный чек</div>
            <h1>Ласунак</h1>
            <table><tr><td>Итого</td><td>18,90</td></tr></table>
            </body></html>
        """.trimIndent()
        val parsed = ReceiptParser.parse(ReceiptQr.linesFromHtml(html))
        assertEquals("Ласунак", parsed.merchant)
        assertEquals(1890L, parsed.amountMinor)
    }
}
