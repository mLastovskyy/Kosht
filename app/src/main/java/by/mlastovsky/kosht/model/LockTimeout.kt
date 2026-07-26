package by.mlastovsky.kosht.model

object LockTimeout {

    const val AT_ONCE = 0

    const val DEFAULT_MINUTES = 1

    const val MAX_MINUTES = 1_440

    fun sanitize(minutes: Int): Int = minutes.coerceIn(AT_ONCE, MAX_MINUTES)

    fun millis(minutes: Int): Long = sanitize(minutes) * 60_000L
}
