package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.receipt.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real Belarusian slips, transcribed line for line from photographs — the
 * layouts the parser actually meets, rather than the ones it would like to.
 *
 * The pre-denomination one is here on purpose: it is a genuine receipt that
 * cannot be read, and the reason is worth pinning down in a test rather than
 * rediscovering from a screenshot.
 */
class RealReceiptTest {

    /**
     * ЗАО ПРОДМАГ-37, Minsk, 21.03.2016 — before the 2016 denomination, so
     * every figure is whole roubles with no kopecks anywhere: `17300`, `278100`.
     */
    private val beforeDenomination = """
        ЗАО ПРОДМАГ-37
        ул.Долгобродская, 10/1
        Терминал №1                Отдел: 000001
        Веремей Ж.Н.          21-03-2016 21:53
        ЧЕК:1.478.518    ПРД ТРН:000000092947
                  Е/И*К-ВО   ЦЕНА  СТОИМОСТЬ
        Хлебцы хр.Др. Корнер Злаковый коктейль
        006027         шт*1.000   17300     17300
        Банан Cavendish 1 кг вес
        021034692      кг*0.742   31500     23400
        Мешки п-эт майка
        0001599        шт*1.000    1700      1700
        ТОВАРОВ В ЧЕКЕ: 19
                       К ОПЛАТЕ:........278100
        Включая налоги:
        НДС                 10%           11173
        НДС                 20%           25865
                 ОПЛАТА,VISA/MC:.......278100
        СПАСИБО ЗА ПОКУПКУ!
    """.trimIndent()

    /** The same shopping as a modern BYN slip prints it: roubles and kopecks. */
    private val afterDenomination = """
        ЗАО ПРОДМАГ-37
        ул.Долгобродская, 10/1
        Терминал №1                Отдел: 000001
        Веремей Ж.Н.          21-03-2026 21:53
        ЧЕК:1.478.518    ПРД ТРН:000000092947
                  Е/И*К-ВО   ЦЕНА  СТОИМОСТЬ
        Хлебцы хрустящие Злаковый коктейль
        006027         шт*1.000   1,73      1,73
        Банан Cavendish 1 кг вес
        021034692      кг*0.742   3,15      2,34
        Мешки п-эт майка
        0001599        шт*1.000   0,17      0,17
        ТОВАРОВ В ЧЕКЕ: 3
                       К ОПЛАТЕ:........4,24
        Включая налоги:
        НДС                 20%           0,71
        СПАСИБО ЗА ПОКУПКУ!
    """.trimIndent()

    @Test
    fun `a pre-denomination slip has no amount to find`() {
        // Nothing on it is written as roubles and kopecks, and treating bare
        // integers as money would turn every article code and quantity on a
        // modern receipt into a price. So this one is honestly unreadable.
        assertNull(ReceiptParser.parse(beforeDenomination).amountMinor)
    }

    @Test
    fun `the same slip in today's format is read in full`() {
        val parsed = ReceiptParser.parse(afterDenomination)
        assertEquals(424L, parsed.amountMinor)
        assertEquals("Продмаг-37", parsed.merchant)
        assertTrue("expected items, got ${parsed.items}", parsed.items.size >= 2)
        // The line total, not the unit price: 0,742 kg of bananas cost 2,34.
        val banana = parsed.items.firstOrNull { it.name.contains("Банан") }
        assertEquals(234L, banana?.amountMinor)
        // Taxes, totals and the item count are not purchases.
        assertTrue(parsed.items.none { it.name.contains("НДС") })
        assertTrue(parsed.items.none { it.name.contains("ТОВАРОВ") })
    }

    @Test
    fun `the date on a real slip is read from its own format`() {
        // Printed with dashes rather than dots, which the app also accepts.
        assertEquals(2026, ReceiptParser.parse(afterDenomination).date?.year)
    }
}
