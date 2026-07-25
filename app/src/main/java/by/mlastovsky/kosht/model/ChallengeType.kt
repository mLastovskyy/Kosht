package by.mlastovsky.kosht.model

enum class ChallengeType {
    /** Spend no more than X (optionally in one category) during the period. */
    SPEND_LIMIT,

    /** No expenses at all during the period. */
    NO_SPEND,

    /** Set aside at least X during the period. */
    SAVE_TARGET
}
