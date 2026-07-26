package by.mlastovsky.kosht.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The unlock code, and the arithmetic around it.
 *
 * The code itself is never stored — only a PBKDF2 digest of it with a random
 * salt, so a copy of the preferences file is not a copy of the code. The
 * stretching is what makes a four-digit secret worth anything at all: ten
 * thousand candidates are trivial to try, but not at a tenth of a second each.
 */
object Pin {

    /** Four digits is what a phone lock asks for; more is allowed, not required. */
    const val MIN_LENGTH = 4

    const val MAX_LENGTH = 8

    /** Deliberately slow — see the class comment. Costs ~0.2 s on a mid phone. */
    private const val ITERATIONS = 100_000

    private const val KEY_BITS = 256

    private const val SALT_BYTES = 16

    /**
     * How long the app may be away before the code is asked for again, when a
     * screen of another app was opened *by* Kosht — the gallery, the camera,
     * the "install unknown apps" page. Coming back from one of those is not
     * really coming back to the app, and asking for the code every time a
     * receipt is photographed would teach the habit of turning the lock off.
     */
    private const val EXTERNAL_GRACE_MILLIS = 10 * 60 * 1000L

    /**
     * Below this, leaving does not count as leaving: recreating the activity
     * (which is how a language change is applied) stops and starts it within a
     * few hundred milliseconds, and that is not someone putting the phone down.
     */
    private const val BLINK_MILLIS = 1_500L

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    /** PBKDF2-HMAC-SHA256 of [pin] with [salt], Base64 for a text preference. */
    fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            encode(factory.generateSecret(spec).encoded)
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time, so a wrong code tells nothing by how long it took. */
    fun verify(pin: String, saltBase64: String, hashBase64: String): Boolean {
        val salt = runCatching { decode(saltBase64) }.getOrNull() ?: return false
        val expected = runCatching { decode(hashBase64) }.getOrNull() ?: return false
        val actual = runCatching { decode(hash(pin, salt)) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    /** Digits only, and as many of them as a code may have. */
    fun isValid(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    /**
     * How long the keypad stays cold after [failures] wrong codes in a row.
     * Four tries are free — a fat finger is not an attacker — and then the
     * waits grow, which is what turns ten thousand guesses into years.
     */
    fun penaltyMillis(failures: Int): Long = when {
        failures < 5 -> 0L
        failures == 5 -> 30_000L
        failures == 6 -> 60_000L
        failures == 7 -> 5 * 60_000L
        else -> 15 * 60_000L
    }

    /**
     * Whether coming back after [awayMillis] away should find the app locked,
     * given the chosen [timeoutMillis] and whether Kosht itself sent the person
     * to another screen ([expectingResult]).
     */
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
