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
}
