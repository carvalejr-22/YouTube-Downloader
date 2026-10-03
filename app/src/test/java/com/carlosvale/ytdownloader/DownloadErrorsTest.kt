package com.carlosvale.ytdownloader

import org.junit.Assert.*
import org.junit.Test

class DownloadErrorsTest {
    @Test fun parsingDoesNotClaimPrivateContent() {
        val error = Exception("ERROR: Cannot parse data")
        assertEquals("EXTRACTION_FAILED", DownloadErrors.code(error))
        assertTrue(DownloadErrors.message(error).contains("não confirma"))
    }
    @Test fun credentialsAndServerMessagesNeverAppearInUi() {
        val secret = "secret-token-123"
        val error = Exception("https://example.org/?token=$secret cookies=$secret")
        assertFalse(DownloadErrors.message(error).contains(secret))
        assertFalse(DownloadErrors.message(error).contains("example.org"))
    }
    @Test fun specificRestrictionWinsOverGenericUnavailable() {
        assertEquals("AUTH_REQUIRED", DownloadErrors.code(Exception("Video unavailable. Sign in")))
        assertEquals("CONTENT_REMOVED", DownloadErrors.code(Exception("Video unavailable: removed")))
        assertEquals("UNAVAILABLE", DownloadErrors.code(Exception("This video is unavailable")))
    }
    @Test fun storageCauseSurvivesExportWrapper() {
        assertEquals("STORAGE_FULL", DownloadErrors.code(IllegalStateException("Não foi possível salvar", Exception("ENOSPC"))))
    }
    @Test fun rateLimitingIsExplicit() {
        assertEquals("RATE_LIMIT", DownloadErrors.code(Exception("HTTP Error 429")))
    }
}
