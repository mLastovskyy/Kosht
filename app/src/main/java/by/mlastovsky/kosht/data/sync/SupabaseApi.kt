package by.mlastovsky.kosht.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A signed-in session as the app needs to remember it. */
data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    /** Epoch millis; refreshed a minute early to survive slow requests. */
    val expiresAt: Long
) {
    fun isExpired(now: Long): Boolean = now >= expiresAt - 60_000
}

/** Why an auth call did not produce a session. */
sealed interface AuthOutcome {
    data class Success(val session: SupabaseSession) : AuthOutcome

    /** Signed up, but Supabase wants the address confirmed by email first. */
    data object ConfirmEmail : AuthOutcome

    /** Wrong password, address already taken, weak password, ... */
    data class Rejected(val message: String) : AuthOutcome

    data object Offline : AuthOutcome
}

private class HttpFailure(val code: Int, val body: String) : IOException("HTTP $code")

/**
 * Thin Supabase client over [HttpURLConnection], matching how the rest of the
 * app talks to the network. Only two surfaces are needed: GoTrue for accounts
 * and PostgREST for the single `sync_rows` table.
 *
 * The anon key shipped here is public by design — it identifies the project,
 * not the user. Row level security on `sync_rows` is what keeps one account's
 * data away from another's.
 */
class SupabaseApi(
    private val baseUrl: String,
    private val anonKey: String
) {

    val isConfigured: Boolean = baseUrl.isNotBlank() && anonKey.isNotBlank()

    // ---- Accounts ---------------------------------------------------------

    suspend fun signUp(email: String, password: String): AuthOutcome =
        authCall("$baseUrl/auth/v1/signup", credentials(email, password)) { body ->
            // Without a token the address needs confirming before first use.
            if (body.optString("access_token").isBlank()) {
                AuthOutcome.ConfirmEmail
            } else {
                AuthOutcome.Success(body.toSession())
            }
        }

    suspend fun signIn(email: String, password: String): AuthOutcome =
        authCall(
            "$baseUrl/auth/v1/token?grant_type=password",
            credentials(email, password)
        ) { AuthOutcome.Success(it.toSession()) }

    suspend fun refresh(refreshToken: String): AuthOutcome =
        authCall(
            "$baseUrl/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refreshToken)
        ) { AuthOutcome.Success(it.toSession()) }

    suspend fun signOut(accessToken: String) {
        runCatching {
            withContext(Dispatchers.IO) {
                send(
                    url = "$baseUrl/auth/v1/logout",
                    method = "POST",
                    token = accessToken,
                    body = "{}"
                )
            }
        }
    }

    // ---- Sync rows --------------------------------------------------------

    /**
     * Rows changed after [since], oldest first. [limit] caps one page; the
     * engine keeps asking until a short page comes back.
     */
    suspend fun pull(accessToken: String, since: Long, limit: Int): JSONArray =
        withContext(Dispatchers.IO) {
            val query = "select=*&updated_at=gt.$since&order=updated_at.asc&limit=$limit"
            JSONArray(send("$baseUrl/rest/v1/sync_rows?$query", "GET", accessToken, null))
        }

    /** Upserts a batch, letting the newest write for a uid win server-side. */
    suspend fun push(accessToken: String, rows: JSONArray) {
        withContext(Dispatchers.IO) {
            send(
                url = "$baseUrl/rest/v1/sync_rows?on_conflict=user_id,entity,uid",
                method = "POST",
                token = accessToken,
                body = rows.toString(),
                extraHeaders = mapOf(
                    "Prefer" to "resolution=merge-duplicates,return=minimal"
                )
            )
        }
    }

    /** Wipes the account's cloud copy; used when the user disconnects. */
    suspend fun deleteAll(accessToken: String, userId: String) {
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(userId, "UTF-8")
            send("$baseUrl/rest/v1/sync_rows?user_id=eq.$encoded", "DELETE", accessToken, null)
        }
    }

    // ---- Plumbing ---------------------------------------------------------

    private fun credentials(email: String, password: String) = JSONObject()
        .put("email", email.trim())
        .put("password", password)

    private suspend fun authCall(
        url: String,
        body: JSONObject,
        onSuccess: (JSONObject) -> AuthOutcome
    ): AuthOutcome = withContext(Dispatchers.IO) {
        try {
            onSuccess(JSONObject(send(url, "POST", token = null, body = body.toString())))
        } catch (failure: HttpFailure) {
            AuthOutcome.Rejected(failure.readableMessage())
        } catch (offline: IOException) {
            AuthOutcome.Offline
        }
    }

    private fun HttpFailure.readableMessage(): String = runCatching {
        val json = JSONObject(body)
        // GoTrue is inconsistent about which field carries the reason.
        listOf("error_description", "msg", "message", "error")
            .firstNotNullOfOrNull { json.optString(it).takeIf { text -> text.isNotBlank() } }
            .orEmpty()
    }.getOrDefault("").ifBlank { "HTTP $code" }

    private fun JSONObject.toSession(): SupabaseSession {
        val user = optJSONObject("user")
        return SupabaseSession(
            accessToken = getString("access_token"),
            refreshToken = optString("refresh_token"),
            userId = user?.optString("id").orEmpty(),
            email = user?.optString("email").orEmpty(),
            expiresAt = System.currentTimeMillis() + optLong("expires_in", 3600) * 1000
        )
    }

    private fun send(
        url: String,
        method: String,
        token: String?,
        body: String?,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer ${token ?: anonKey}")
            setRequestProperty("Accept", "application/json")
            extraHeaders.forEach(::setRequestProperty)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            body?.let { connection.outputStream.use { out -> out.write(it.toByteArray()) } }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw HttpFailure(code, connection.errorStream?.readText().orEmpty())
            }
            connection.inputStream.readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readText(): String =
        bufferedReader().use { it.readText() }

    /** Signals a stale access token, which the engine answers with a refresh. */
    fun isUnauthorized(error: Throwable): Boolean =
        error is HttpFailure && (error.code == 401 || error.code == 403)
}
