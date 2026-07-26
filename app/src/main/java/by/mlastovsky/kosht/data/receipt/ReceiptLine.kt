package by.mlastovsky.kosht.data.receipt

/**
 * One line of a receipt, with how prominently it was printed.
 *
 * [emphasis] is the size of the print relative to the body text: 1 is ordinary,
 * anything above it is the large type a shop sets its own name in. OCR measures
 * it from the height of the recognized line; an electronic receipt takes it
 * from the heading and bold tags. Reading a name off the biggest print is the
 * one cue that works when the wording is unfamiliar — which, for a shop nobody
 * has heard of, it always is.
 */
data class ReceiptLine(val text: String, val emphasis: Float = 1f) {

    companion object {
        /** Plain text with nothing known about how it looked. */
        fun of(text: String): List<ReceiptLine> = text.lines().map { ReceiptLine(it) }
    }
}
