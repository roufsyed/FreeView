package com.rouf.freeview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the shared URL helpers (no Android framework needed). */
class UrlTextTest {

    // --- normalizeBookmarkUrl ---

    @Test
    fun normalize_dropsQueryAndFragment() {
        assertEquals(
            "https://medium.com/@user/how-to-x-abc123",
            normalizeBookmarkUrl("https://medium.com/@user/how-to-x-abc123?source=rss&sk=tok#read"),
        )
    }

    @Test
    fun normalize_foldsHttpToHttps() {
        assertEquals(
            normalizeBookmarkUrl("https://medium.com/@user/post-abc"),
            normalizeBookmarkUrl("http://medium.com/@user/post-abc"),
        )
    }

    @Test
    fun normalize_lowercasesHostButKeepsPathCase() {
        assertEquals(
            "https://medium.com/@User/My-Post-abc",
            normalizeBookmarkUrl("https://Medium.COM/@User/My-Post-abc"),
        )
    }

    @Test
    fun normalize_stripsTrailingSlashes() {
        assertEquals(
            "https://medium.com/@user/post-abc",
            normalizeBookmarkUrl("https://medium.com/@user/post-abc///"),
        )
    }

    @Test
    fun normalize_sameArticleDifferentTrackingParams_dedupesToSameKey() {
        val a = normalizeBookmarkUrl("https://medium.com/@u/great-read-9f2?source=home---feed")
        val b = normalizeBookmarkUrl("https://medium.com/@u/great-read-9f2?sk=abcdef")
        assertEquals(a, b)
    }

    @Test
    fun normalize_differentArticles_doNotCollide() {
        assertNotEquals(
            normalizeBookmarkUrl("https://medium.com/@u/first-post-111"),
            normalizeBookmarkUrl("https://medium.com/@u/second-post-222"),
        )
    }

    @Test
    fun normalize_blankIsEmpty() {
        assertEquals("", normalizeBookmarkUrl("   "))
    }

    @Test
    fun normalize_malformedNeverThrowsAndIsStable() {
        val weird = "not a url"
        // Must not throw, and must be a stable key so identical junk de-dupes against itself.
        assertEquals(normalizeBookmarkUrl(weird), normalizeBookmarkUrl(weird))
    }

    @Test
    fun normalize_isIdempotent() {
        val once = normalizeBookmarkUrl("https://Medium.com/@u/post-abc/?source=x#y")
        assertEquals(once, normalizeBookmarkUrl(once))
    }

    // --- deriveArticleTitle ---

    @Test
    fun derive_slugMinusTrailingId() {
        assertEquals(
            "How to center a div",
            deriveArticleTitle("https://medium.com/@user/how-to-center-a-div-abc123def"),
        )
    }

    @Test
    fun derive_singleWordSlugHasNoIdToDrop() {
        assertEquals("Welcome", deriveArticleTitle("https://medium.com/@user/welcome"))
    }

    @Test
    fun derive_ignoresQueryAndTrailingSlash() {
        assertEquals(
            "My post",
            deriveArticleTitle("https://medium.com/@user/my-post-xyz/?source=rss"),
        )
    }

    @Test
    fun derive_hostOnlyFallsBackToFullUrl() {
        val url = "https://medium.com"
        assertEquals(url, deriveArticleTitle(url))
    }

    @Test
    fun derive_upperCasesFirstCharLocaleIndependently() {
        // Locks the use of Char.uppercase() (not String.uppercase(Locale)) — the Turkish-i trap.
        assertTrue(deriveArticleTitle("https://medium.com/@u/install-the-app-abc").startsWith("I"))
    }
}
