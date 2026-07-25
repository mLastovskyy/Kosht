package by.mlastovsky.kosht

import by.mlastovsky.kosht.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses plain integer input`() {
        assertEquals(1200L, Money.parseToMinor("12", "BYN"))
    }

    @Test
    fun `parses decimal input with dot`() {
        assertEquals(1250L, Money.parseToMinor("12.5", "BYN"))
        assertEquals(1255L, Money.parseToMinor("12.55", "USD"))
    }

    @Test
    fun `parses decimal input with comma`() {
        assertEquals(999L, Money.parseToMinor("9,99", "EUR"))
    }

    @Test
    fun `parses trailing separator as whole number`() {
        assertEquals(700L, Money.parseToMinor("7.", "BYN"))
    }

    @Test
    fun `returns null for blank or invalid input`() {
        assertNull(Money.parseToMinor("", "BYN"))
        assertNull(Money.parseToMinor("  ", "BYN"))
        assertNull(Money.parseToMinor("abc", "BYN"))
    }

    @Test
    fun `fraction digits default to two for unknown currency`() {
        assertEquals(2, Money.fractionDigits("XXXX"))
    }

    @Test
    fun `zero input parses to zero minor units`() {
        assertEquals(0L, Money.parseToMinor("0", "BYN"))
    }
}
