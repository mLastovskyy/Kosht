package by.mlastovsky.kosht.data.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,

    val expiresAt: Long
) {
    fun isExpired(now: Long): Boolean = now >= expiresAt - 60_000
}

sealed interface AuthOutcome {
    data class Success(val session: SupabaseSession) : AuthOutcome

    data object CodeSent : AuthOutcome

    data class Rejected(val reason: AuthError, val detail: String) : AuthOutcome

    data object Offline : AuthOutcome
}

enum class AuthError {
    WrongCode,
    TooManyRequests,
    WrongCredentials,
    WeakPassword,
    EmailTaken,
    Unknown;

    companion object {
        fun of(message: String): AuthError {
            val text = message.lowercase()
            return when {
                "expired" in text || "invalid" in text && "token" in text -> WrongCode
                "otp" in text && ("invalid" in text || "incorrect" in text) -> WrongCode
                "security purposes" in text || "rate limit" in text ||
                    "too many" in text -> TooManyRequests

                "already registered" in text || "already been registered" in text -> EmailTaken
                "password" in text && ("short" in text || "least" in text ||
                    "weak" in text) -> WeakPassword

                "invalid login" in text || "invalid credentials" in text -> WrongCredentials
                else -> Unknown
            }
        }
    }
}

enum class CodePurpose(val type: String) {

    SignUp("email"),

    Reset("recovery")
}

private class HttpFailure(val code: Int, val body: String) : IOException("HTTP $code")

class SupabaseApi(
    private val baseUrl: String,
    private val anonKey: String
) {

    val isConfigured: Boolean = baseUrl.isNotBlank() && anonKey.isNotBlank()

    suspend fun emailRegistered(email: String): Boolean? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("check_email", email.trim())
            send("$baseUrl/rest/v1/rpc/email_registered", "POST", null, body.toString())
                .trim()
                .toBooleanStrictOrNull()
        }.getOrNull()
    }

    suspend fun sendSignUpCode(email: String): AuthOutcome = authCall(
        url = "$baseUrl/auth/v1/otp",
        body = JSONObject().put("email", email.trim()).put("create_user", true)
    ) { AuthOutcome.CodeSent }

    suspend fun sendResetCode(email: String): AuthOutcome = authCall(
        url = "$baseUrl/auth/v1/recover",
        body = JSONObject().put("email", email.trim())
    ) { AuthOutcome.CodeSent }

    suspend fun verifyCode(email: String, code: String, purpose: CodePurpose): AuthOutcome =
        authCall(
            url = "$baseUrl/auth/v1/verify",
            body = JSONObject()
                .put("email", email.trim())
                .put("token", code.trim())
                .put("type", purpose.type)
        ) { AuthOutcome.Success(it.toSession()) }

    suspend fun setPassword(accessToken: String, password: String): AuthOutcome =
        authCall(
            url = "$baseUrl/auth/v1/user",
            body = JSONObject().put("password", password),
            method = "PUT",
            token = accessToken
        ) { AuthOutcome.CodeSent }

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

    suspend fun recordConsent(
        session: SupabaseSession,
        kind: String,
        granted: Boolean,
        policyVersion: String,
        source: String
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val row = JSONObject()
                .put("user_id", session.userId)
                .put("kind", kind)
                .put("granted", granted)
                .put("policy_version", policyVersion)
                .put("source", source)
            send(
                url = "$baseUrl/rest/v1/consents",
                method = "POST",
                token = session.accessToken,
                body = JSONArray().put(row).toString(),
                extraHeaders = mapOf("Prefer" to "return=minimal")
            )
            true
        }.getOrDefault(false)
    }

    suspend fun currentConsent(session: SupabaseSession, kind: String): Boolean? =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = "select=granted&kind=eq.$kind"
                val rows = JSONArray(
                    send("$baseUrl/rest/v1/current_consents?$query", "GET", session.accessToken, null)
                )
                rows.optJSONObject(0)?.optBoolean("granted")
            }.getOrNull()
        }

    suspend fun deleteAccount(session: SupabaseSession): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            send("$baseUrl/rest/v1/rpc/delete_my_account", "POST", session.accessToken, "{}")
            true
        }.getOrDefault(false)
    }

    suspend fun pull(accessToken: String, since: Long, limit: Int): JSONArray =
        withContext(Dispatchers.IO) {
            val query = "select=*&updated_at=gt.$since&order=updated_at.asc&limit=$limit"
            JSONArray(send("$baseUrl/rest/v1/sync_rows?$query", "GET", accessToken, null))
        }

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

    suspend fun uploadPhoto(
        session: SupabaseSession,
        path: String,
        bytes: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            sendBytes(
                url = "$baseUrl/storage/v1/object/$PHOTO_BUCKET/$path",
                method = "POST",
                token = session.accessToken,
                body = bytes,
                contentType = "image/jpeg",

                extraHeaders = mapOf("x-upsert" to "true")
            )
            true
        }.getOrDefault(false)
    }

    suspend fun downloadPhoto(session: SupabaseSession, path: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                readBytes("$baseUrl/storage/v1/object/$PHOTO_BUCKET/$path", session.accessToken)
            }.getOrNull()
        }

    suspend fun deletePhoto(session: SupabaseSession, path: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                send(
                    url = "$baseUrl/storage/v1/object/$PHOTO_BUCKET/$path",
                    method = "DELETE",
                    token = session.accessToken,
                    body = null
                )
                true
            }.getOrDefault(false)
        }

    suspend fun listPhotos(session: SupabaseSession): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("prefix", "")
                .put("limit", PHOTO_LIST_LIMIT)
            val response = send(
                url = "$baseUrl/storage/v1/object/list/$PHOTO_BUCKET/${session.userId}",
                method = "POST",
                token = session.accessToken,
                body = body.toString()
            )
            val array = JSONArray(response)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun deleteAll(accessToken: String, userId: String) {
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(userId, "UTF-8")
            send("$baseUrl/rest/v1/sync_rows?user_id=eq.$encoded", "DELETE", accessToken, null)
        }
    }

    private fun credentials(email: String, password: String) = JSONObject()
        .put("email", email.trim())
        .put("password", password)

    private suspend fun authCall(
        url: String,
        body: JSONObject,
        method: String = "POST",
        token: String? = null,
        onSuccess: (JSONObject) -> AuthOutcome
    ): AuthOutcome = withContext(Dispatchers.IO) {
        try {
            val response = send(url, method, token = token, body = body.toString())

            onSuccess(if (response.isBlank()) JSONObject() else JSONObject(response))
        } catch (failure: HttpFailure) {
            val detail = failure.readableMessage()
            AuthOutcome.Rejected(AuthError.of(detail), detail)
        } catch (offline: IOException) {
            AuthOutcome.Offline
        }
    }

    private fun HttpFailure.readableMessage(): String = runCatching {
        val json = JSONObject(body)

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

    private fun sendBytes(
        url: String,
        method: String,
        token: String?,
        body: ByteArray,
        contentType: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer ${token ?: anonKey}")
            setRequestProperty("Content-Type", contentType)

            setFixedLengthStreamingMode(body.size)
            extraHeaders.forEach(::setRequestProperty)
        }
        return try {
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw HttpFailure(code, connection.errorStream?.readText().orEmpty())
            }
            connection.inputStream.readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun readBytes(url: String, token: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw HttpFailure(code, connection.errorStream?.readText().orEmpty())
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readText(): String =
        bufferedReader().use { it.readText() }

    fun isUnauthorized(error: Throwable): Boolean =
        error is HttpFailure && (error.code == 401 || error.code == 403)

    private companion object {
        const val PHOTO_BUCKET = "receipts"

        const val PHOTO_LIST_LIMIT = 5000
    }
}
