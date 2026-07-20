package com.rouf.freediumcfd

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [MediumService.buildUrl]. Pure JVM (no Android framework),
 * because buildUrl uses java.net.URLEncoder rather than android.net.Uri.
 */
class MediumServiceTest {

    private val article = "https://medium.com/@user/title-abc123"

    @Test
    fun readMedium_prefixesFullUrlRaw() {
        assertEquals(
            "https://readmedium.com/en/https://medium.com/@user/title-abc123",
            MediumService.READ_MEDIUM.buildUrl(article),
        )
    }

    @Test
    fun freedium_prefixesFullUrlRaw() {
        assertEquals(
            "https://freedium-mirror.cfd/https://medium.com/@user/title-abc123",
            MediumService.FREEDIUM.buildUrl(article),
        )
    }

    @Test
    fun archiveToday_encodesUrlInQueryParam() {
        assertEquals(
            "https://archive.today/?url=https%3A%2F%2Fmedium.com%2F%40user%2Ftitle-abc123&run=1",
            MediumService.ARCHIVE_TODAY.buildUrl(article),
        )
    }

    @Test
    fun archiveIs_encodesUrlInQueryParam() {
        assertEquals(
            "https://archive.is/?url=https%3A%2F%2Fmedium.com%2F%40user%2Ftitle-abc123&run=1",
            MediumService.ARCHIVE_IS.buildUrl(article),
        )
    }

    @Test
    fun proxyApi_encodesUrlInQueryParam() {
        assertEquals(
            "https://medium-parser.vercel.app/?url=https%3A%2F%2Fmedium.com%2F%40user%2Ftitle-abc123",
            MediumService.PROXY_API.buildUrl(article),
        )
    }

    @Test
    fun queryParamService_encodesAmpersandsSoTrackingParamsDoNotSplitQuery() {
        val tracked = "https://medium.com/p/abc?source=collection&sk=xyz"
        assertEquals(
            "https://archive.today/?url=https%3A%2F%2Fmedium.com%2Fp%2Fabc%3Fsource%3Dcollection%26sk%3Dxyz&run=1",
            MediumService.ARCHIVE_TODAY.buildUrl(tracked),
        )
    }

    @Test
    fun fromId_returnsMatchingService() {
        assertEquals(MediumService.PROXY_API, MediumService.fromId("proxy"))
        assertEquals(MediumService.ARCHIVE_IS, MediumService.fromId("archive_is"))
    }

    @Test
    fun fromId_defaultsToFreediumForUnknownOrNull() {
        assertEquals(MediumService.FREEDIUM, MediumService.fromId(null))
        assertEquals(MediumService.FREEDIUM, MediumService.fromId("nonexistent"))
        assertEquals(MediumService.FREEDIUM, MediumService.DEFAULT)
    }
}
