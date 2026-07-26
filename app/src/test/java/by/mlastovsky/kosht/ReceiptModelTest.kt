package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.receipt.ReceiptLine
import by.mlastovsky.kosht.data.receipt.ReceiptParser
import by.mlastovsky.kosht.data.receipt.ml.LineFeatures
import by.mlastovsky.kosht.data.receipt.ml.LineKind
import by.mlastovsky.kosht.data.receipt.ml.LineModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptModelTest {

    private val model: LineModel = File("src/main/assets/" + LineModel.ASSET)
        .inputStream()
        .use { requireNotNull(LineModel.read(it)) { "the trained model is missing" } }

    private val slip = """
        ООО "Виталюр"
        УНП 190239501
        г. Минск, ул. Кульман, 1
        Кассир: Петров А.С.
        26.07.2026 18:02
        Молоко Савушкин 3,2% 1л            2,45
        Сыр Тильзитер 45% 200г             7,30
        Пакет майка                        0,17
        ОПЛАЧЕНО КАРТОЙ                    9,92
        НДС 20%                            1,65
        СПАСИБО ЗА ПОКУПКУ
    """.trimIndent().lines()

    @Test
    fun `the hash matches the one the trainer used`() {
        // Same FNV-1a as scripts/train-receipt-model.py; if these drift apart
        // every weight in the shipped model points at the wrong feature.
        assertEquals(1052510047, LineFeatures.hash("итого"))
        assertEquals(806842020, LineFeatures.hash("молоко"))
        assertEquals(433749208, LineFeatures.hash(" ит"))
    }

    @Test
    fun `digits are shaped away so prices do not become their own words`() {
        assertEquals("молоко #,# % #л", LineFeatures.normalized("Молоко 3,2 % 1л"))
    }

    @Test
    fun `the model tells purchases from the paperwork around them`() {
        val kinds = slip.mapIndexed { index, text ->
            text to model.chances(LineFeatures.of(text, 1f, index, slip.size))
                .maxByOrNull { it.value }!!.key
        }.toMap()

        assertEquals(LineKind.MERCHANT, kinds.getValue("ООО \"Виталюр\""))
        assertEquals(LineKind.ITEM, kinds.getValue("Молоко Савушкин 3,2% 1л            2,45"))
        assertEquals(LineKind.ITEM, kinds.getValue("Пакет майка                        0,17"))
        assertEquals(LineKind.OTHER, kinds.getValue("НДС 20%                            1,65"))
        assertEquals(LineKind.OTHER, kinds.getValue("УНП 190239501"))
    }

    @Test
    fun `a slip with no total keyword still gives up its amount`() {
        // Nothing says "итого" here, so the heuristics have nothing to grab and
        // the model has to point at the line that settles the bill.
        val parsed = ReceiptParser.parse(slip.map { ReceiptLine(it) }, model)
        assertEquals(992L, parsed.amountMinor)
        assertNotNull(parsed.merchant)
        assertTrue(parsed.items.none { it.name.contains("НДС") })
        assertTrue(parsed.items.any { it.name.contains("Молоко") })
    }

    @Test
    fun `the parser without a model is left exactly as it was`() {
        val plain = ReceiptParser.parse(slip.map { ReceiptLine(it) })
        val judged = ReceiptParser.parse(slip.map { ReceiptLine(it) }, model)
        assertEquals(plain.date, judged.date)
    }
}
