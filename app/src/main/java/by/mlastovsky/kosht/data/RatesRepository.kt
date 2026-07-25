package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.RateDao
import by.mlastovsky.kosht.data.db.RateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToLong

/**
 * Official daily exchange rates of the National Bank of Belarus.
 * Everything converts through BYN, the app's anchor currency.
 */
class RatesRepository(private val rateDao: RateDao) {

    /** code -> rate, refreshed reactively from the DB. */
    val rates: Flow<Map<String, RateEntity>> = rateDao.observeAll()
        .map { list -> list.associateBy { it.code } }

    suspend fun refreshIfStale(maxAgeMillis: Long = DEFAULT_MAX_AGE) {
        val last = rateDao.lastUpdatedAt() ?: 0L
        if (System.currentTimeMillis() - last > maxAgeMillis) {
            runCatching { refresh() }
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val connection = URL(NBRB_DAILY_RATES).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONArray(body)
            val now = System.currentTimeMillis()
            val rates = buildList {
                add(RateEntity(code = "BYN", scale = 1, rate = 1.0, updatedAt = now))
                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)
                    add(
                        RateEntity(
                            code = item.getString("Cur_Abbreviation"),
                            scale = item.getInt("Cur_Scale"),
                            rate = item.getDouble("Cur_OfficialRate"),
                            updatedAt = now
                        )
                    )
                }
            }
            rateDao.upsertAll(rates)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val NBRB_DAILY_RATES = "https://api.nbrb.by/exrates/rates?periodicity=0"
        private const val DEFAULT_MAX_AGE = 6 * 60 * 60 * 1000L

        /**
         * Converts an amount in minor units of [code] to minor units of BYN.
         * Returns null when the rate is unknown. Both currencies use 2 fraction
         * digits, so minor units convert 1:1 through the decimal rate.
         */
        fun toBynMinor(amountMinor: Long, code: String, rates: Map<String, RateEntity>): Long? {
            if (code == "BYN") return amountMinor
            val rate = rates[code] ?: return null
            if (rate.scale <= 0) return null
            return (amountMinor.toDouble() * rate.rate / rate.scale).roundToLong()
        }
    }
}
