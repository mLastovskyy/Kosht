package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.receipt.OcrDigits
import by.mlastovsky.kosht.data.receipt.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The letters a thermal slip comes back with instead of digits. The point of
 * these is the other half too: a name must keep its letters, and nothing that
 * was never a price may turn into one.
 */
class OcrDigitsTest {

    @Test
    fun `a Cyrillic O inside a price becomes a zero`() {
        assertEquals("Молоко 1,40", OcrDigits.repair("Молоко 1,4О"))
        assertEquals("Хлеб 0,95", OcrDigits.repair("Хлеб О,95"))
    }

    @Test
    fun `the usual suspects are all repaired`() {
        assertEquals("12,50", OcrDigits.repair("l2,5О"))
        assertEquals("3,20", OcrDigits.repair("З,2O"))
        assertEquals("6,85", OcrDigits.repair("б,85"))
        assertEquals("8,05", OcrDigits.repair("B,О5"))
    }

    @Test
    fun `a comma is what the app writes back`() {
        // Whatever separator the slip used, the repaired token is uniform.
        assertEquals("7,30", OcrDigits.repair("7.3О"))
    }

    @Test
    fun `words keep their letters`() {
        val line = "Кассир Ольга Ивановна"
        assertEquals(line, OcrDigits.repair(line))
    }

    @Test
    fun `a token that is only letters is never turned into a price`() {
        // "ОО,ОО" has no digit to anchor it: it was never an amount.
        assertEquals("ОО,ОО", OcrDigits.repair("ОО,ОО"))
    }

    @Test
    fun `a date is not an amount and is left alone`() {
        val line = "26.07.2026 15:59"
        assertEquals(line, OcrDigits.repair(line))
    }

    @Test
    fun `a receipt that was unreadable now parses`() {
        // Exactly the failure this exists for: every figure on the slip came
        // back with a letter in it, so nothing could be read at all.
        val text = """
            ЕВРООПТ
            Хлеб l,85
            Молоко 2,9О
            ИТОГО 4,75
        """.trimIndent()
        val parsed = ReceiptParser.parse(text)
        assertEquals(475L, parsed.amountMinor)
        assertEquals(2, parsed.items.size)
        assertEquals(185L, parsed.items[0].amountMinor)
        assertEquals(290L, parsed.items[1].amountMinor)
    }
}
