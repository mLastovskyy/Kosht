package by.mlastovsky.kosht.data.receipt

data class ReceiptLine(val text: String, val emphasis: Float = 1f) {

    companion object {

        fun of(text: String): List<ReceiptLine> = text.lines().map { ReceiptLine(it) }
    }
}
