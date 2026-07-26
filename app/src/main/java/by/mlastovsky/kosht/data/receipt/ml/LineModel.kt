package by.mlastovsky.kosht.data.receipt.ml

import java.io.InputStream

enum class LineKind { TOTAL, ITEM, MERCHANT, OTHER }

class LineModel(private val weights: Map<LineKind, FloatArray>) {

    fun scores(vector: SparseVector): Map<LineKind, Float> = weights.mapValues { (_, row) ->
        var sum = 0f
        vector.indices.forEachIndexed { at, index ->
            if (index < row.size) sum += row[index] * vector.values[at]
        }
        sum
    }

    fun chances(vector: SparseVector): Map<LineKind, Float> {
        val raw = scores(vector)
        val top = raw.values.max()
        val exponents = raw.mapValues { (_, score) -> Math.exp((score - top).toDouble()) }
        val sum = exponents.values.sum()
        return exponents.mapValues { (_, value) -> (value / sum).toFloat() }
    }

    fun chanceOf(kind: LineKind, text: String, emphasis: Float, index: Int, count: Int): Float =
        chances(LineFeatures.of(text, emphasis, index, count))[kind] ?: 0f

    companion object {

        const val ASSET = "receipt-model.txt"

        private const val HEADER = "kosht-receipt-model"

        fun read(stream: InputStream): LineModel? = runCatching {
            val weights = mutableMapOf<LineKind, FloatArray>()
            stream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val head = line.substringBefore(' ')
                    when {
                        head == HEADER || head == "size" || head.isEmpty() -> Unit
                        else -> {
                            val kind = runCatching { LineKind.valueOf(head) }.getOrNull()
                                ?: return@forEach
                            weights[kind] = row(line.substringAfter(' '))
                        }
                    }
                }
            }
            if (weights.size == LineKind.entries.size) LineModel(weights) else null
        }.getOrNull()

        private fun row(body: String): FloatArray {
            val row = FloatArray(LineFeatures.SIZE)
            body.splitToSequence(' ').forEach { pair ->
                val at = pair.indexOf(':')
                if (at <= 0) return@forEach
                val index = pair.substring(0, at).toIntOrNull() ?: return@forEach
                val weight = pair.substring(at + 1).toFloatOrNull() ?: return@forEach
                if (index in row.indices) row[index] = weight
            }
            return row
        }
    }
}
