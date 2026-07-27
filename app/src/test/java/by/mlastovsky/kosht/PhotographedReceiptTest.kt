package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.receipt.ReceiptLine
import by.mlastovsky.kosht.data.receipt.ReceiptParser
import by.mlastovsky.kosht.data.receipt.ml.LineModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotographedReceiptTest {

    private val model: LineModel = File("src/main/assets/" + LineModel.ASSET)
        .inputStream()
        .use { requireNotNull(LineModel.read(it)) { "the trained model is missing" } }

    private val oplatiSanta = """
        ОПЛАТИ
        Общество с ограниченной
        ответственностью "Санта Ритейл"
        ПРОДАЖА
        ЧЕК №: 183
        Дата: 26.07.2026 22:19:26
        УНП: 291313486
        Смена: 1097
        --------------------------------
        1. Крабовые палочки (имит) Санта
        Бремор Ролл с яйцом и зеленью
        180 г полим/уп
        3.99 × 1.000                3.99
        2. Десерт ТОП МАНДАРИН-СЛИВКИ
        ваф стак 70 г флоу-пак
        1.53 × 1.000                1.53
        3. Молоко Савушкин ультрапастер
        1,5% 1 л пэт/бут
        2.40 × 1.000                2.40
        4. Лапша Роллтон с говядиной по
        домашнему 85 г флоу-пак
        1.79 × 1.000                1.79
        5. Бедро цыпленка-бройлера Ганна
        Аппетитное в/к охл вес 1 кг в/уп
        17.39 × 0.555               9.65
        6. Батончик шоколадный Milky Way
        Клубничный коктейль 26 г
        флоу-пак
        1.38 × 1.000                1.38
        --------------------------------
        ИТОГО:                     20.74
           Перевод Оплати           20.74
        Номер платежа: 238626342
    """.trimIndent()

    private val kopeechka = """
        Магазин "Копеечка"
        г. Минск, б-р Шевченко, 24-1Н
        ****************************************
        Оставьте свой отзыв об обслуживании
        +375-29-144-70-00 (Viber)
        ****************************************
        Касса 21
        УНП 191178504            РН СККО 119081617
                  ПЛАТЕЖНЫЙ ДОКУМЕНТ
                    № ДОК. 23489
        2245761 Чипсы"LAY S"(рифл. колб. пепперони)125г
        4690388119492
                    5.99      x1.000        5.99
        - - - - - - - - - - - - - - - - - - - -
        ИТОГО                         5.99
        ИТОГО К ОПЛАТЕ                5.99
        БАНК.КАРТА                    5.99
        Кассир Дир. Романюк Татьяна  27.07.2026 19:31:55
                УИ 814EFF3C1D63D7C307190A91
    """.trimIndent()

    private val euroopt = """
        ООО "ЕВРОТОРГ", Магазин "ЕВРООПТ"
        г.Минск, ул.В.Хоружей,д.31,пом.1Н
        Режим работы с 9.00 до 22.00
        Касса 3
        УНП 101168731            РН СККО 119055417
              НЕ ЯВЛЯЕТСЯ ПЛАТЕЖНЫМ ДОКУМЕНТОМ
        Платежный документ № док.00046222
        Чек продажи 342-3-1523263
        --------------------------------------
        Итого к оплате                    1.65
        --------------------------------------
        Спасибо, что пользуетесь электронными
        чеками. Для просмотра вашего электронного
        чека отсканируйте QR код:
    """.trimIndent()

    @Test
    fun `an Oplati slip gives up every line it lists`() {
        val parsed = ReceiptParser.parse(oplatiSanta)
        assertEquals(2074L, parsed.amountMinor)
        assertEquals("Санта", parsed.merchant)
        assertEquals(
            listOf(399L, 153L, 240L, 179L, 965L, 138L),
            parsed.items.map { it.amountMinor }
        )
        assertEquals("Крабовые палочки (имит) Санта", parsed.items[0].name)
        assertEquals("Молоко Савушкин ультрапастер", parsed.items[2].name)
        assertEquals("Батончик шоколадный Milky Way", parsed.items[5].name)
    }

    @Test
    fun `a weighed line keeps its own weight, not the price beside it`() {
        val chicken = ReceiptParser.parse(oplatiSanta).items[4]
        assertEquals("Бедро цыпленка-бройлера Ганна", chicken.name)
        assertEquals(965L, chicken.amountMinor)
        assertEquals(0.555, chicken.quantity!!, 0.001)
    }

    @Test
    fun `a single item over its own barcode is still one purchase`() {
        val parsed = ReceiptParser.parse(kopeechka)
        assertEquals(599L, parsed.amountMinor)
        assertEquals("Копеечка", parsed.merchant)
        assertEquals(1, parsed.items.size)
        assertEquals(599L, parsed.items[0].amountMinor)
        assertTrue(
            "expected the chips, got ${parsed.items[0].name}",
            parsed.items[0].name.startsWith("Чипсы")
        )
        assertEquals(null, parsed.items[0].quantity)
    }

    @Test
    fun `the trained model keeps the same lines the rules found`() {
        listOf(oplatiSanta to 6, kopeechka to 1, euroopt to 0).forEach { (slip, expected) ->
            val judged = ReceiptParser.parse(slip.lines().map { ReceiptLine(it) }, model)
            val plain = ReceiptParser.parse(slip)
            assertEquals(plain.amountMinor, judged.amountMinor)
            assertEquals(expected, judged.items.size)
        }
    }

    @Test
    fun `a slip that lists nothing gives only its total`() {
        val parsed = ReceiptParser.parse(euroopt)
        assertEquals(165L, parsed.amountMinor)
        assertEquals("Евроопт", parsed.merchant)
        assertEquals(emptyList<Any>(), parsed.items)
    }
}
