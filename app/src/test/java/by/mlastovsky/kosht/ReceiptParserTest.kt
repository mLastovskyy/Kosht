package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.receipt.ReceiptParser
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
    fun `merchant is the first meaningful line`() {
        val text = """
            ООО «Евроторг»
            ИТОГО 5,00
        """.trimIndent()
        assertEquals("ООО «Евроторг»", ReceiptParser.parse(text).merchant)
    }

    @Test
    fun `no amounts means null total`() {
        assertNull(ReceiptParser.parse("просто текст без цифр").amountMinor)
    }
}
