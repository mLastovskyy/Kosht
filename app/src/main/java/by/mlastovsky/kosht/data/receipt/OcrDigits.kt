package by.mlastovsky.kosht.data.receipt

object OcrDigits {

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

    private val amountish = Regex(
        "(?<![\\p{L}\\d])(?<![\\d][.,/\\-:])([\\dOoОоQDlI|іӏЗзASsбGTBВgq]{1,9})" +
            "[.,]([\\dOoОоQDlI|іӏЗзASsбGTBВgq]{2})(?![\\p{L}\\d.,/\\-:])"
    )

    fun repair(line: String): String = amountish.replace(line) { match ->

        if (match.value.none { it.isDigit() }) return@replace match.value
        val whole = match.groupValues[1].map { lookalikes[it] ?: it }.joinToString("")
        val fraction = match.groupValues[2].map { lookalikes[it] ?: it }.joinToString("")
        "$whole,$fraction"
    }

    fun repair(lines: List<ReceiptLine>): List<ReceiptLine> =
        lines.map { it.copy(text = repair(it.text)) }
}
