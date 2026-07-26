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
    fun `a line too long to be a name leaves the note empty`() {
        // It reads like a name and nothing rejects it — except that no shop
        // prints its name across sixty characters, and a note cut in half is
        // worse than no note at all.
        val text = """
            Общество с ограниченной ответственностью Первая Столичная Торговая Компания Плюс
            ИТОГО 8,40
        """.trimIndent()
        assertNull(ReceiptParser.parse(text).merchant)
    }

    @Test
    fun `reads the shopping off the lines above the total`() {
        val text = """
            ЕВРООПТ
            Хлеб Бородинский 1,85
            Молоко 3,2% 2 x 1,45 2,90
            ИТОГО 4,75
        """.trimIndent()
        val items = ReceiptParser.parse(text).items
        assertEquals(2, items.size)
        assertEquals("Хлеб Бородинский", items[0].name)
        assertEquals(185L, items[0].amountMinor)
        assertNull(items[0].quantity)
        assertEquals("Молоко 3,2%", items[1].name)
        assertEquals(290L, items[1].amountMinor)
        assertEquals(2.0, items[1].quantity!!, 0.001)
    }

    @Test
    fun `a name printed above its own figures is one purchase`() {
        val text = """
            Санта
            Яблоки Джонаголд
            0,756 x 3,99 3,02
            ИТОГО 3,02
        """.trimIndent()
        val items = ReceiptParser.parse(text).items
        assertEquals(1, items.size)
        assertEquals("Яблоки Джонаголд", items[0].name)
        assertEquals(302L, items[0].amountMinor)
        assertEquals(0.756, items[0].quantity!!, 0.001)
    }

    @Test
    fun `discounts, cards and headers are not purchases`() {
        val text = """
            Наименование Кол-во Цена Сумма
            Кофе 6,90
            Скидка 1,00
            НДС 20% 1,15
            Оплата картой BELKART 5,90
            ИТОГО 5,90
        """.trimIndent()
        val items = ReceiptParser.parse(text).items
        assertEquals(1, items.size)
        assertEquals("Кофе", items[0].name)
    }

    @Test
    fun `nothing below the total counts as shopping`() {
        val text = """
            Печенье 2,30
            ИТОГО 2,30
            Сдача 7,70
            Внесено 10,00
        """.trimIndent()
        val items = ReceiptParser.parse(text).items
        assertEquals(1, items.size)
        assertEquals("Печенье", items[0].name)
    }

    @Test
    fun `lines adding up to far more than the total are not believed`() {
        // Something that is not a purchase was read as one. A wrong list is
        // worse than none, so the whole list goes rather than the total.
        // A misread digit turns 0,40 into 940,00 — the sort of thing OCR does
        // on a crumpled slip, and the sort of thing no filter can name.
        val text = """
            Хлеб 1,85
            Молоко 2,90
            Пакет майка 940,00
            ИТОГО 4,75
        """.trimIndent()
        val parsed = ReceiptParser.parse(text)
        assertEquals(475L, parsed.amountMinor)
        assertEquals(emptyList<Any>(), parsed.items)
    }

    @Test
    fun `lines adding up to less than the total are kept as they are`() {
        // One line the scan could not read is simply missing; no product is
        // invented to make the arithmetic come out even.
        val text = """
            Хлеб 1,85
            Молоко 2,90
            ИТОГО 9,20
        """.trimIndent()
        val parsed = ReceiptParser.parse(text)
        assertEquals(920L, parsed.amountMinor)
        assertEquals(2, parsed.items.size)
        assertEquals(475L, parsed.items.sumOf { it.amountMinor })
    }

    @Test
    fun `a discount below the items does not throw the list away`() {
        val text = """
            Кофе 6,90
            Печенье 3,10
            Скидка 1,00
            ИТОГО 9,00
        """.trimIndent()
        val parsed = ReceiptParser.parse(text)
        assertEquals(2, parsed.items.size)
    }

    @Test
    fun `a receipt with nothing readable lists no purchases`() {
        val text = """
            КАССОВЫЙ ЧЕК
            УНП 191234567
            К ОПЛАТЕ 12,00
        """.trimIndent()
        assertEquals(emptyList<Any>(), ReceiptParser.parse(text).items)
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
