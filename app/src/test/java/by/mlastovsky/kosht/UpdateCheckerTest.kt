package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `parses version and build from a release asset name`() {
        assertEquals("2.47", UpdateChecker.parseVersionName("kosht-2.47-release.apk"))
        assertEquals(47L, UpdateChecker.parseBuildNumber("kosht-2.47-release.apk"))
    }

    @Test
    fun `ignores debug and unrelated artifacts`() {
        assertNull(UpdateChecker.parseBuildNumber("kosht-2.47-debug.apk"))
        assertNull(UpdateChecker.parseBuildNumber("mapping.txt"))
        assertNull(UpdateChecker.parseVersionName("app-release.apk"))
    }

    @Test
    fun `build number keeps growing past three digits`() {
        assertEquals(1234L, UpdateChecker.parseBuildNumber("kosht-3.1234-release.apk"))
        assertEquals("3.1234", UpdateChecker.parseVersionName("kosht-3.1234-release.apk"))
    }
}
