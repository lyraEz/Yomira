package yomira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.release.interactor.AppVersion

class AppVersionTest {

    @Test
    fun `reads stable and beta names`() {
        assertEquals(AppVersion(1, 12, 0), AppVersion.parse("v1.12.0"))
        assertEquals(AppVersion(1, 13, 0, beta = 2), AppVersion.parse("1.13.0-beta.2"))
        assertEquals(AppVersion(1, 13, 0, beta = 3), AppVersion.parse("v1.13.0-beta3"))
        assertEquals(AppVersion(2, 0, 0), AppVersion.parse("2.0"))
        assertNull(AppVersion.parse("r1234x"))
    }

    @Test
    fun `stable is newer than its betas and betas are ordered`() {
        assertTrue(AppVersion.isNewer("v1.12.1", "1.12.0"))
        assertTrue(AppVersion.isNewer("v1.13.0-beta.1", "1.12.9"))
        assertTrue(AppVersion.isNewer("v1.13.0-beta.2", "1.13.0-beta.1"))
        assertTrue(AppVersion.isNewer("v1.13.0", "1.13.0-beta.5"))
        assertFalse(AppVersion.isNewer("v1.13.0-beta.5", "1.13.0"))
        assertFalse(AppVersion.isNewer("v1.12.0", "1.12.0"))
        assertFalse(AppVersion.isNewer("v1.9.9", "1.10.0"))
        // Version names the parser cannot read never count as an update.
        assertFalse(AppVersion.isNewer("nightly", "1.12.0"))
    }
}
