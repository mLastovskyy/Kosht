package by.mlastovsky.kosht.data.awards

import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.db.ChallengeEntity
import by.mlastovsky.kosht.data.db.RateEntity
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.model.ChallengeType
import java.time.LocalDate

enum class ChallengeStatus { ACTIVE, DONE, FAILED }

/** What one day of spending in one category came to. */
data class DaySpend(val day: LocalDate, val categoryId: Long, val minor: Long)

/** A challenge as it stands right now. Never stored — always recomputed. */
data class ChallengeProgress(
    val entity: ChallengeEntity,
    /** Spent/saved against the limit or target; elapsed days for NO_SPEND. */
    val progress: Float,
    val progressLabelMinor: Long,
    val daysPassed: Int,
    val daysTotal: Int,
    val status: ChallengeStatus
)

/**
 * Turns a challenge and the spending behind it into progress and a verdict.
 *
 * Pure on purpose: the achievements screen shows this, the award tracker
 * counts the finished ones, and a unit test can check the arithmetic without
 * a database or a clock.
 */
object ChallengeEvaluator {

    fun evaluate(
        challenge: ChallengeEntity,
        spend: List<DaySpend>,
        savings: List<SavingEntity>,
        rates: Map<String, RateEntity>,
        currencyCode: String,
        today: LocalDate = LocalDate.now()
    ): ChallengeProgress {
        val daysTotal = (challenge.endEpochDay - challenge.startEpochDay + 1).toInt()
        val daysPassed = (today.toEpochDay() - challenge.startEpochDay + 1)
            .coerceIn(0, daysTotal.toLong()).toInt()
        val periodOver = today.toEpochDay() > challenge.endEpochDay
        val inPeriod = { day: LocalDate ->
            day.toEpochDay() in challenge.startEpochDay..challenge.endEpochDay
        }

        return when (challenge.type) {
            ChallengeType.SPEND_LIMIT -> {
                val spent = spend
                    .filter { inPeriod(it.day) }
                    .filter { challenge.categoryId == null || it.categoryId == challenge.categoryId }
                    .sumOf { it.minor }
                ChallengeProgress(
                    entity = challenge,
                    progress = if (challenge.amountMinor > 0) {
                        (spent.toFloat() / challenge.amountMinor).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    progressLabelMinor = spent,
                    daysPassed = daysPassed,
                    daysTotal = daysTotal,
                    status = when {
                        spent > challenge.amountMinor -> ChallengeStatus.FAILED
                        periodOver -> ChallengeStatus.DONE
                        else -> ChallengeStatus.ACTIVE
                    }
                )
            }

            ChallengeType.NO_SPEND -> {
                val anyExpense = spend.any { inPeriod(it.day) && it.minor > 0 }
                ChallengeProgress(
                    entity = challenge,
                    progress = if (daysTotal > 0) daysPassed.toFloat() / daysTotal else 0f,
                    progressLabelMinor = 0,
                    daysPassed = daysPassed,
                    daysTotal = daysTotal,
                    status = when {
                        anyExpense -> ChallengeStatus.FAILED
                        periodOver -> ChallengeStatus.DONE
                        else -> ChallengeStatus.ACTIVE
                    }
                )
            }

            ChallengeType.SAVE_TARGET -> {
                // Savings keep their own currency, so both sides are compared
                // in BYN — the one currency every rate is published against.
                val savedByn = savings
                    .filter { it.amountMinor > 0 }
                    .filter { inPeriod(by.mlastovsky.kosht.util.Dates.toLocalDate(it.timestamp)) }
                    .sumOf {
                        RatesRepository.toBynMinor(it.amountMinor, it.currencyCode, rates) ?: 0L
                    }
                val targetByn = RatesRepository
                    .toBynMinor(challenge.amountMinor, currencyCode, rates)
                    ?: challenge.amountMinor
                ChallengeProgress(
                    entity = challenge,
                    progress = if (targetByn > 0) {
                        (savedByn.toFloat() / targetByn).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    progressLabelMinor = savedByn,
                    daysPassed = daysPassed,
                    daysTotal = daysTotal,
                    status = when {
                        savedByn >= targetByn -> ChallengeStatus.DONE
                        periodOver -> ChallengeStatus.FAILED
                        else -> ChallengeStatus.ACTIVE
                    }
                )
            }
        }
    }
}
