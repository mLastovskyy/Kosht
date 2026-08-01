package by.mlastovsky.kosht.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.premiumStore: DataStore<Preferences> by preferencesDataStore(name = "premium")

/**
 * Kosht Premium lives in its own store and never travels to the account: an
 * entitlement belongs to the receipt the store issued, not to a synced
 * preference another device could copy. Until billing is wired up the flag is
 * only ever written by [setPremium], which the purchase flow will call.
 */
class PremiumRepository(private val context: Context) {

    val premium: Flow<Boolean> = context.premiumStore.data.map { it[Keys.premium] ?: false }

    suspend fun setPremium(value: Boolean) {
        context.premiumStore.edit { it[Keys.premium] = value }
    }

    private object Keys {
        val premium = booleanPreferencesKey("premium")
    }
}
