package by.mlastovsky.kosht.util

/**
 * How long the note on a record may be — one number for the field the user
 * types in and for everything the app writes into it by itself.
 */
object Notes {

    /** Everything a note field accepts; longer input is simply not taken. */
    const val MAX_LENGTH = 200

    /**
     * How long a shop name read off a receipt may be. Well inside
     * [MAX_LENGTH], because a line longer than this is not a name at all —
     * and a note that would not fit is left empty rather than cut in half.
     */
    const val MAX_SCANNED_LENGTH = 40
}
