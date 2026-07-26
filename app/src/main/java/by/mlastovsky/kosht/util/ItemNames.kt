package by.mlastovsky.kosht.util

object ItemNames {

    const val MAX_LENGTH = 60

    fun normalize(raw: String): String? {
        val collapsed = collapse(raw).take(MAX_LENGTH).trimEnd()
        if (collapsed.none { it.isLetterOrDigit() }) return null
        return collapsed.lowercase().replaceFirstChar { it.uppercaseChar() }
    }

    fun key(raw: String): String =
        collapse(raw).take(MAX_LENGTH).trimEnd().lowercase().replace('ё', 'е')

    private fun collapse(raw: String): String =
        raw.trim().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("""\s+""")
}
