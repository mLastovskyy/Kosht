package by.mlastovsky.kosht.model

/**
 * How long Kosht may sit in the background before it asks for the code again,
 * in whole minutes — typed in rather than picked from a list, because the right
 * number is a matter of habit: one person photographs receipts for five minutes
 * at a time, another wants the code the instant the app leaves the screen.
 */
object LockTimeout {

    /** Ask the moment the app leaves the screen. */
    const val AT_ONCE = 0

    const val DEFAULT_MINUTES = 1

    /**
     * A day. Past that a lock would be one in name only, and a stray extra
     * digit should not quietly turn it off for a week.
     */
    const val MAX_MINUTES = 1_440

    fun sanitize(minutes: Int): Int = minutes.coerceIn(AT_ONCE, MAX_MINUTES)

    fun millis(minutes: Int): Long = sanitize(minutes) * 60_000L
}
