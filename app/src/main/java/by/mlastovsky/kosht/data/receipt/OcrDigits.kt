package by.mlastovsky.kosht.data.receipt

/**
 * Repairs the letters OCR mistakes digits for.
 *
 * A thermal-printed slip photographed by hand comes back with `1,4О` (Cyrillic
 * О), `l2,50`, `З,20` — the shapes are nearly identical and the recognizer has
 * no context to tell them apart. A receipt reader does have that context: a
 * token that ends in two decimals and is otherwise digits is a price, whatever
 * letter slipped into it.
 *
 * Deliberately narrow. Only tokens that already look like an amount are touched,
 * and only characters with a single unambiguous digit twin — so a product name
 * keeps its letters and nothing is "corrected" into a figure that was never
 * printed. This is what makes an unreadable receipt readable without sending it
 * anywhere: no service, no key, no model, just the shapes.
 */
object OcrDigits {

    /**
     * Letters that OCR returns instead of digits, Cyrillic and Latin alike.
     * Anything genuinely ambiguous (like `Ч` for 4, which is also a word) is
     * left out on purpose: a wrong figure is worse than a missing one.
     */
    private val lookalikes = mapOf(
        'O' to '0', 'o' to '0', 'О' to '0', 'о' to '0', 'Q' to '0', 'D' to '0',
        'l' to '1', 'I' to '1', '|' to '1', 'і' to '1', 'ӏ' to '1',
        'З' to '3', 'з' to '3',
        'A' to '4',
        'S' to '5', 's' to '5',
        'б' to '6', 'G' to '6',
        'T' to '7',
        'B' to '8', 'В' to '8',
        'g' to '9', 'q' to '9'
    )

    /**
     * A run of digits and lookalikes ending in a decimal separator and two more
     * of the same — in other words, something printed where a price goes.
     *
     * The guards on both sides are what keep `26.07.2026` a date rather than a
     * pair of prices: a token flanked by another figure or another separator is
     * part of something longer, and this only ever repairs a whole one.
     */
    private val amountish = Regex(
        "(?<![\\p{L}\\d])(?<![\\d][.,/\\-:])([\\dOoОоQDlI|іӏЗзASsбGTBВgq]{1,9})" +
            "[.,]([\\dOoОоQDlI|іӏЗзASsбGTBВgq]{2})(?![\\p{L}\\d.,/\\-:])"
    )

    /**
     * The line with its prices spelled in digits. Everything outside an
     * amount-shaped token is returned untouched, letters and all.
     */
    fun repair(line: String): String = amountish.replace(line) { match ->
        // A token without a single digit anywhere was never a price: "ОО,ОО"
        // is two pairs of letters, and reading it as 00,00 would be invention.
        // One real digit is enough of an anchor — "О,95" is plainly 0,95.
        if (match.value.none { it.isDigit() }) return@replace match.value
        val whole = match.groupValues[1].map { lookalikes[it] ?: it }.joinToString("")
        val fraction = match.groupValues[2].map { lookalikes[it] ?: it }.joinToString("")
        "$whole,$fraction"
    }

    /** Every line of a receipt, prices repaired, emphasis kept. */
    fun repair(lines: List<ReceiptLine>): List<ReceiptLine> =
        lines.map { it.copy(text = repair(it.text)) }
}
