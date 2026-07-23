package com.rouf.freeview

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the deep-link URI gate. Uses the REAL android.net.Uri.
 *
 * FreeView opens links from any host (Medium lives on medium.com and countless custom publication
 * domains), so the gate is scheme + length only — no host restriction — while still rejecting
 * dangerous non-http(s) schemes.
 */
@RunWith(AndroidJUnit4::class)
class MediumUriValidationTest {

    private fun uri(s: String) = Uri.parse(s)

    @Test
    fun accepts_http_and_https_on_any_host() {
        assertTrue(isAcceptableViewUri(uri("https://medium.com/@user/post-abc123")))
        assertTrue(isAcceptableViewUri(uri("http://medium.com/@user/post-abc123")))
        assertTrue(isAcceptableViewUri(uri("https://username.medium.com/some-slug-abc123")))
        assertTrue(isAcceptableViewUri(uri("https://ai.plainenglish.io/uber-architecture-part-1-abc")))
        assertTrue(isAcceptableViewUri(uri("https://towardsdatascience.com/some-article-xyz")))
        assertTrue(isAcceptableViewUri(uri("https://example.com/whatever")))
    }

    @Test
    fun rejects_dangerous_and_non_http_schemes() {
        assertFalse(isAcceptableViewUri(uri("javascript:alert(1)")))
        assertFalse(isAcceptableViewUri(uri("file:///etc/hosts")))
        assertFalse(isAcceptableViewUri(uri("content://com.evil/secret")))
        assertFalse(isAcceptableViewUri(uri("intent://x/y#Intent;end")))
        assertFalse(isAcceptableViewUri(uri("data:text/html,<script>alert(1)</script>")))
    }

    @Test
    fun rejects_oversized_url() {
        assertFalse(isAcceptableViewUri(uri("https://medium.com/" + "a".repeat(MAX_URL_LENGTH))))
    }

    @Test
    fun isHttpUrl_matrix() {
        assertTrue(isHttpUrl(uri("http://x/y")))
        assertTrue(isHttpUrl(uri("https://x/y")))
        assertTrue(isHttpUrl(uri("HTTPS://x/y")))
        assertFalse(isHttpUrl(uri("javascript:x")))
        assertFalse(isHttpUrl(uri("file:///x")))
        assertFalse(isHttpUrl(uri("ftp://x/y")))
    }
}
