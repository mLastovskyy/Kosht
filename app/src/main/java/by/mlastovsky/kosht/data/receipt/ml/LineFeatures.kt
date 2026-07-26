package by.mlastovsky.kosht.data.receipt.ml

class SparseVector(val indices: IntArray, val values: FloatArray)

object LineFeatures {

    const val DENSE = 16
    const val TOKEN_BUCKETS = 2048
    const val GRAM_BUCKETS = 4096

    const val TOKEN_BASE = 1 + DENSE
    const val GRAM_BASE = TOKEN_BASE + TOKEN_BUCKETS
    const val SIZE = GRAM_BASE + GRAM_BUCKETS

    private val amount = Regex("(?<!\\d)\\d{1,9}(?:[  ]\\d{3})*[.,]\\d{2}(?!\\d)")

    private val quantity = Regex("""[\dхx×*]\s*[хx×*]\s*\d""")

    private val currencyWords = listOf("byn", "руб", "р.", "br", "бр", "eur", "usd")

    fun of(text: String, emphasis: Float, index: Int, count: Int): SparseVector {
        val shape = normalized(text)
        val weights = HashMap<Int, Float>(64)
        weights[0] = 1f

        val letters = text.count { it.isLetter() }
        val digits = text.count { it in '0'..'9' }
        val upper = text.count { it.isUpperCase() }
        val amounts = amount.findAll(text).count()
        val lower = text.lowercase()
        val span = text.length.coerceAtLeast(1).toFloat()
        val position = if (count > 1) index.toFloat() / (count - 1) else 0f

        val dense = floatArrayOf(
            if (amounts > 0) 1f else 0f,
            (amounts / 3f).coerceAtMost(1f),
            digits / span,
            letters / span,
            if (letters > 0) upper.toFloat() / letters else 0f,
            (text.length / 40f).coerceAtMost(1f),
            position,
            position * position,
            if (index < 3) 1f else 0f,
            if (index >= count - 3) 1f else 0f,
            (emphasis - 1f).coerceIn(0f, 1f),
            if (':' in text) 1f else 0f,
            if ('%' in text) 1f else 0f,
            if (quantity.containsMatchIn(lower)) 1f else 0f,
            if (currencyWords.any { it in lower }) 1f else 0f,
            if (amount.find(text)?.range?.last == text.length - 1) 1f else 0f
        )
        dense.forEachIndexed { at, value -> if (value != 0f) weights[1 + at] = value }

        tokens(shape).forEach { token ->
            val slot = TOKEN_BASE + (hash(token) % TOKEN_BUCKETS)
            weights[slot] = (weights[slot] ?: 0f) + 1f
        }
        grams(shape).forEach { gram ->
            val slot = GRAM_BASE + (hash(gram) % GRAM_BUCKETS)
            weights[slot] = (weights[slot] ?: 0f) + 1f
        }

        val indices = weights.keys.toIntArray()
        indices.sort()
        return SparseVector(indices, FloatArray(indices.size) { weights.getValue(indices[it]) })
    }

    fun normalized(text: String): String {
        val builder = StringBuilder(text.length)
        text.lowercase().forEach { symbol ->
            builder.append(if (symbol in '0'..'9') '#' else symbol)
        }
        return builder.toString().replace(Regex("""\s+"""), " ").trim()
    }

    fun tokens(shape: String): List<String> = shape
        .split(Regex("""[^\p{L}#]+"""))
        .filter { it.length >= 2 }
        .take(24)

    fun grams(shape: String): List<String> {
        if (shape.length < 3) return emptyList()
        val padded = " $shape "
        return (0..padded.length - 3).map { padded.substring(it, it + 3) }.take(96)
    }

    fun hash(text: String): Int {
        var value = FNV_OFFSET
        text.toByteArray(Charsets.UTF_8).forEach { byte ->
            value = (value xor (byte.toLong() and 0xFFL)) * FNV_PRIME and 0xFFFFFFFFL
        }
        return (value and 0x7FFFFFFFL).toInt()
    }

    private const val FNV_OFFSET = 2166136261L
    private const val FNV_PRIME = 16777619L
}
