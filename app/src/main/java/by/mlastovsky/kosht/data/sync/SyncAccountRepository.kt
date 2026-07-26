package by.mlastovsky.kosht.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.accountStore: DataStore<Preferences> by preferencesDataStore(name = "account")

/** What the UI needs to know about the cloud account. */
data class AccountState(
    val email: String?,
    val autoSync: Boolean,
    /** False until the user has answered the first-launch account question. */
    val onboarded: Boolean
) {
    val signedIn: Boolean get() = !email.isNullOrBlank()
}

/**
 * Session storage and the two account preferences that go with it.
 *
 * Tokens live in the app's private DataStore. That is the same protection the
 * finance database itself gets: readable only by this app on a non-rooted
 * device, and excluded from backups alongside it.
 */
class SyncAccountRepository(
    private val context: Context,
    private val api: SupabaseApi
) {

    private object Keys {
        val accessToken = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
        val userId = stringPreferencesKey("user_id")
        val email = stringPreferencesKey("email")
        val expiresAt = longPreferencesKey("expires_at")
        val autoSync = booleanPreferencesKey("auto_sync")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val state: Flow<AccountState> = context.accountStore.data.map { prefs ->
        AccountState(
            email = prefs[Keys.email],
            autoSync = prefs[Keys.autoSync] ?: true,
            onboarded = prefs[Keys.onboarded] ?: false
        )
    }

    val isConfigured: Boolean get() = api.isConfigured

    suspend fun emailRegistered(email: String): Boolean? = api.emailRegistered(email)

    suspend fun sendSignUpCode(email: String): AuthOutcome = api.sendSignUpCode(email)

    suspend fun sendResetCode(email: String): AuthOutcome = api.sendResetCode(email)

    /**
     * The session that comes back is kept, but the account is only usable
     * once a password is set — that is the next step of both flows.
     */
    suspend fun verifyCode(email: String, code: String, purpose: CodePurpose): AuthOutcome =
        api.verifyCode(email, code, purpose).also { store(it) }

    suspend fun setPassword(password: String): AuthOutcome {
        val session = validAccessToken() ?: return AuthOutcome.Offline
        return api.setPassword(session.accessToken, password)
    }

    suspend fun signIn(email: String, password: String): AuthOutcome =
        api.signIn(email, password).also { store(it) }

    /**
     * Access tokens live an hour; this hands back a usable one, refreshing
     * through the long-lived refresh token when needed. Null means the device
     * is either signed out or offline with an expired token.
     */
    suspend fun validAccessToken(): SupabaseSession? {
        val prefs = context.accountStore.data.first()
        val refreshToken = prefs[Keys.refreshToken] ?: return null
        val session = SupabaseSession(
            accessToken = prefs[Keys.accessToken].orEmpty(),
            refreshToken = refreshToken,
            userId = prefs[Keys.userId].orEmpty(),
            email = prefs[Keys.email].orEmpty(),
            expiresAt = prefs[Keys.expiresAt] ?: 0
        )
        if (!session.isExpired(System.currentTimeMillis())) return session
        return when (val refreshed = api.refresh(refreshToken)) {
            is AuthOutcome.Success -> refreshed.session.also { store(refreshed) }
            // A rejected refresh token means the session is gone for good.
            is AuthOutcome.Rejected -> {
                clear()
                null
            }

            else -> null
        }
    }

    // ---- Consent and data-subject rights ----------------------------------

    /** Records agreement to the terms and, separately, to being emailed. */
    suspend fun recordSignUpConsents(marketing: Boolean) {
        val session = validAccessToken() ?: return
        api.recordConsent(
            session = session,
            kind = CONSENT_TERMS,
            granted = true,
            policyVersion = POLICY_VERSION,
            source = "sign_up"
        )
        api.recordConsent(
            session = session,
            kind = CONSENT_MARKETING,
            granted = marketing,
            policyVersion = POLICY_VERSION,
            source = "sign_up"
        )
    }

    suspend fun setMarketingConsent(granted: Boolean): Boolean {
        val session = validAccessToken() ?: return false
        return api.recordConsent(
            session = session,
            kind = CONSENT_MARKETING,
            granted = granted,
            policyVersion = POLICY_VERSION,
            source = "settings"
        )
    }

    suspend fun marketingConsent(): Boolean? {
        val session = validAccessToken() ?: return null
        return api.currentConsent(session, CONSENT_MARKETING)
    }

    /** Erases the cloud copy and forgets the session on this device. */
    suspend fun deleteAccount(): Boolean {
        val session = validAccessToken() ?: return false
        if (!api.deleteAccount(session)) return false
        clear()
        return true
    }

    suspend fun signOut() {
        context.accountStore.data.first()[Keys.accessToken]?.let { api.signOut(it) }
        clear()
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.accountStore.edit { it[Keys.autoSync] = enabled }
    }

    suspend fun markOnboarded() {
        context.accountStore.edit { it[Keys.onboarded] = true }
    }

    private suspend fun store(outcome: AuthOutcome) {
        val session = (outcome as? AuthOutcome.Success)?.session ?: return
        context.accountStore.edit { prefs ->
            prefs[Keys.accessToken] = session.accessToken
            prefs[Keys.refreshToken] = session.refreshToken
            prefs[Keys.userId] = session.userId
            prefs[Keys.email] = session.email
            prefs[Keys.expiresAt] = session.expiresAt
            prefs[Keys.onboarded] = true
        }
    }

    companion object {
        const val CONSENT_TERMS = "privacy_policy"
        const val CONSENT_MARKETING = "marketing_email"

        /** Bump together with the documents, so old agreements stay dated. */
        const val POLICY_VERSION = "1.0"
    }

    private suspend fun clear() {
        context.accountStore.edit { prefs ->
            prefs.remove(Keys.accessToken)
            prefs.remove(Keys.refreshToken)
            prefs.remove(Keys.userId)
            prefs.remove(Keys.email)
            prefs.remove(Keys.expiresAt)
        }
    }
}
