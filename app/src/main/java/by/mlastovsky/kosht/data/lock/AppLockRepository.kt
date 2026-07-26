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

/**
 * A store of its own, not a corner of "settings", and that is the point: the
 * lock belongs to this phone and must never travel to another one. The other
 * phone may have no fingerprint reader, may be in someone else's hands, and a
 * code that arrives over the network is a code its owner did not choose.
 */
private val Context.lockStore: DataStore<Preferences> by preferencesDataStore(name = "app_lock")

data class AppLockSettings(
    /** Base64 PBKDF2 digest of the code; null means there is no lock. */
    val pinHash: String?,
    val pinSalt: String?,
    /** How many dots the lock screen draws, so the entry can submit itself. */
    val pinLength: Int,
    /** Offer the phone's own fingerprint/face instead of typing the code. */
    val biometrics: Boolean,
    /** Minutes away before the code is asked for again; 0 = at once. */
    val timeoutMinutes: Int,
    /** Wrong codes in a row; drives the growing wait between tries. */
    val failedAttempts: Int,
    /** Epoch millis until which the keypad stays cold. */
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

    /** Stores a new code; the digest is computed by the caller, off the main thread. */
    suspend fun setPin(hash: String, salt: String, length: Int) {
        context.lockStore.edit { prefs ->
            prefs[Keys.pinHash] = hash
            prefs[Keys.pinSalt] = salt
            prefs[Keys.pinLength] = length
            prefs[Keys.failedAttempts] = 0
            prefs[Keys.lockedOutUntil] = 0L
        }
    }

    /**
     * Switches the lock off completely. Biometrics go with it: without a code
     * there is nothing to fall back to when a finger is not recognised.
     */
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

    /**
     * Counts a wrong code and, once there have been a few, says when the next
     * try is allowed. Kept on disk on purpose: killing the app is exactly what
     * someone guessing would try in order to get their five free attempts back.
     */
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
