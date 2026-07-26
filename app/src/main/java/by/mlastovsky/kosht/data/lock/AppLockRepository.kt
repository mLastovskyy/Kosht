package by.mlastovsky.kosht.data.lock

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import by.mlastovsky.kosht.model.LockTimeout
import by.mlastovsky.kosht.util.Pin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lockStore: DataStore<Preferences> by preferencesDataStore(name = "app_lock")

data class AppLockSettings(

    val pinHash: String?,
    val pinSalt: String?,

    val pinLength: Int,

    val biometrics: Boolean,

    val timeoutMinutes: Int,

    val failedAttempts: Int,

    val lockedOutUntil: Long
) {
    val enabled: Boolean get() = pinHash != null && pinSalt != null

    val timeoutMillis: Long get() = LockTimeout.millis(timeoutMinutes)
}

class AppLockRepository(private val context: Context) {

    private object Keys {
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val pinLength = intPreferencesKey("pin_length")
        val biometrics = booleanPreferencesKey("biometrics")
        val timeoutMinutes = intPreferencesKey("timeout_minutes")
        val failedAttempts = intPreferencesKey("failed_attempts")
        val lockedOutUntil = longPreferencesKey("locked_out_until")
    }

    val settings: Flow<AppLockSettings> = context.lockStore.data.map { prefs ->
        AppLockSettings(
            pinHash = prefs[Keys.pinHash]?.takeIf { it.isNotBlank() },
            pinSalt = prefs[Keys.pinSalt]?.takeIf { it.isNotBlank() },
            pinLength = prefs[Keys.pinLength] ?: Pin.MIN_LENGTH,
            biometrics = prefs[Keys.biometrics] ?: false,
            timeoutMinutes = LockTimeout.sanitize(
                prefs[Keys.timeoutMinutes] ?: LockTimeout.DEFAULT_MINUTES
            ),
            failedAttempts = prefs[Keys.failedAttempts] ?: 0,
            lockedOutUntil = prefs[Keys.lockedOutUntil] ?: 0L
        )
    }

    suspend fun setPin(hash: String, salt: String, length: Int) {
        context.lockStore.edit { prefs ->
            prefs[Keys.pinHash] = hash
            prefs[Keys.pinSalt] = salt
            prefs[Keys.pinLength] = length
            prefs[Keys.failedAttempts] = 0
            prefs[Keys.lockedOutUntil] = 0L
        }
    }

    suspend fun clear() {
        context.lockStore.edit { prefs ->
            prefs.remove(Keys.pinHash)
            prefs.remove(Keys.pinSalt)
            prefs.remove(Keys.pinLength)
            prefs[Keys.biometrics] = false
            prefs[Keys.failedAttempts] = 0
            prefs[Keys.lockedOutUntil] = 0L
        }
    }

    suspend fun setBiometrics(enabled: Boolean) {
        context.lockStore.edit { it[Keys.biometrics] = enabled }
    }

    suspend fun setTimeoutMinutes(minutes: Int) {
        context.lockStore.edit { it[Keys.timeoutMinutes] = LockTimeout.sanitize(minutes) }
    }

    suspend fun recordFailure(now: Long) {
        context.lockStore.edit { prefs ->
            val failures = (prefs[Keys.failedAttempts] ?: 0) + 1
            prefs[Keys.failedAttempts] = failures
            val penalty = Pin.penaltyMillis(failures)
            if (penalty > 0) prefs[Keys.lockedOutUntil] = now + penalty
        }
    }

    suspend fun clearFailures() {
        context.lockStore.edit { prefs ->
            prefs[Keys.failedAttempts] = 0
            prefs[Keys.lockedOutUntil] = 0L
        }
    }
}
