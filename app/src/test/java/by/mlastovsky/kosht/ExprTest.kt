package by.mlastovsky.kosht

import by.mlastovsky.kosht.util.Expr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExprTest {

    @Test
    fun `plain number evaluates to minor units`() {
        assertEquals(1250L, Expr.evaluateToMinor("12.5", "BYN"))
    }

    @Test
    fun `addition sums operands`() {
        assertEquals(1590L, Expr.evaluateToMinor("12,5+3,4", "BYN"))
    }

    @Test
    fun `multiplication has precedence`() {
        assertEquals(700L, Expr.evaluateToMinor("1+2×3", "BYN"))
    }

    @Test
    fun `division works`() {
        assertEquals(500L, Expr.evaluateToMinor("10÷2", "BYN"))
    }

    @Test
    fun `trailing operator is ignored`() {
        assertEquals(1200L, Expr.evaluateToMinor("12+", "BYN"))
    }

    @Test
    fun `pending operation detection`() {
        assertTrue(Expr.hasPendingOperation("12+3"))
        assertFalse(Expr.hasPendingOperation("12.5"))
        assertFalse(Expr.hasPendingOperation(""))
    }

    @Test
    fun `invalid input returns null`() {
        assertNull(Expr.evaluateToMinor("", "BYN"))
        assertNull(Expr.evaluateToMinor("abc", "BYN"))
    }

    @Test
    fun `division by zero returns null`() {
        assertNull(Expr.evaluateToMinor("5÷0", "BYN"))
    }
}
