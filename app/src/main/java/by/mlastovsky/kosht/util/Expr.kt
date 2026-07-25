package by.mlastovsky.kosht.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Tiny calculator for the amount keypad: digits, one decimal separator per
 * number and the operators + − × ÷ with the usual precedence.
 */
object Expr {

    private val operators = setOf('+', '−', '×', '÷', '-', '*', '/')

    fun hasPendingOperation(input: String): Boolean =
        input.drop(1).any { it in operators }

    /** Evaluates the expression; a trailing operator is ignored. */
    fun evaluate(input: String): BigDecimal? {
        val normalized = input
            .replace(',', '.')
            .replace('−', '-')
            .replace('×', '*')
            .replace('÷', '/')
            .trimEnd { it in "+-*/".toSet() }
        if (normalized.isBlank()) return null

        val tokens = tokenize(normalized) ?: return null
        return runCatching { evalTokens(tokens) }.getOrNull()
    }

    /** Minor units of the evaluated expression, honoring currency digits. */
    fun evaluateToMinor(input: String, currencyCode: String): Long? {
        val value = evaluate(input) ?: return null
        val digits = Money.fractionDigits(currencyCode)
        return runCatching {
            value.movePointRight(digits).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull()
    }

    private fun tokenize(s: String): List<String>? {
        val tokens = mutableListOf<String>()
        var number = StringBuilder()
        for (ch in s) {
            when {
                ch.isDigit() || ch == '.' -> number.append(ch)
                ch in "+-*/" -> {
                    if (number.isEmpty()) return null
                    tokens.add(number.toString())
                    number = StringBuilder()
                    tokens.add(ch.toString())
                }
                else -> return null
            }
        }
        if (number.isNotEmpty()) tokens.add(number.toString())
        return tokens.takeIf { it.isNotEmpty() && it.size % 2 == 1 }
    }

    private fun evalTokens(tokens: List<String>): BigDecimal {
        // First pass: * and /
        val flat = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token == "*" || token == "/") {
                val left = BigDecimal(flat.removeAt(flat.lastIndex))
                val right = BigDecimal(tokens[i + 1])
                val result = if (token == "*") {
                    left.multiply(right)
                } else {
                    left.divide(right, MathContext.DECIMAL64)
                }
                flat.add(result.toPlainString())
                i += 2
            } else {
                flat.add(token)
                i++
            }
        }
        // Second pass: + and -
        var acc = BigDecimal(flat[0])
        var j = 1
        while (j < flat.size) {
            val right = BigDecimal(flat[j + 1])
            acc = if (flat[j] == "+") acc.add(right) else acc.subtract(right)
            j += 2
        }
        return acc
    }
}
