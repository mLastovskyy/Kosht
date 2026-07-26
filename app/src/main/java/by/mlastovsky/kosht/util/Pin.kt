package by.mlastovsky.kosht.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Pin {

    const val MIN_LENGTH = 4

    const val MAX_LENGTH = 8

    private const val ITERATIONS = 100_000

    private const val KEY_BITS = 256

    private const val SALT_BYTES = 16

    private const val EXTERNAL_GRACE_MILLIS = 10 * 60 * 1000L

    private const val BLINK_MILLIS = 1_500L

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            encode(factory.generateSecret(spec).encoded)
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, saltBase64: String, hashBase64: String): Boolean {
        val salt = runCatching { decode(saltBase64) }.getOrNull() ?: return false
        val expected = runCatching { decode(hashBase64) }.getOrNull() ?: return false
        val actual = runCatching { decode(hash(pin, salt)) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    fun isValid(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun penaltyMillis(failures: Int): Long = when {
        failures < 5 -> 0L
        failures == 5 -> 30_000L
        failures == 6 -> 60_000L
        failures == 7 -> 5 * 60_000L
        else -> 15 * 60_000L
    }

    fun shouldLock(
        awayMillis: Long,
        timeoutMillis: Long,
        expectingResult: Boolean
    ): Boolean {
        if (awayMillis < BLINK_MILLIS) return false
        val allowed = if (expectingResult) {
            maxOf(timeoutMillis, EXTERNAL_GRACE_MILLIS)
        } else {
            timeoutMillis
        }
        return awayMillis > allowed
    }
}
