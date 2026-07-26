package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.db.RateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatesTest {

    private val rates = mapOf(
        "BYN" to RateEntity("BYN", 1, 1.0, 0),
        "USD" to RateEntity("USD", 1, 3.2, 0),
        "RUB" to RateEntity("RUB", 100, 3.7, 0)
    )

    @Test
    fun `byn converts to itself`() {
        assertEquals(12_34L, RatesRepository.toBynMinor(12_34L, "BYN", rates))
    }

    @Test
    fun `usd converts using official rate`() {
        // 10.00 USD * 3.2 = 32.00 BYN
        assertEquals(32_00L, RatesRepository.toBynMinor(10_00L, "USD", rates))
    }

    @Test
    fun `scaled currency divides by scale`() {
        // 100.00 RUB = 3.70 BYN (rate is per 100 units)
        assertEquals(3_70L, RatesRepository.toBynMinor(100_00L, "RUB", rates))
    }

    @Test
    fun `unknown currency returns null`() {
        assertNull(RatesRepository.toBynMinor(10_00L, "JPY", rates))
    }

    @Test
    fun `byn converts into a foreign currency`() {
        // 32.00 BYN / 3.2 = 10.00 USD
        assertEquals(10_00L, RatesRepository.convertMinor(32_00L, "BYN", "USD", rates))
    }

    @Test
    fun `two foreign currencies cross through byn`() {
        // 1.00 USD = 3.20 BYN = 86.49 RUB (3.7 BYN per 100 RUB)
        assertEquals(86_49L, RatesRepository.convertMinor(1_00L, "USD", "RUB", rates))
    }

    @Test
    fun `converting to the same currency changes nothing`() {
        assertEquals(7_77L, RatesRepository.convertMinor(7_77L, "USD", "USD", rates))
    }

    @Test
    fun `conversion needs both sides priced`() {
        assertNull(RatesRepository.convertMinor(1_00L, "USD", "JPY", rates))
        assertNull(RatesRepository.convertMinor(1_00L, "JPY", "USD", rates))
    }

    @Test
    fun `the factor is what one unit is worth in the other currency`() {
        // Switching the app from USD to BYN multiplies every amount by 3.2.
        assertEquals(3.2, RatesRepository.factor("USD", "BYN", rates)!!, 1e-9)
        assertEquals(1.0, RatesRepository.factor("USD", "USD", rates)!!, 1e-9)
        assertNull(RatesRepository.factor("USD", "JPY", rates))
    }

    @Test
    fun `a round trip through the factor comes back to the same amount`() {
        val toUsd = RatesRepository.factor("BYN", "USD", rates)!!
        val backToByn = RatesRepository.factor("USD", "BYN", rates)!!
        assertEquals(100_00L, Math.round(100_00L * toUsd * backToByn))
    }
}
