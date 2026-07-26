package by.mlastovsky.kosht

import by.mlastovsky.kosht.model.LockTimeout
import by.mlastovsky.kosht.util.Pin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {

    @Test
    fun `the right code verifies and a wrong one does not`() {
        val salt = Pin.newSalt()
        val hash = Pin.hash("2604", salt)
        val saltText = Pin.encode(salt)

        assertTrue(Pin.verify("2604", saltText, hash))
        assertFalse(Pin.verify("2605", saltText, hash))
        assertFalse(Pin.verify("", saltText, hash))
    }

    @Test
    fun `the same code under another salt is another digest`() {
        val first = Pin.hash("1234", Pin.newSalt())
        val second = Pin.hash("1234", Pin.newSalt())
        assertNotEquals(first, second)
    }

    @Test
    fun `the digest is not the code`() {
        val hash = Pin.hash("1234", Pin.newSalt())
        assertFalse(hash.contains("1234"))
    }

    @Test
    fun `nonsense salt or digest is a refusal, not a crash`() {
        assertFalse(Pin.verify("1234", "not base64 at all!", "neither is this!"))
    }

    @Test
    fun `a code is four to eight digits and nothing else`() {
        assertTrue(Pin.isValid("1234"))
        assertTrue(Pin.isValid("12345678"))
        assertFalse(Pin.isValid("123"))
        assertFalse(Pin.isValid("123456789"))
        assertFalse(Pin.isValid("12a4"))
        assertFalse(Pin.isValid(""))
    }

    @Test
    fun `four wrong tries are free and then the waits grow`() {
        assertEquals(0L, Pin.penaltyMillis(0))
        assertEquals(0L, Pin.penaltyMillis(4))
        assertEquals(30_000L, Pin.penaltyMillis(5))
        assertEquals(60_000L, Pin.penaltyMillis(6))
        assertEquals(300_000L, Pin.penaltyMillis(7))
        assertEquals(900_000L, Pin.penaltyMillis(8))
        assertEquals(900_000L, Pin.penaltyMillis(50))
    }

    @Test
    fun `a typed timeout is minutes, within reason`() {
        assertEquals(0L, LockTimeout.millis(LockTimeout.AT_ONCE))
        assertEquals(7 * 60_000L, LockTimeout.millis(7))

        assertEquals(LockTimeout.MAX_MINUTES, LockTimeout.sanitize(99_999))
        assertEquals(0, LockTimeout.sanitize(-5))
    }

    @Test
    fun `a cold start always asks`() {

        listOf(LockTimeout.AT_ONCE, 1, 5, LockTimeout.MAX_MINUTES).forEach { minutes ->
            val timeout = LockTimeout.millis(minutes)
            assertTrue(Pin.shouldLock(Long.MAX_VALUE, timeout, expectingResult = false))
            assertTrue(Pin.shouldLock(Long.MAX_VALUE, timeout, expectingResult = true))
        }
    }

    @Test
    fun `recreating the activity is not leaving the app`() {

        assertFalse(Pin.shouldLock(300L, LockTimeout.millis(0), expectingResult = false))
    }

    @Test
    fun `at once means at once`() {
        assertTrue(Pin.shouldLock(5_000L, LockTimeout.millis(0), expectingResult = false))
    }

    @Test
    fun `a minute away is allowed when a minute was typed`() {
        val timeout = LockTimeout.millis(1)
        assertFalse(Pin.shouldLock(59_000L, timeout, expectingResult = false))
        assertTrue(Pin.shouldLock(61_000L, timeout, expectingResult = false))
    }

    @Test
    fun `any typed number is honored, not just the round ones`() {
        val timeout = LockTimeout.millis(7)
        assertFalse(Pin.shouldLock(6 * 60_000L, timeout, expectingResult = false))
        assertTrue(Pin.shouldLock(8 * 60_000L, timeout, expectingResult = false))
    }

    @Test
    fun `photographing a receipt does not cost a code`() {

        assertFalse(Pin.shouldLock(4 * 60_000L, LockTimeout.millis(0), expectingResult = true))
    }

    @Test
    fun `but an hour in the gallery still locks`() {
        assertTrue(Pin.shouldLock(60 * 60_000L, LockTimeout.millis(0), expectingResult = true))
    }

    @Test
    fun `a longer typed timeout still wins for the app's own screens`() {
        // Twenty minutes typed, so a quarter of an hour in the camera is fine.
        assertFalse(Pin.shouldLock(15 * 60_000L, LockTimeout.millis(20), expectingResult = true))
    }
}
